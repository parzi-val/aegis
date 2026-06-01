package com.example.aegis.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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

            val isPdf = mimeType == "application/pdf"
            if (!mimeType.startsWith("image/") && !isPdf) {
                log.log("SKIPPED — unsupported type: $mimeType")
                return@withContext ExtractionResult(state = "SKIPPED")
            }

            if (!inferenceHolder.isModelAvailable) {
                log.log("SKIPPED — model file not found")
                return@withContext ExtractionResult(state = "SKIPPED")
            }

            return@withContext if (isPdf) {
                extractFromPdf(localPath, log)
            } else {
                extractFromImage(localPath, log)
            }
        }

    private suspend fun extractFromPdf(localPath: String, log: ExtractionLogger): ExtractionResult {
        log.log("PDF detected — extracting text with PDFBox")
        val text = extractPdfText(localPath)
        if (text == null) {
            log.log("SKIPPED — PDF read failed at $localPath")
            return ExtractionResult(state = "SKIPPED")
        }
        log.log("PDF text extracted — ${text.length} chars")
        if (text.isBlank()) {
            log.log("SKIPPED — PDF has no extractable text (scanned image PDF?)")
            return ExtractionResult(state = "SKIPPED")
        }
        return try {
            log.log("starting text inference")
            val raw = runGemmaTextInference(buildPdfExtractionPrompt(text))
            log.log("inference complete — ${raw.length} chars")
            log.log("raw response:\n$raw")
            val result = parseExtractionResponse(raw)
            log.log("parse done — state=${result.state}")
            if (result.errorMessage != null) log.log("parse error: ${result.errorMessage}")
            result
        } catch (e: Exception) {
            log.log("EXCEPTION — ${e.javaClass.simpleName}: ${e.message}")
            ExtractionResult(state = "ERROR", errorMessage = e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun extractFromImage(localPath: String, log: ExtractionLogger): ExtractionResult {
        val imageBytes = decodeAndReencode(localPath)
        if (imageBytes == null) {
            log.log("SKIPPED — file not found or decode failed at $localPath")
            return ExtractionResult(state = "SKIPPED")
        }
        log.log("image loaded — ${imageBytes.size} bytes")
        return try {
            log.log("starting vision inference")
            val raw = runGemmaVisionInference(imageBytes, buildImageExtractionPrompt())
            log.log("inference complete — ${raw.length} chars")
            log.log("raw response:\n$raw")
            val result = parseExtractionResponse(raw)
            log.log("parse done — state=${result.state}")
            if (result.errorMessage != null) log.log("parse error: ${result.errorMessage}")
            result
        } catch (e: Exception) {
            log.log("EXCEPTION — ${e.javaClass.simpleName}: ${e.message}")
            ExtractionResult(state = "ERROR", errorMessage = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun extractPdfText(localPath: String): String? {
        val file = File(localPath)
        if (!file.exists()) return null
        PDFBoxResourceLoader.init(context)
        return try {
            val doc = PDDocument.load(file)
            val text = PDFTextStripper().getText(doc)
            doc.close()
            text
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun runGemmaVisionInference(imageBytes: ByteArray, prompt: String): String {
        val log = inferenceHolder.logger
        return inferenceHolder.withSession(temperature = 0.2) { conversation ->
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
                        override fun onDone() { cont.resume(sb.toString()) }
                        override fun onError(throwable: Throwable) {
                            log.log("onError — ${throwable.message}")
                            cont.resumeWithException(throwable)
                        }
                    },
                    emptyMap(),
                )
                cont.invokeOnCancellation { conversation.cancelProcess() }
            }
        }
    }

    private suspend fun runGemmaTextInference(prompt: String): String {
        val log = inferenceHolder.logger
        return inferenceHolder.withSession(temperature = 0.2) { conversation ->
            suspendCancellableCoroutine { cont ->
                val sb = StringBuilder()
                var tokenCount = 0
                conversation.sendMessageAsync(
                    prompt,
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            tokenCount++
                            if (tokenCount == 1) log.log("first token received")
                            if (tokenCount % 20 == 0) log.log("tokens so far: $tokenCount")
                            sb.append(message.toString())
                        }
                        override fun onDone() { cont.resume(sb.toString()) }
                        override fun onError(throwable: Throwable) {
                            log.log("onError — ${throwable.message}")
                            cont.resumeWithException(throwable)
                        }
                    },
                )
                cont.invokeOnCancellation { conversation.cancelProcess() }
            }
        }
    }

    private fun buildImageExtractionPrompt(): String = """
Extract data from this medical document image. Reply with ONLY a JSON object, no markdown.
{"documentType":"PRESCRIPTION|LAB_REPORT|DISCHARGE_SUMMARY|SCAN|INSURANCE|UNKNOWN","providerName":"","doctorName":"","documentDate":"YYYY-MM-DD or null","patientName":"","labValues":{"test":"value unit"},"medications":[{"name":"","dosage":"","frequency":""}],"conditions":["string","string"]}
IMPORTANT — conditions array: every diagnosis, impression finding, or named condition must go here as a string. This includes anything written as "Impression:", "Diagnosis:", "Findings suggest", "Consistent with", or listed under a DIAGNOSIS section.
Example: impression says "Iron Deficiency Anaemia. Elevated TSH suggests Hypothyroidism." → "conditions":["Iron Deficiency Anaemia","Hypothyroidism"]
Only extract what is explicitly visible.
""".trimIndent()

    private fun buildPdfExtractionPrompt(pdfText: String): String = """
You are a medical document parser. Extract structured data from the following medical document text and reply with ONLY a JSON object, no markdown, no explanation.
{"documentType":"PRESCRIPTION|LAB_REPORT|DISCHARGE_SUMMARY|SCAN|INSURANCE|UNKNOWN","providerName":"","doctorName":"","documentDate":"YYYY-MM-DD or null","patientName":"","labValues":{"test":"value unit"},"medications":[{"name":"","dosage":"","frequency":""}],"conditions":["string","string"]}
IMPORTANT — conditions array: every diagnosis, impression finding, or named condition must go here as a string. This includes anything written as "Impression:", "Diagnosis:", "Final Diagnosis:", "Findings suggest", "Consistent with", or listed under any DIAGNOSIS section.
Example: "Final Diagnosis: Type 2 Diabetes Mellitus, Hypertensive Heart Disease" → "conditions":["Type 2 Diabetes Mellitus","Hypertensive Heart Disease"]

DOCUMENT TEXT:
$pdfText
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
        val unwrapped = if (startsWith("```")) {
            val start = indexOf('\n').takeIf { it >= 0 } ?: return trim()
            val end = lastIndexOf("```").takeIf { it > start } ?: return substring(start + 1).trim()
            substring(start + 1, end).trim()
        } else this

        // Find the MATCHING closing brace for the first opening brace.
        // lastIndexOf('}') is wrong when the model appends text after the JSON that also
        // contains '}' — brace-depth counting handles that correctly.
        val startIdx = unwrapped.indexOf('{')
        if (startIdx < 0) return unwrapped.trim()

        var depth = 0
        var inString = false
        var escaped = false
        var endIdx = -1
        for (i in startIdx until unwrapped.length) {
            val c = unwrapped[i]
            when {
                escaped            -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"'           -> inString = !inString
                inString           -> { /* skip */ }
                c == '{'           -> depth++
                c == '}' && --depth == 0 -> { endIdx = i; break }
            }
        }

        val s = if (endIdx >= 0) unwrapped.substring(startIdx, endIdx + 1) else unwrapped.trim()
        // Strip trailing commas before } or ] (Gemma occasionally emits these)
        val noTrailingCommas = s.replace(Regex(",\\s*([}\\]])"), "$1")
        return noTrailingCommas.repairBrackets()
    }

    // Walk the string tracking [ and { separately. Any ] or } with no matching open
    // delimiter is spurious (model closed something twice) and gets stripped. Any still-open
    // delimiter at the end is inserted before the last character (handles truncated responses).
    private fun String.repairBrackets(): String {
        val stack = ArrayDeque<Pair<Int, Char>>()
        var inString = false
        var escaped = false
        val spurious = mutableSetOf<Int>()

        for (i in indices) {
            val c = this[i]
            when {
                escaped               -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"'              -> inString = !inString
                inString              -> {}
                c == '{' || c == '['  -> stack.addLast(i to c)
                c == ']'              -> if (stack.lastOrNull()?.second == '[') stack.removeLast() else spurious.add(i)
                c == '}'              -> if (stack.lastOrNull()?.second == '{') stack.removeLast() else spurious.add(i)
            }
        }

        if (spurious.isEmpty() && stack.isEmpty()) return this

        val sb = StringBuilder(filterIndexed { i, _ -> i !in spurious })
        if (stack.isNotEmpty()) {
            val closing = stack.reversed().map { if (it.second == '[') ']' else '}' }.joinToString("")
            sb.insert((sb.length - 1).coerceAtLeast(0), closing)
        }
        return sb.toString()
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
