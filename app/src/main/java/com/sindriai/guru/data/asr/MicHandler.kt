package com.sindriai.guru.data.asr

import android.content.Context
import androidx.lifecycle.Observer
import com.sindriai.whispertinylab.data.asr.WhisperManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * MicHandler
 * Manages microphone start/stop actions and exposes Whisper speech results
 * as StateFlow for ViewModel and UI layers.
 */
class MicHandler(context: Context) {

    // WhisperManager handles audio recording and speech-to-text processing
    private val whisperManager = WhisperManager(context)

    // Indicates whether the mic is currently recording
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    // Holds the latest recognized speech text
    private val _speechResult = MutableStateFlow("")
    val speechResult: StateFlow<String> = _speechResult

    // Receives transcription results from WhisperManager
    private val resultObserver = Observer<String?> { result ->
        if (result != null) {
            _speechResult.value = result
        }
    }

    // Receives mic recording state updates from WhisperManager
    private val recordingObserver = Observer<Boolean> { isRecording ->
        _isListening.value = isRecording
    }

    // Attach observers to WhisperManager LiveData
    init {
        whisperManager.speechResult.observeForever(resultObserver)
        whisperManager.isRecording.observeForever(recordingObserver)
    }

    fun startListening() {
        whisperManager.startRecording()
    }

    // Stops recording and triggers speech recognition
    fun stopListening() {
        whisperManager.stopRecording()
    }

    // Removes observers and releases Whisper resources
    fun clear() {
        whisperManager.speechResult.removeObserver(resultObserver)
        whisperManager.isRecording.removeObserver(recordingObserver)
        whisperManager.destroy()
    }
}
