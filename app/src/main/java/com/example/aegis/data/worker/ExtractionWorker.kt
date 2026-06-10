package com.example.aegis.data.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.aegis.data.ml.GemmaExtractionService
import com.example.aegis.data.repository.DocumentRepository
import com.example.aegis.data.repository.SuggestionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class ExtractionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val extractionService: GemmaExtractionService,
    private val documentRepository: DocumentRepository,
    private val suggestionRepository: SuggestionRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val docId = inputData.getLong(KEY_DOC_ID, -1L)
        if (docId < 0L) return Result.failure()

        val doc = documentRepository.getDocumentById(docId)
            ?: return Result.success() // document was deleted before we ran

        if (doc.extractionState != "PENDING") return Result.success() // already processed

        setForeground(buildForegroundInfo(doc.fileName))

        return try {
            val result = extractionService.extract(doc.localPath, doc.mimeType)
            documentRepository.updateDocument(
                doc.copy(
                    ocrText = result.ocrText ?: doc.ocrText,
                    documentType = result.documentType.takeIf { it != "UNKNOWN" } ?: doc.documentType,
                    providerName = result.providerName.ifBlank { doc.providerName },
                    documentDate = result.documentDate ?: doc.documentDate,
                    extractedFields = result.extractedFieldsJson ?: result.errorMessage ?: doc.extractedFields,
                    extractionState = result.state,
                )
            )
            if (result.state == "DONE" && result.extractedFieldsJson != null) {
                suggestionRepository.processExtraction(docId, result.extractedFieldsJson)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            documentRepository.updateDocument(doc.copy(extractionState = "ERROR"))
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Extraction failed")))
        }
    }

    private fun buildForegroundInfo(fileName: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Analysing document")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val WORK_QUEUE = "extraction_queue"
        const val CHANNEL_ID = "extraction"
        const val NOTIFICATION_ID = 1002
        const val KEY_DOC_ID = "doc_id"
        const val KEY_ERROR = "error"

        // All extraction work is funnelled through one unique chain.
        // APPEND_OR_REPLACE appends to an ENQUEUED/RUNNING chain so multiple
        // documents are always processed one after another, never in parallel.
        fun enqueue(workManager: WorkManager, docId: Long) {
            workManager.beginUniqueWork(
                WORK_QUEUE,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<ExtractionWorker>()
                    .setInputData(workDataOf(KEY_DOC_ID to docId))
                    .build(),
            ).enqueue()
        }
    }
}
