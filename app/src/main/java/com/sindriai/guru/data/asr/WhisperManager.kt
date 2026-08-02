package com.sindriai.whispertinylab.data.asr

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sindriai.guru.core.util.WaveUtil
import com.sindriai.guru.data.asr.Recorder
import com.sindriai.guru.data.asr.Whisper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.sqrt

class WhisperManager(val context: Context) {

    companion object {
        private const val DEFAULT_MODEL_TO_USE = "whisper-tiny.tflite"
        private const val ENGLISH_ONLY_MODEL_EXTENSION = ".en.tflite"
        private const val ENGLISH_ONLY_VOCAB_FILE = "filters_vocab_en.bin"
        private const val MULTILINGUAL_VOCAB_FILE = "filters_vocab_multilingual.bin"
        private val EXTENSIONS_TO_COPY = arrayOf("tflite", "bin", "wav", "pcm")
    }

    private val _speechResult = MutableLiveData<String?>()
    val speechResult: LiveData<String?> = _speechResult

    // New: recording state for UI
    private val _isRecording = MutableLiveData<Boolean>()
    val isRecording: LiveData<Boolean> = _isRecording

    // --- VAD state ---
    private var vadIsSpeaking = false
    private var vadSpeechFrames = 0
    private var vadSilenceFrames = 0

    // Tunable parameters
    private val vadThreshold = 0.0004f        // RMS threshold for "voice"
    private val minSpeechFrames = 2         // how many "voice" chunks before we consider it real speech
    private val maxSilenceFrames = 2        // how many silent chunks after speech before auto stop

    private val MAX_RECORDING_DURATION_MS = 30_000L
    private var recordingTimeoutRunnable: Runnable? = null
    @Volatile
    private var autoModeEnabled = true      // we want automatic speech detection
    // ------------------


    private var mRecorder: Recorder? = null
    private var sdcardDataFolder: File? = null
    private val handler = Handler(Looper.getMainLooper())
    private var mWhisper: Whisper? = null
    private var selectedTfliteFile: File? = null
    private var startTime: Long = 0
    var modelStatus: String? = null
    private var modelResult: String? = null

    init {
        initViews()
        setupAssets()
        setupRecorder()
    }

    private fun initViews() {
        sdcardDataFolder = context.getExternalFilesDir(null)
        updateStatus("Ready")
    }

    private fun setupAssets() {
        sdcardDataFolder?.let { folder ->
            copyAssetsToSdcard(context, folder, EXTENSIONS_TO_COPY)
            selectedTfliteFile = File(folder, DEFAULT_MODEL_TO_USE)
        }
    }

    private fun setupRecorder() {
        mRecorder = Recorder(context).apply {
            setListener(object : Recorder.RecorderListener {
                override fun onUpdateReceived(message: String) {
                    handler.post { updateButtonState(message) }
                }

                override fun onDataReceived(samples: FloatArray) {
                    if (!autoModeEnabled) return

                    val rms = calculateRms(samples)

                    if (rms > vadThreshold) {
                        vadSpeechFrames++
                        vadSilenceFrames = 0

                        if (!vadIsSpeaking && vadSpeechFrames >= minSpeechFrames) {
                            vadIsSpeaking = true
                        }
                    } else {
                        if (vadIsSpeaking) {
                            vadSilenceFrames++
                            if (vadSilenceFrames >= maxSilenceFrames) {
                                vadIsSpeaking = false
                                vadSpeechFrames = 0
                                vadSilenceFrames = 0
                                handler.post {
                                    stopRecording()
                                }
                            }
                        }
                    }
                }

            })
        }
    }

