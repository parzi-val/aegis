package com.example.aegis.data.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.aegis.BuildConfig
import com.example.aegis.data.ml.GemmaInferenceHolder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val inferenceHolder: GemmaInferenceHolder,
    private val okhttp: OkHttpClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dest = File(inferenceHolder.modelPath)
        val tmp = File(dest.parent, "${dest.name}.tmp")

        setForeground(buildForegroundInfo(0L, -1L))

        return try {
            val client = okhttp.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
            val response = client.newCall(
                Request.Builder().url("${BuildConfig.MODEL_SERVER_URL}/model/download").build()
            ).execute()

            if (!response.isSuccessful) {
                return Result.failure(workDataOf(KEY_ERROR to "Server returned ${response.code}"))
            }

            val body = response.body
                ?: return Result.failure(workDataOf(KEY_ERROR to "Empty response body"))
            val total = body.contentLength()

            dest.parentFile?.mkdirs()
            var received = 0L
            var lastPct = -1

            tmp.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(65_536)
                    var n: Int
                    while (input.read(buf).also { n = it } >= 0) {
                        out.write(buf, 0, n)
                        received += n
                        // Throttle updates to one per percentage point (~100 total for a 2 GB file)
                        val pct = if (total > 0) ((received * 100) / total).toInt() else -1
                        if (pct != lastPct) {
                            lastPct = pct
                            setForeground(buildForegroundInfo(received, total))
                            setProgress(workDataOf(KEY_RECEIVED to received, KEY_TOTAL to total))
                        }
                    }
                }
            }

            check(tmp.renameTo(dest)) { "Failed to finalise model file" }
            Result.success()
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            tmp.delete()
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed")))
        }
    }

    private fun buildForegroundInfo(received: Long, total: Long): ForegroundInfo {
        val progress = if (total > 0) ((received.toFloat() / total.toFloat()) * 100).toInt() else 0
        val text = if (total > 0) "${received.toMb()} / ${total.toMb()}" else "Connecting…"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading AI model")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, total <= 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun Long.toMb() = "%.1f MB".format(this / 1_048_576.0)

    companion object {
        const val WORK_NAME = "model_download"
        const val CHANNEL_ID = "model_download"
        const val NOTIFICATION_ID = 1001
        const val KEY_RECEIVED = "received"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
    }
}
