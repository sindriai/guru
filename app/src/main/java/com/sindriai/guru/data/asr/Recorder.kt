package com.sindriai.guru.data.asr;

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import com.sindriai.guru.core.util.WaveUtil
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission

class Recorder(private val context: Context) {

    interface RecorderListener {
        fun onUpdateReceived(message: String)
        fun onDataReceived(samples: FloatArray)
    }

    companion object {
        const val MSG_RECORDING = "Recording..."
        const val MSG_RECORDING_DONE = "Recording done...!"
    }

    private val inProgress = AtomicBoolean(false)
    private var wavFilePath: String? = null
    private var listener: RecorderListener? = null
    private val lock = ReentrantLock()
    private val hasTask = lock.newCondition()
    private val fileSavedLock = Object()
    @Volatile private var shouldStartRecording = false

    @SuppressLint("MissingPermission")
    private val workerThread = Thread { recordLoop() }.apply { start() }

    fun setListener(listener: RecorderListener) {
        this.listener = listener
    }

    fun setFilePath(wavFile: String) {
        wavFilePath = wavFile
    }

    fun start() {
        if (!inProgress.compareAndSet(false, true)) {
            return
        }
        lock.lock()
        try {
            shouldStartRecording = true
            hasTask.signal()
        } finally {
            lock.unlock()
        }
    }

    fun stop() {
        inProgress.set(false)
        synchronized(fileSavedLock) {
            try {
                fileSavedLock.wait()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    fun isInProgress(): Boolean = inProgress.get()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun recordLoop() {
        while (true) {
            lock.lock()
            try {
                while (!shouldStartRecording) hasTask.await()
                shouldStartRecording = false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } finally {
                lock.unlock()
            }

            try {
                recordAudio()
            } catch (e: Exception) {
                listener?.onUpdateReceived(e.message ?: "Recording error")
            } finally {
                inProgress.set(false)
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun recordAudio() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener?.onUpdateReceived("Permission not granted for recording")
            return
        }

        listener?.onUpdateReceived(MSG_RECORDING)

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // Use VOICE_COMMUNICATION to help HW AEC/NS when available
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        // --- AEC + Noise Suppression ---
        val sessionId = audioRecord.audioSessionId
        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null

        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(sessionId)
            aec?.enabled = true
        }

        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(sessionId)
            ns?.enabled = true
        }
        // -------------------------------

        audioRecord.startRecording()

        // You can keep these as-is or tweak chunkBytes for finer VAD resolution
        val maxBytes = sampleRate * 2 * 30
        val chunkBytes = sampleRate * 2 * 1 // 1 second chunks for VAD (was 3s)

        val outputBuffer = ByteArrayOutputStream()
        val realtimeBuffer = ByteArrayOutputStream()
        val audioData = ByteArray(bufferSize)
        var totalBytes = 0

        while (inProgress.get() && totalBytes < maxBytes) {
            val bytesRead = audioRecord.read(audioData, 0, bufferSize)
            if (bytesRead > 0) {
                outputBuffer.write(audioData, 0, bytesRead)
                realtimeBuffer.write(audioData, 0, bytesRead)
                totalBytes += bytesRead

                if (realtimeBuffer.size() >= chunkBytes) {
                    val samples = ByteBuffer.wrap(realtimeBuffer.toByteArray())
                        .order(ByteOrder.nativeOrder())
                        .asShortBuffer()
                    val floatArray = FloatArray(samples.remaining()) {
                        samples.get().toFloat() / 32768f
                    }
                    listener?.onDataReceived(floatArray)
                    realtimeBuffer.reset()
                }
            } else {
                break
            }
        }

        audioRecord.stop()
        audioRecord.release()

        // Release effects
        aec?.release()
        ns?.release()

        WaveUtil.createWaveFile(wavFilePath, outputBuffer.toByteArray(), sampleRate, 1, 2)
        listener?.onUpdateReceived(MSG_RECORDING_DONE)

        synchronized(fileSavedLock) {
            fileSavedLock.notify()
        }
    }

}
