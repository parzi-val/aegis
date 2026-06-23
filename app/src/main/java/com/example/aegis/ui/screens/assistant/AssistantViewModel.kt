package com.example.aegis.ui.screens.assistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.db.dao.AllergyDao
import com.example.aegis.data.db.dao.ConditionDao
import com.example.aegis.data.db.dao.DocumentDao
import com.example.aegis.data.db.dao.MedicationDao
import com.example.aegis.data.db.dao.PatientDao
import com.example.aegis.data.ml.GemmaInferenceHolder
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val inferenceHolder: GemmaInferenceHolder,
    private val patientDao: PatientDao,
    private val conditionDao: ConditionDao,
    private val medicationDao: MedicationDao,
    private val allergyDao: AllergyDao,
    private val documentDao: DocumentDao,
) : ViewModel() {

    enum class ModelState { NOT_LOADED, LOADING, READY, ERROR }

    data class ChatMessage(val text: String, val isUser: Boolean)

    var modelState by mutableStateOf(
        if (inferenceHolder.isLoaded) ModelState.READY else ModelState.NOT_LOADED
    )
        private set
    var errorMessage by mutableStateOf("")
        private set
    var isGenerating by mutableStateOf(false)
        private set

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _partialResponse = MutableStateFlow("")
    val partialResponse: StateFlow<String> = _partialResponse.asStateFlow()

    fun loadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            modelState = ModelState.LOADING
            try {
                if (!inferenceHolder.isModelAvailable) {
                    errorMessage = "Model file not found."
                    modelState = ModelState.ERROR
                    return@launch
                }
                inferenceHolder.getOrLoad()
                modelState = ModelState.READY
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load model"
                modelState = ModelState.ERROR
            }
        }
    }

    fun send(userText: String) {
        if (isGenerating || modelState != ModelState.READY) return
        if (userText.isBlank()) return

        _messages.update { it + ChatMessage(userText, isUser = true) }
        isGenerating = true
        _partialResponse.value = ""

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val systemPrompt = buildSystemPrompt()
                val fullPrompt = "$systemPrompt\n\nUser question: $userText"

                val response = inferenceHolder.withSession(temperature = 0.7) { conversation ->
                    suspendCancellableCoroutine { cont ->
                        val callback = object : MessageCallback {
                            val sb = StringBuilder()
                            override fun onMessage(message: Message) {
                                sb.append(message.toString())
                                _partialResponse.update { it + message.toString() }
                            }
                            override fun onDone() { cont.resume(sb.toString()) }
                            override fun onError(throwable: Throwable) {
                                cont.resumeWithException(throwable)
                            }
                        }
                        conversation.sendMessageAsync(fullPrompt, callback)
                        cont.invokeOnCancellation { conversation.cancelProcess() }
                    }
                }
                _messages.update { it + ChatMessage(response, isUser = false) }
                _partialResponse.value = ""
                isGenerating = false
            } catch (e: Exception) {
                _messages.update { it + ChatMessage("Error: ${e.message}", isUser = false) }
                _partialResponse.value = ""
                isGenerating = false
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        _partialResponse.value = ""
    }

    private suspend fun buildSystemPrompt(): String {
        val patient = patientDao.getPatientOnce()
        val conditions = conditionDao.getAllOnce()
        val medications = medicationDao.getAllOnce()
        val allergies = allergyDao.getAllOnce()
        val documents = documentDao.getAllOnce()

        return buildString {
            appendLine("You are Aegis Assistant, a helpful health information assistant. You have access to the user's personal health profile stored in the Aegis app. Answer questions based on this data. Do not provide medical diagnoses or treatment advice — only summarize and explain what's in the records.")
            appendLine()

            if (patient != null) {
                appendLine("## Patient Profile")
                appendLine("Name: ${patient.name}")
                appendLine("Blood Type: ${patient.bloodType}")
                if (patient.gender != null) appendLine("Gender: ${patient.gender}")
                if (patient.emergencyContactName != null) appendLine("Emergency Contact: ${patient.emergencyContactName} ${patient.emergencyContactPhone ?: ""}")
                appendLine()
            }

            if (conditions.isNotEmpty()) {
                appendLine("## Conditions")
                conditions.forEach { c ->
                    append("- ${c.name}")
                    if (!c.isActive) append(" (inactive)")
                    if (c.notes.isNotBlank()) append(" — ${c.notes}")
                    appendLine()
                }
                appendLine()
            }

            if (medications.isNotEmpty()) {
                appendLine("## Medications")
                medications.forEach { m ->
                    append("- ${m.name}")
                    if (m.dosage.isNotBlank()) append(", ${m.dosage}")
                    if (m.frequency.isNotBlank()) append(", ${m.frequency}")
                    if (!m.isActive) append(" (stopped)")
                    appendLine()
                }
                appendLine()
            }

            if (allergies.isNotEmpty()) {
                appendLine("## Allergies")
                allergies.forEach { a ->
                    append("- ${a.substance} (${a.severity})")
                    if (a.reaction.isNotBlank()) append(" — reaction: ${a.reaction}")
                    appendLine()
                }
                appendLine()
            }

            val extractedDocs = documents.filter { it.extractedFields != null }
            if (extractedDocs.isNotEmpty()) {
                appendLine("## Document Extractions")
                extractedDocs.forEach { doc ->
                    appendLine("### ${doc.fileName} (${doc.documentType})")
                    if (doc.providerName.isNotBlank()) appendLine("Provider: ${doc.providerName}")
                    appendLine(doc.extractedFields)
                    appendLine()
                }
            }
        }
    }
}
