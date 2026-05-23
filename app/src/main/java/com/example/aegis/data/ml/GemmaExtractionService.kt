package com.example.aegis.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ExtractionResult(
    val state: String,                   // DONE | SKIPPED | ERROR
    val ocrText: String? = null,
    val documentType: String = "UNKNOWN",
    val providerName: String = "",
    val documentDate: Long? = null,
    val extractedFieldsJson: String? = null,
    val errorMessage: String? = null,
)

@Singleton
class GemmaExtractionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inferenceHolder: GemmaInferenceHolder,
) {
    suspend fun extract(localPath: String, mimeType: String): ExtractionResult =
        withContext(Dispatchers.IO) {
            val log = inferenceHolder.logger
            log.clear()
            log.log("extract() — mimeType=$mimeType")

            if (!mimeType.startsWith("image/")) {
                log.log("SKIPPED — not an image")
                return@withContext ExtractionResult(state = "SKIPPED")
            }

            val imageBytes = decodeAndReencode(localPath)
            if (imageBytes == null) {
                log.log("SKIPPED — file not found or decode failed at $localPath")
                return@withContext ExtractionResult(state = "SKIPPED")
            }
            log.log("image loaded — ${imageBytes.size} bytes")

            if (!inferenceHolder.isModelAvailable) {
                log.log("SKIPPED — model file not found")
                return@withContext ExtractionResult(state = "SKIPPED")
            }

            return@withContext try {
                log.log("calling getOrLoad()")
                val engine = inferenceHolder.getOrLoad()
                log.log("starting inference")
                val raw = runGemmaInference(engine, imageBytes, buildExtractionPrompt())
                log.log("inference complete — ${raw.length} chars")
                val result = parseExtractionResponse(raw)
                log.log("parse done — state=${result.state}")
                result
            } catch (e: Exception) {
                log.log("EXCEPTION — ${e.javaClass.simpleName}: ${e.message}")
                ExtractionResult(state = "ERROR", errorMessage = e.message ?: e.javaClass.simpleName)
            }
        }

    private suspend fun runGemmaInference(engine: Engine, imageBytes: ByteArray, prompt: String): String {
        val log = inferenceHolder.logger
        val conversation = inferenceHolder.newConversation(engine, temperature = 0.2)
        return try {
            suspendCancellableCoroutine { cont ->
                val contents = Contents.of(listOf(Content.ImageBytes(imageBytes), Content.Text(prompt)))
                val sb = StringBuilder()
                var tokenCount = 0
                conversation.sendMessageAsync(
                    contents,
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            tokenCount++
                            if (tokenCount == 1) log.log("first token received")
                            if (tokenCount % 20 == 0) log.log("tokens so far: $tokenCount")
                            sb.append(message.toString())
                        }
                        override fun onDone() {
                            cont.resume(sb.toString())
                        }
                        override fun onError(throwable: Throwable) {
                            log.log("onError — ${throwable.message}")
                            cont.resumeWithException(throwable)
                        }
                    },
                    emptyMap(),
                )
                cont.invokeOnCancellation { conversation.cancelProcess() }
            }
        } finally {
            conversation.close()
        }
    }

    private fun buildExtractionPrompt(): String = """
Extract data from this medical document image. Reply with ONLY a JSON object, no markdown.
{"documentType":"PRESCRIPTION|LAB_REPORT|DISCHARGE_SUMMARY|SCAN|INSURANCE|UNKNOWN","providerName":"","doctorName":"","documentDate":"YYYY-MM-DD or null","patientName":"","labValues":{"test":"value unit"},"medications":[{"name":"","dosage":"","frequency":""}],"conditions":[],"summary":""}
Only extract what is explicitly visible.
""".trimIndent()

    private fun parseExtractionResponse(raw: String): ExtractionResult {
        val cleaned = raw.extractJson()
        val json = try {
            JSONObject(cleaned)
        } catch (e: JSONException) {
            return ExtractionResult(state = "ERROR", errorMessage = "JSON parse failed: ${e.message}\n\nRaw: $cleaned")
        }

        val documentType = json.optString("documentType", "UNKNOWN")
            .takeIf { it in VALID_DOC_TYPES } ?: "UNKNOWN"

        val providerName = json.optString("providerName", "")

        val documentDate = try {
            val dateStr = json.optString("documentDate", "")
            if (dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                LocalDate.parse(dateStr)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } else null
        } catch (e: Exception) {
            null
        }

        return ExtractionResult(
            state = "DONE",
            documentType = documentType,
            providerName = providerName,
            documentDate = documentDate,
            extractedFieldsJson = cleaned,
        )
    }

    private fun String.extractJson(): String {
        val s = if (startsWith("```")) {
            val start = indexOf('\n').takeIf { it >= 0 } ?: return trim()
            val end = lastIndexOf("```").takeIf { it > start } ?: return substring(start + 1).trim()
            substring(start + 1, end).trim()
        } else trim()
        // strip trailing commas before } or ] (Gemma 4 occasionally emits these)
        return s.replace(Regex(",\\s*([}\\]])"), "$1")
    }

    private fun decodeAndReencode(localPath: String): ByteArray? = try {
        val bitmap = BitmapFactory.decodeFile(localPath) ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    } catch (e: Exception) { null }

    companion object {
        private val VALID_DOC_TYPES = setOf(
            "PRESCRIPTION", "LAB_REPORT", "DISCHARGE_SUMMARY", "SCAN", "INSURANCE", "UNKNOWN",
        )
    }
}