    private fun calculateRms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) {
            sum += s * s
        }
        return sqrt(sum / samples.size).toFloat()
    }

    private fun initWhisperModel(modelFile: File) {
        val isMultilingualModel = !modelFile.name.endsWith(ENGLISH_ONLY_MODEL_EXTENSION)
        val vocabFileName =
            if (isMultilingualModel) MULTILINGUAL_VOCAB_FILE else ENGLISH_ONLY_VOCAB_FILE
        val vocabFile = File(sdcardDataFolder, vocabFileName)

        mWhisper = Whisper(context).apply {
            loadModel(modelFile, vocabFile, isMultilingualModel)
            setListener(object : Whisper.WhisperListener {
                override fun onUpdateReceived(message: String) {
                    when (message) {
                        Whisper.MSG_PROCESSING -> {
                            handler.post {
                                updateStatus("Processing audio...")
                                clearResult()
                            }
                            startTime = System.currentTimeMillis()
                        }
                    }
                }

                override fun onResultReceived(result: String) {
                    val timeTaken = System.currentTimeMillis() - startTime
                    handler.post {
                        updateStatus(" ${timeTaken}ms")
                        appendResult(result)
                    }
                }
            })
        }
    }

    private fun deinitWhisperModel() {
        mWhisper?.unloadModel()
        mWhisper = null
    }

    private fun updateStatus(status: String) {
        modelStatus = status
    }

    private fun appendResult(result: String) {
        modelResult += result
        _speechResult.value = modelResult ?: ""
    }

    private fun clearResult() {
        modelResult = ""
    }

    private fun handleTranscribeClick() {
        mRecorder?.let { recorder ->
            if (recorder.isInProgress()){
                stopRecording()
            }
        }

        if (mWhisper == null) {
            selectedTfliteFile?.let { modelFile ->
                initWhisperModel(modelFile)
            } ?: run {
                updateStatus("Model file not found")
                return
            }
        }

        mWhisper?.let { whisper ->
            if (!whisper.isInProgress()) {
                startTranscription()
            } else {
                stopTranscription()
            }
        }
    }

    private fun startTranscription() {
        sdcardDataFolder?.let { folder ->
            val waveFile = File(folder, WaveUtil.RECORDING_FILE)
            if (waveFile.exists()) {
                mWhisper?.apply {
                    setFilePath(waveFile.absolutePath)
                    setAction(Whisper.ACTION_TRANSCRIBE)
                    start()
                }
            } else {
                updateStatus("No recording found to transcribe")
            }
        }
    }

    private fun stopTranscription() {
        mWhisper?.stop()
    }

    private fun updateButtonState(message: String) {}

    private fun startRecordingTimeout() {
        recordingTimeoutRunnable?.let {
            handler.removeCallbacks(it)
        }
        recordingTimeoutRunnable = Runnable {
            stopRecording()
        }
        handler.postDelayed(recordingTimeoutRunnable!!, MAX_RECORDING_DURATION_MS)
    }


    fun startRecording() {
        mRecorder?.let { recorder ->
            if (recorder.isInProgress()) return
            // Manual start: turn ON continuous mode and start recording
            autoModeEnabled = true
            // reset VAD state when starting
            vadIsSpeaking = false
            vadSpeechFrames = 0
            vadSilenceFrames = 0

            sdcardDataFolder?.let { folder ->
                val waveFile = File(folder, WaveUtil.RECORDING_FILE)
                mRecorder?.apply {
                    setFilePath(waveFile.absolutePath)
                    start()
                }
                _isRecording.postValue(true)
                startRecordingTimeout()
            }
        }
    }

    fun stopRecording() {
        mRecorder?.let { recorder ->
            if (recorder.isInProgress()) {

                // Cancel timeout
                recordingTimeoutRunnable?.let {
                    handler.removeCallbacks(it)
                }
                recordingTimeoutRunnable = null

                // Manual stop: turn OFF continuous mode so we don't auto-restart
                autoModeEnabled = false
                mRecorder?.stop()

                _isRecording.postValue(false)
                // Transcribe the just-recorded WAV
                handleTranscribeClick()

                // If user is still in "continuous mode", arm the next utterance
                if (autoModeEnabled) {
                    handler.post {
                        startRecording()
                    }
                }
            }
        }
    }

    fun destroy() {
        _isRecording.postValue(false)
        mRecorder?.let { recorder ->
            if (recorder.isInProgress()) recorder.stop()
        }
        deinitWhisperModel()
    }

    private fun copyAssetsToSdcard(
        context: Context,
        destFolder: File,
        extensions: Array<String>
    ) {
        val assetManager: AssetManager = context.assets

        try {
            val assetFiles = assetManager.list("") ?: return

            for (assetFileName in assetFiles) {
                var matched = false
                for (extension in extensions) {
                    if (assetFileName.endsWith(".$extension")) {
                        matched = true
                        break
                    }
                }
                if (!matched) continue

                val outFile = File(destFolder, assetFileName)
                if (outFile.exists()) continue

                try {
                    assetManager.open(assetFileName).use { inputStream ->
                        FileOutputStream(outFile).use { outputStream ->
                            val buffer = ByteArray(1024)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                } catch (_: IOException) {}
            }
        } catch (_: IOException) {}
    }
}
