package com.sindriai.guru.data.gemma

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class GemmaInferenceManager(
    private val context: Context,
    private val modelPath: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    companion object {
        private const val TAG = "GemmaDebug"
        private val SYSTEM_INSTRUCTION = Contents.of(
            """
            You are Guru, a personal AI tutor developed by SindriAI.
            
            CRITICAL RULE (MUST FOLLOW):
            All mathematical expressions MUST be enclosed in $$...$$.
            """.trimIndent()
        )
        private val SAMPLER_CONFIG = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.7)
    }

    enum class InferenceState { IDLE, THINKING, ANSWERING, STOPPED, ERROR }

    private val _gemmaState = MutableStateFlow(InferenceState.IDLE)
    val gemmaState: StateFlow<InferenceState> = _gemmaState.asStateFlow()

    private val mutex = Mutex()
    private var inferenceJob: Job? = null
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private var currentActivePrompt: String? = null
    private var pending: PendingRequest? = null
    private var doneSignal: CompletableDeferred<Unit>? = null

    // Performance optimization: Used StringBuilder over raw String accumulation
    private val fullResponseBuilder = StringBuilder()

    private data class PendingRequest(
        val prompt: String,
        val imageUri: Uri?,
        val onPromptSubmitted: () -> Unit,
        val onPartial: (String) -> Unit,
        val onDone: (String) -> Unit
    )

    init {
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        // Offloading heavyweight engine creation off the calling thread
        scope.launch {
            mutex.withLock {
                initializeEngineAndConversation()
            }
        }
    }

    private fun initializeEngineAndConversation() {
        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            visionBackend = Backend.CPU(),
            cacheDir = context.cacheDir.absolutePath
        )
        val newEngine = Engine(config).apply { initialize() }
        engine = newEngine
        conversation = createNewConversation(newEngine)
    }

    private fun createNewConversation(currentEngine: Engine?): Conversation? {
        return currentEngine?.createConversation(
            ConversationConfig(systemInstruction = SYSTEM_INSTRUCTION, samplerConfig = SAMPLER_CONFIG)
        )
    }

    fun submitPrompt(
        newPrompt: String,
        attachedImageUri: Uri?,
        onPromptSubmitted: () -> Unit,
        onPartial: (String) -> Unit,
        onDone: (String) -> Unit
    ) {
        Log.d("ABCDX", "Submit prompt called = "+newPrompt)
        inferenceJob = scope.launch {
            mutex.withLock {
                val currentState = _gemmaState.value
                if (currentState == InferenceState.THINKING || currentState == InferenceState.ANSWERING) {
                    val mergedPrompt = if (currentState == InferenceState.THINKING && !currentActivePrompt.isNullOrBlank()) {
                        "User revised request: $currentActivePrompt and also added: $newPrompt"
                    } else newPrompt

                    pending = PendingRequest(mergedPrompt, attachedImageUri, onPromptSubmitted, onPartial, onDone)
                    return@withLock
                }

                runOnRequest(PendingRequest(newPrompt, attachedImageUri, onPromptSubmitted, onPartial, onDone))

                while (pending != null) {
                    val next = pending!!
                    pending = null
                    runOnRequest(next)
                }
            }
        }
    }

    private suspend fun runOnRequest(req: PendingRequest) {
        fullResponseBuilder.clear()
        val signal = CompletableDeferred<Unit>().also { doneSignal = it }

        currentActivePrompt = req.prompt
        _gemmaState.value = InferenceState.THINKING

        try {
            req.onPromptSubmitted()
            val callback = createMessageCallback(req, signal)

            val currentConversation = conversation
            if (req.imageUri != null) {
                withContext(Dispatchers.IO) {
                    val imagePath = copyUriToCacheFile(context, req.imageUri)
                    currentConversation?.sendMessageAsync(
                        Contents.of(Content.ImageFile(imagePath), Content.Text(req.prompt)),
                        callback
                    )
                }
            } else {
                currentConversation?.sendMessageAsync(req.prompt, callback)
            }

            signal.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing request", e)
            _gemmaState.value = InferenceState.ERROR
            currentActivePrompt = null
            signal.complete(Unit)
        } finally {
            doneSignal = null
        }
    }

    private fun createMessageCallback(req: PendingRequest, signal: CompletableDeferred<Unit>) =
        object : MessageCallback {
            override fun onMessage(message: Message) {
                if (_gemmaState.value == InferenceState.STOPPED) return

                if (_gemmaState.value != InferenceState.ANSWERING) {
                    _gemmaState.value = InferenceState.ANSWERING
                }

                val chunk = message.toString()
                fullResponseBuilder.append(chunk)
                req.onPartial(chunk)
            }

            override fun onDone() {
                completeSession(InferenceState.IDLE, req, signal)
            }

            override fun onError(throwable: Throwable) {
                Log.e(TAG, "Gemma error during streaming", throwable)
                completeSession(InferenceState.IDLE, req, signal)
            }
        }

    private fun completeSession(
        targetState: InferenceState,
        req: PendingRequest,
        signal: CompletableDeferred<Unit>
    ) {
        _gemmaState.value = targetState
        currentActivePrompt = null
        req.onDone(fullResponseBuilder.toString())
        signal.complete(Unit)
    }

    private fun copyUriToCacheFile(context: Context, uri: Uri): String {
        val fileName = "prompt_image_${System.currentTimeMillis()}.jpg"
        val outFile = File(context.cacheDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Unable to open image URI: $uri")

        return outFile.absolutePath
    }

    fun stopConversion() {
        Log.d(TAG, "Stopping Inference mid way")
        _gemmaState.value = InferenceState.STOPPED

        conversation?.cancelProcess()
        doneSignal?.complete(Unit)
        currentActivePrompt = null

        conversation?.close()
        conversation = createNewConversation(engine)
    }

    fun close() {
        scope.cancel()
        engine?.close()
        conversation?.close()
    }
}