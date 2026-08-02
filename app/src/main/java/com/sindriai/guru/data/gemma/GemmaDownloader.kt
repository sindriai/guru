package com.sindriai.guru.data.gemma

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.*
import java.io.File

class GemmaDownloader(private val context: Context) {

    companion object {
        const val UNIQUE_WORK_NAME = "gemma_model_download"
    }

    fun start(token: String, directory: File) {
        val request = OneTimeWorkRequestBuilder<GemmaDownloadWorker>()
            .setInputData(
                workDataOf(
                    GemmaDownloadWorker.KEY_TOKEN to token,
                    GemmaDownloadWorker.KEY_DIR to directory.absolutePath
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP, // ✅ keep running one, don’t start a duplicate
            request
        )
    }

    /** ✅ Observe all work infos for this unique work (running/succeeded/failed) */
    fun observeUniqueWork(): LiveData<List<WorkInfo>> {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME)
    }

    /** Optional: allow cancelling */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
