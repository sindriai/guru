// MainViewModel.kt — only change: MODEL_FILE_NAME removed, modelFile() now
// points at GemmaDownloadWorker.FILE_NAME so there is exactly one filename
// constant for the model, shared by the writer (Worker) and the reader
// (isModelAlreadyDownloaded / modelFile).
package com.sindriai.guru.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import com.sindriai.guru.app.AppConfig
import com.sindriai.guru.data.gemma.GemmaDownloadWorker
import com.sindriai.guru.data.gemma.GemmaDownloader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ModelDownloadUiState(
    val inProgress: Boolean = false,
    val percent: Int = 0,
    val fraction: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val downloadedFile: File? = null
) {
    val isSuccess: Boolean get() = downloadedFile != null
    val isError: Boolean get() = errorMessage != null
}

class MainViewModel(
    private val appContext: Context,
    private val huggingFaceToken: String,
) : ViewModel() {

    private val appConfig = AppConfig(appContext)

    private val gemmaDownloader = GemmaDownloader(appContext)

    private var observeJob: Job? = null

    // CHANGED: was `File(appContext.filesDir, MODEL_FILE_NAME)` with a
    // locally-defined constant that had drifted out of sync with the
    // filename GemmaDownloadWorker actually writes to. Now both sides read
    // from the same constant, so this check can never disagree with what
    // the Worker produced.
    private fun modelFile(): File = File(appContext.filesDir, GemmaDownloadWorker.FILE_NAME)

    fun isModelAlreadyDownloaded(): Boolean {
        val final = modelFile()
        val part = File(final.parentFile, final.name + ".part")
        val etag = File(final.parentFile, final.name + ".etag")

        if (final.exists() && final.length() > 0) {
            if (part.exists()) part.delete()
            if (etag.exists()) etag.delete()
            return true
        }
        return false
    }

    private val _uiState = MutableStateFlow(
        if (isModelAlreadyDownloaded()) {
            val file = modelFile()
            appConfig.settings.gemmaModelPath = file.absolutePath
            ModelDownloadUiState(
                inProgress = false,
                percent = 100,
                fraction = 1f,
                downloadedFile = modelFile()
            )
        } else ModelDownloadUiState()
    )
    val uiState: StateFlow<ModelDownloadUiState> = _uiState

    fun startModelDownloadIfNeeded() {

        if (_uiState.value.isSuccess) return

        if (isModelAlreadyDownloaded()) {
            _uiState.value = _uiState.value.copy(
                inProgress = false,
                percent = 100,
                fraction = 1f,
                errorMessage = null,
                downloadedFile = modelFile()
            )
            appConfig.settings.gemmaModelPath = modelFile().absolutePath
            return
        }

        gemmaDownloader.start(
            token = huggingFaceToken,
            directory = appContext.filesDir
        )

        if (observeJob?.isActive == true) return

        observeJob = viewModelScope.launch {
            gemmaDownloader.observeUniqueWork().asFlow().collect { workInfos ->
                val info = workInfos
                    .firstOrNull { it.state == WorkInfo.State.RUNNING }
                    ?: workInfos
                        .firstOrNull { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                    ?: workInfos.firstOrNull()

                if (info != null) applyWorkInfoToUi(info)
            }
        }

        _uiState.value = _uiState.value.copy(
            inProgress = true,
            errorMessage = null
        )
    }

    private fun applyWorkInfoToUi(info: WorkInfo) {
        val progress = info.progress

        val percent = progress.getInt(GemmaDownloadWorker.PROGRESS_PERCENT, 0)
        val bytes = progress.getLong(GemmaDownloadWorker.PROGRESS_BYTES, 0L)
        val total = progress.getLong(GemmaDownloadWorker.PROGRESS_TOTAL, 0L)
        val fraction = if (total > 0) (bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

        when (info.state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING -> {
                _uiState.value = _uiState.value.copy(
                    inProgress = true,
                    percent = percent,
                    fraction = fraction,
                    bytesDownloaded = bytes,
                    totalBytes = total,
                    errorMessage = null
                )
            }

            WorkInfo.State.BLOCKED -> {
                _uiState.value = _uiState.value.copy(
                    inProgress = true,
                    errorMessage = "Waiting for constraints (network/conditions)."
                )
            }

            WorkInfo.State.SUCCEEDED -> {
                val path = info.outputData.getString(GemmaDownloadWorker.OUTPUT_FILE_PATH)
                val file = path?.let { File(it) } ?: modelFile()
                appConfig.settings.gemmaModelPath = file.absolutePath

                _uiState.value = _uiState.value.copy(
                    inProgress = false,
                    percent = 100,
                    fraction = 1f,
                    bytesDownloaded = if (total > 0) total else bytes,
                    totalBytes = if (total > 0) total else _uiState.value.totalBytes,
                    errorMessage = null,
                    downloadedFile = file
                )
                observeJob?.cancel()
            }

            WorkInfo.State.FAILED -> {
                _uiState.value = _uiState.value.copy(
                    inProgress = false,
                    errorMessage = "Download failed. Please retry."
                )
                observeJob?.cancel()
            }

            WorkInfo.State.CANCELLED -> {
                _uiState.value = _uiState.value.copy(
                    inProgress = false,
                    errorMessage = "Download cancelled."
                )
                observeJob?.cancel()
            }
        }
    }

    fun retry() {
        gemmaDownloader.cancel()
        observeJob?.cancel()
        observeJob = null

        _uiState.value = ModelDownloadUiState()
        startModelDownloadIfNeeded()
    }

    fun cancelDownload() {
        gemmaDownloader.cancel()
    }
}