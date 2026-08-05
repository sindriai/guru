package com.sindriai.guru.ui.learning

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sindriai.guru.app.AppConfig
import com.sindriai.guru.data.asr.MicHandler
import com.sindriai.guru.data.chatsession.ChatHistory
import com.sindriai.guru.data.chatsession.ChatHistoryManager
import com.sindriai.guru.data.chatsession.Sender
import com.sindriai.guru.data.gemma.GemmaInferenceManager
import com.sindriai.guru.data.tts.TextReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class LearningViewModel(
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext
    private val appConfig = AppConfig(appContext)

    private var isInitializingHeavyComponents = false

    private val _areAllEnginesReady = MutableStateFlow(false)

    private val _gemmaState =
        MutableStateFlow(GemmaInferenceManager.InferenceState.IDLE)
    val gemmaState: StateFlow<GemmaInferenceManager.InferenceState> = _gemmaState.asStateFlow()

    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking: StateFlow<Boolean> = _isTtsSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _speechResult = MutableStateFlow("")
    val speechResult: StateFlow<String> = _speechResult.asStateFlow()

    private val _streamingGuruText = MutableStateFlow<String?>(null)
    val streamingGuruText: StateFlow<String?> = _streamingGuruText.asStateFlow()

    private val _attachedImageUri = MutableStateFlow<Uri?>(null)
    val attachedImageUri: StateFlow<Uri?> = _attachedImageUri.asStateFlow()

    private val chatHistoryManager = ChatHistoryManager(appContext)
    val chatHistory: StateFlow<ChatHistory> = chatHistoryManager.chatHistory

    private var textReader: TextReader? = null
    private var micHandler: MicHandler? = null
    private var gemmaManager: GemmaInferenceManager? = null

    private var selectedTopicId: String? = null
    private val foundationCache = mutableMapOf<String, String>()

    // ─────────────────────────────────────────────────────────────────
    // Hidden conversation warm-up state machine
    // ─────────────────────────────────────────────────────────────────
    //
    // Replaces the old scattered `isThisTopicSelectedNew` boolean. Each
    // topic goes through exactly one of these states:
    //   NotStarted           -- "Start Learning" hasn't been pressed yet
    //   InProgress(topicId)  -- warm-up inference is running right now
    //   Completed(topicId)   -- warm-up finished (successfully or not)
    //
    // `deferred` lets any caller (e.g. the first chat question) suspend
    // until warm-up finishes, without polling and without a race: if the
    // student opens chat and asks something before warm-up completes,
    // handleNewPrompt() below simply awaits the same Deferred.
    private sealed class WarmupState {
        object NotStarted : WarmupState()
        data class InProgress(val topicId: String, val deferred: CompletableDeferred<Boolean>) : WarmupState()
        data class Completed(val topicId: String, val success: Boolean) : WarmupState()
    }

    private var warmupState: WarmupState = WarmupState.NotStarted
    private var warmupJob: Job? = null

    companion object {
        private const val TAG = "LearningViewModel"
        private const val MAX_HISTORY_MESSAGES_IN_PROMPT = 12
        private const val WARMUP_ENGINE_WAIT_TIMEOUT_MS = 20_000L
    }

    fun initializeHeavyComponents() {
        if (_areAllEnginesReady.value || isInitializingHeavyComponents) return

        isInitializingHeavyComponents = true

        viewModelScope.launch {
            try {
                // Let activity/screen settle first.
                delay(300)

                val localMicHandler = withContext(Dispatchers.Main.immediate) {
                    MicHandler(appContext)
                }

                //textReader = localTextReader
                micHandler = localMicHandler

                launch {
                    localMicHandler.isListening.collect { listening ->
                        _isListening.value = listening
                    }
                }

                launch {
                    localMicHandler.speechResult.collect { spokenText ->
                        _speechResult.value = spokenText
                        if (spokenText.isNotBlank()) {
                            handleNewPrompt(spokenText)
                        }
                    }
                }

                // Background heavy model creation
                val localGemmaManager = withContext(Dispatchers.Default) {
                    val modelPath = appConfig.settings.gemmaModelPath
                    if (modelPath.isNullOrBlank()) {
                        null
                    } else {
                        GemmaInferenceManager(
                            context = appContext,
                            modelPath = modelPath,
                            scope = viewModelScope
                        )
                    }
                }

                gemmaManager = localGemmaManager

                launch {
                    localGemmaManager?.gemmaState?.collect { state ->
                        _gemmaState.value = state
                    }
                }

                _areAllEnginesReady.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                _gemmaState.value = GemmaInferenceManager.InferenceState.ERROR
            } finally {
                isInitializingHeavyComponents = false
            }
        }
    }

    fun initializeGemmaEngine() {
        if (gemmaManager != null) return

        viewModelScope.launch {
            try {
                val localGemmaManager = withContext(Dispatchers.Default) {
                    val modelPath = appConfig.settings.gemmaModelPath
                    if (modelPath.isNullOrBlank()) null
                    else GemmaInferenceManager(
                        context = appContext,
                        modelPath = modelPath,
                        scope = viewModelScope
                    )
                }

                gemmaManager = localGemmaManager

                localGemmaManager?.let { manager ->
                    launch {
                        manager.gemmaState.collect { state ->
                            _gemmaState.value = state
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _gemmaState.value = GemmaInferenceManager.InferenceState.ERROR
            }
        }
    }

    fun setSelectedTopicId(id: String) {
        selectedTopicId = id
        chatHistoryManager.loadTopic(id)

        // Only pre-fetch/cache the foundation text here so it's ready the
        // instant warm-up is triggered. Nothing is sent to Gemma yet --
        // that only happens from startTopicWarmup(), which is wired to
        // the "Start Learning" button, per requirement #4.
        viewModelScope.launch {
            runCatching { getFoundationContent(id) }
        }
    }

    // Call this from the "Start Learning" button click (TopicScreen),
    // NOT from setSelectedTopicId. Runs at most once per topic; a second
    // call for the same topic (e.g. recomposition re-firing the click
    // handler) is a no-op.
    fun startTopicWarmup(topicId: String) {
        val existing = warmupState
        val alreadyHandled =
            (existing is WarmupState.InProgress && existing.topicId == topicId) ||
                    (existing is WarmupState.Completed && existing.topicId == topicId)
        if (alreadyHandled) return

        warmupJob?.cancel()

        val deferred = CompletableDeferred<Boolean>()
        warmupState = WarmupState.InProgress(topicId, deferred)

        warmupJob = viewModelScope.launch {
            val success = runCatching {
                waitUntilEngineReady()

                val manager = gemmaManager
                    ?: error("Gemma engine not available for warm-up")

                val foundation = getFoundationContent(topicId)

                if (foundation.isNullOrBlank()) {
                    // No foundation content to prime with -- nothing to
                    // warm up, so this trivially "succeeds" (the normal
                    // chat prompt won't attach anything either).
                    true
                } else {
                    // Fresh conversation per topic so this topic's warm-up
                    // context doesn't blend with a previous topic's.
                    manager.resetConversation()
                    val primingPrompt = buildWarmupPrompt(foundation)
                    manager.primeConversation(primingPrompt).isSuccess
                }
            }.getOrElse { e ->
                Log.e(TAG, "Warm-up failed for topic $topicId", e)
                false
            }

            warmupState = WarmupState.Completed(topicId, success)
            if (!deferred.isCompleted) deferred.complete(success)
        }
    }

    private suspend fun waitUntilEngineReady(timeoutMs: Long = WARMUP_ENGINE_WAIT_TIMEOUT_MS) {
        withTimeoutOrNull(timeoutMs) {
            _areAllEnginesReady.first { it }
        }
    }

    // Suspends until the given topic's warm-up is done (if one is
    // running or already ran), returning whether it succeeded. Returns
    // false immediately for any topic that never started a warm-up (e.g.
    // foundation-less topics, or a race where the question arrives before
    // startTopicWarmup was ever called) -- callers treat `false` as "fall
    // back to attaching foundation directly to this turn".
    private suspend fun awaitWarmupForTopic(topicId: String?): Boolean {
        if (topicId.isNullOrBlank()) return false
        return when (val state = warmupState) {
            is WarmupState.InProgress -> if (state.topicId == topicId) state.deferred.await() else false
            is WarmupState.Completed -> if (state.topicId == topicId) state.success else false
            WarmupState.NotStarted -> false
        }
    }

    fun setAttachedImage(uri: Uri) {
        _attachedImageUri.value = uri
    }

    fun clearAttachedImage() {
        _attachedImageUri.value = null
    }

    fun startRecordingForWhisper() {
        if (!_areAllEnginesReady.value) return
        micHandler?.startListening()
    }

    fun stopRecordingForWhisper() {
        if (!_areAllEnginesReady.value) return
        micHandler?.stopListening()
    }

    fun startGemmaInference(prompt: String) {
        if (prompt.isBlank()) return

        if (!_areAllEnginesReady.value) {
            showToast("Guru is getting ready, please wait...")
            return
        }

        handleNewPrompt(prompt)
    }

    fun stopGemmaInference(){
        gemmaManager?.stopConversion()
    }

    private fun handleNewPrompt(prompt: String) {

        val currentGemmaManager = gemmaManager ?: return
        val cleanPrompt = prompt.trim()

        _streamingGuruText.value = null

        if (cleanPrompt.isBlank()) return

        chatHistoryManager.addUserMessage(cleanPrompt)

        viewModelScope.launch {
            val topicId = selectedTopicId

            // If warm-up for this topic is still running, this suspends
            // here until it finishes (requirement #7) instead of racing
            // ahead with a prompt that assumes context which isn't there
            // yet. If warm-up already succeeded, the foundation text is
            // already inside the model's context -- we do NOT re-attach it.
            val warmupSucceeded = awaitWarmupForTopic(topicId)

            val foundationFallback = if (!warmupSucceeded && !topicId.isNullOrBlank()) {
                // Warm-up never ran or failed -- fall back to the old
                // behavior so the student still gets a correct (if
                // slower) first answer instead of a broken one.
                getFoundationContent(topicId)
            } else {
                null
            }

            val finalPrompt = buildChatPrompt(
                userPrompt = cleanPrompt,
                chatHistory = chatHistory.value,
                foundationFallback = foundationFallback
            )

            currentGemmaManager.submitPrompt(
                newPrompt = finalPrompt,
                attachedImageUri = attachedImageUri.value,
                onPromptSubmitted = { clearAttachedImage() },
                onPartial = { partial ->
                    _streamingGuruText.value = (_streamingGuruText.value ?: "") + partial
                    Log.d("ABCDX",""+partial)
                },
                onDone = {
                    Log.d("ABCDX","----Done----")
                    chatHistoryManager.addGuruMessage(_streamingGuruText.value?:"")
                    _streamingGuruText.value = ""
                }
            )
        }
    }

    private suspend fun getFoundationContent(topicId: String): String? {
        foundationCache[topicId]?.let { return it }

        val content = withContext(Dispatchers.IO) {
            loadFoundationContentFromAssets("course_materials/${topicId.take(8)}/$topicId/$topicId.json")
        }

        if (!content.isNullOrBlank()) {
            foundationCache[topicId] = content
        }

        return content
    }

    private fun loadFoundationContentFromAssets(assetPath: String): String? {
        return try {
            val jsonText = appContext.assets
                .open(assetPath)
                .bufferedReader()
                .use { it.readText() }

            val obj = JSONObject(jsonText)
            obj.optString("content").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // The hidden priming prompt sent once per topic via
    // GemmaInferenceManager.primeConversation(). Its response is always
    // discarded by the manager -- this text only shapes what the model
    // internalizes, never what's shown.
    private fun buildWarmupPrompt(foundation: String): String {
        val foundationForPrompt = foundation
            .lineSequence()
            .take(400)
            .joinToString("\n")

        return """
            Carefully study and remember the following learning material.
            This information will become the background context for all
            future questions in this session.

            Do not explain it.
            Do not summarize it.
            Do not answer any question yet.
            Simply acknowledge internally after you have fully understood it.

            Learning Material:
            $foundationForPrompt
        """.trimIndent()
    }

    // Normal chat-turn prompt. Small by design: recent history + the
    // question. `foundationFallback` is only non-null when warm-up never
    // ran or failed for this topic (see handleNewPrompt).
    private fun buildChatPrompt(
        userPrompt: String,
        chatHistory: ChatHistory,
        foundationFallback: String?
    ): String {
        val formattedHistory = chatHistory.chatHistory
            .takeLast(MAX_HISTORY_MESSAGES_IN_PROMPT)
            .joinToString(separator = "\n") { message ->
                when (message.sender) {
                    Sender.USER -> "User: ${message.content}"
                    Sender.GURU -> "Guru: ${message.content}"
                }
            }

        val context = buildString {
            if (!foundationFallback.isNullOrBlank()) {
                append("Foundation Knowledge (use it to teach): ")
                append(foundationFallback.lineSequence().take(120).joinToString("\n"))
                append("\n")
            }
            if (formattedHistory.isNotBlank()) {
                append("Conversation so far: ")
                append(formattedHistory)
            }
        }

        return """
            $context
            
            Answer in less than 120 words.
            Try to answer in simple and easy way.
            Try to answer in steps if required.
            User question:
            $userPrompt
        """.trimIndent()
    }

    private fun showToast(message: String) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        warmupJob?.cancel()
        micHandler?.clear()
        gemmaManager?.close()
    }
}