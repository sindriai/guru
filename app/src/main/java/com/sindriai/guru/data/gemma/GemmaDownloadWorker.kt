// GemmaDownloadWorker.kt — companion object, only change: FILE_NAME visibility
package com.sindriai.guru.data.gemma

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException

class GemmaDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TOKEN = "KEY_TOKEN"
        const val KEY_DIR = "KEY_DIR"

        const val PROGRESS_PERCENT = "PROGRESS_PERCENT"
        const val PROGRESS_BYTES = "PROGRESS_BYTES"
        const val PROGRESS_TOTAL = "PROGRESS_TOTAL"

        const val OUTPUT_FILE_PATH = "OUTPUT_FILE_PATH"

        private const val CHANNEL_ID = "gemma_download"
        private const val NOTIF_ID = 1001

        private const val URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"

        // CHANGED: was `private const val FILE_NAME`. This is the actual
        // filename the download writes to disk under `filesDir`, so it must
        // be visible to MainViewModel — otherwise anything outside this
        // Worker that wants to check "is the model already on disk" has no
        // way to know the real filename and drifts out of sync with it
        // (which is exactly what caused the DownloadGemma3nDialog bug).
        const val FILE_NAME = "gemma-4-E2B-it.litertlm"
    }

    private val okHttpClient = OkHttpClient.Builder().build()

    override suspend fun doWork(): Result {
        val token = inputData.getString(KEY_TOKEN) ?: return Result.failure()
        val dirPath = inputData.getString(KEY_DIR) ?: return Result.failure()

        val directory = File(dirPath)
        directory.mkdirs()

        val finalFile = File(directory, FILE_NAME)
        val partFile = File(directory, "$FILE_NAME.part")
        val etagFile = File(directory, "$FILE_NAME.etag")

        if (finalFile.exists()) {
            return Result.success(workDataOf(OUTPUT_FILE_PATH to finalFile.absolutePath))
        }

        setForeground(createForegroundInfo(percent = 0))

        return try {
            val resultFile = downloadResumable(
                token = token,
                directory = directory,
                finalFile = finalFile,
                partFile = partFile,
                etagFile = etagFile
            )

            Result.success(workDataOf(OUTPUT_FILE_PATH to resultFile.absolutePath))
        } catch (e: Exception) {
            if (e is IOException) Result.retry() else Result.failure()
        }
    }

    private fun buildOpenAppPendingIntent(): PendingIntent {
        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            ?: Intent()

        val stackBuilder = TaskStackBuilder.create(applicationContext).addNextIntentWithParentStack(launchIntent)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return stackBuilder.getPendingIntent(0, flags)!!
    }

    private fun createForegroundInfo(percent: Int): ForegroundInfo {
        createNotificationChannelIfNeeded()

        val openAppIntent = buildOpenAppPendingIntent()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading Guru model")
            .setContentText(if (percent <= 0) "Starting..." else "$percent%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .setContentIntent(openAppIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    private fun updateForeground(percent: Int) {
        setForegroundAsync(createForegroundInfo(percent))
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gemma download",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
    }

    private suspend fun downloadResumable(
        token: String,
        directory: File,
        finalFile: File,
        partFile: File,
        etagFile: File
    ): File {
        val headReq = Request.Builder()
            .url(URL)
            .header("Authorization", "Bearer $token")
            .head()
            .build()

        val headResp = okHttpClient.newCall(headReq).execute()
        val totalBytesFromHead = headResp.header("Content-Length")?.toLongOrNull() ?: -1L
        val remoteEtag = headResp.header("ETag")
        headResp.close()

        val savedEtag = readSmallText(etagFile)
        if (!savedEtag.isNullOrBlank() && !remoteEtag.isNullOrBlank() && savedEtag != remoteEtag) {
            partFile.delete()
        }

        val resumeFrom = if (partFile.exists()) partFile.length() else 0L

        if (!remoteEtag.isNullOrBlank()) writeSmallText(etagFile, remoteEtag)

        val reqBuilder = Request.Builder()
            .url(URL)
            .header("Authorization", "Bearer $token")

        if (resumeFrom > 0L) {
            reqBuilder.header("Range", "bytes=$resumeFrom-")
            if (!remoteEtag.isNullOrBlank()) reqBuilder.header("If-Range", remoteEtag)
        }

        val resp = okHttpClient.newCall(reqBuilder.build()).execute()

        when (resp.code) {
            206 -> Unit
            200 -> {
                if (resumeFrom > 0L) partFile.delete()
            }
            416 -> {
                val partLen = if (partFile.exists()) partFile.length() else 0L
                if (totalBytesFromHead > 0 && partLen == totalBytesFromHead) {
                    resp.close()
                    finalizeDownload(partFile, finalFile, etagFile)
                    return finalFile
                } else {
                    resp.close()
                    throw IOException("Resume failed (416).")
                }
            }
            else -> {
                val code = resp.code
                resp.close()
                throw IOException("HTTP $code")
            }
        }

        val body = resp.body ?: run {
            resp.close()
            throw IOException("Empty body")
        }

        val totalBytes =
            if (totalBytesFromHead > 0) totalBytesFromHead
            else {
                val bLen = body.contentLength()
                if (bLen > 0 && resumeFrom > 0 && resp.code == 206) resumeFrom + bLen else bLen
            }

        val append = partFile.exists() && partFile.length() > 0L && resp.code == 206
        val sink = partFile.sink(append = append).buffer()
        val source = body.source()

        var totalWritten = if (append) partFile.length() else 0L
        var lastPercent = -1

        sink.use { bufferedSink ->
            while (true) {
                if (isStopped) throw IOException("Stopped")
                val read = source.read(bufferedSink.buffer, 8 * 1024L)
                if (read == -1L) break

                totalWritten += read
                bufferedSink.emitCompleteSegments()

                if (totalBytes > 0) {
                    val percent = ((totalWritten * 100f) / totalBytes).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent

                        setProgress(
                            workDataOf(
                                PROGRESS_PERCENT to percent,
                                PROGRESS_BYTES to totalWritten,
                                PROGRESS_TOTAL to totalBytes
                            )
                        )

                        updateForeground(percent)
                    }
                }
            }
            bufferedSink.flush()
        }

        resp.close()

        if (totalBytes > 0 && partFile.length() != totalBytes) {
            throw IOException("Incomplete: ${partFile.length()} / $totalBytes")
        }

        finalizeDownload(partFile, finalFile, etagFile)
        return finalFile
    }

    private fun finalizeDownload(partFile: File, finalFile: File, etagFile: File) {
        if (!partFile.exists()) return
        if (finalFile.exists()) finalFile.delete()

        val renamed = partFile.renameTo(finalFile)
        if (!renamed) {
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
        }
        if (etagFile.exists()) etagFile.delete()
    }

    private fun readSmallText(file: File): String? =
        try { if (!file.exists()) null else file.readText().trim() } catch (_: Exception) { null }

    private fun writeSmallText(file: File, text: String) {
        try { file.writeText(text) } catch (_: Exception) {}
    }
}