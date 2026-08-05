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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    var isThisTopicSelectedNew = true

    private var textReader: TextReader? = null
    private var micHandler: MicHandler? = null
    private var gemmaManager: GemmaInferenceManager? = null

    private var selectedTopicId: String? = null
    private val foundationCache = mutableMapOf<String, String>()

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

        isThisTopicSelectedNew = true

        selectedTopicId = id
        chatHistoryManager.loadTopic(id)

        viewModelScope.launch {
            runCatching { getFoundationContent(id) }
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
            val foundation = if (topicId.isNullOrBlank()) {
                null
            } else {
                getFoundationContent(topicId)
            }

            val finalPrompt = buildPromptWithFoundation(
                userPrompt = cleanPrompt,
                chatHistory = chatHistory.value,
                foundation = foundation
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
            val courseId = topicId.take(8)
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

    private fun buildPromptWithFoundation(
        userPrompt: String,
        chatHistory: ChatHistory,
        foundation: String?
    ): String {
        val formattedHistory = chatHistory.chatHistory.joinToString(separator = "\n") { message ->
            when (message.sender) {
                Sender.USER -> "User: ${message.content}"
                Sender.GURU -> "Guru: ${message.content}"
            }
        }

        val foundationForPrompt = foundation
            ?.lineSequence()
            ?.take(120)
            ?.joinToString("\n")
            ?: "N/A"

        var instruction = ""

        if(isThisTopicSelectedNew){
            instruction = "Foundation Knowledge (use it to teach): " +
                    "$foundationForPrompt, " +
                    "Conversation so far: $formattedHistory"
            isThisTopicSelectedNew = false
        }else{
            instruction = ""
        }

        return """
            $instruction
            
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
        micHandler?.clear()
        gemmaManager?.close()
    }
}