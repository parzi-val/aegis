package com.example.aegis.ui.screens.visits

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.db.entity.DocumentEntity
import com.example.aegis.data.db.entity.VisitLogEntity
import com.example.aegis.data.ml.GemmaExtractionService
import com.example.aegis.data.repository.DocumentRepository
import com.example.aegis.data.repository.HealthProfileRepository
import com.example.aegis.data.repository.SuggestionRepository
import com.example.aegis.data.repository.VisitRepository
import kotlinx.coroutines.flow.map
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AddVisitViewModel @Inject constructor(
    private val visitRepository: VisitRepository,
    private val documentRepository: DocumentRepository,
    private val extractionService: GemmaExtractionService,
    private val suggestionRepository: SuggestionRepository,
    healthProfileRepository: HealthProfileRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val providerSuggestions = documentRepository.providerSuggestions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profileConditionNames = healthProfileRepository.activeConditions
        .map { list -> list.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var providerName by mutableStateOf("")
    var visitDate by mutableStateOf(System.currentTimeMillis())
    var notes by mutableStateOf("")
    var conditionTagInput by mutableStateOf("")

    private val _conditionTags = mutableStateListOf<String>()
    val conditionTags: List<String> = _conditionTags

    private val _pendingFiles = mutableStateListOf<Pair<Uri, String>>()
    val pendingFiles: List<Pair<Uri, String>> = _pendingFiles

    var isSaving by mutableStateOf(false)
        private set

    fun addConditionTag() {
        val tag = conditionTagInput.trim()
        if (tag.isNotEmpty() && !_conditionTags.contains(tag)) _conditionTags.add(tag)
        conditionTagInput = ""
    }

    fun removeConditionTag(tag: String) { _conditionTags.remove(tag) }

    fun onFilesPicked(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                val name = getDisplayName(uri)
                withContext(Dispatchers.Main) { _pendingFiles.add(uri to name) }
            }
        }
    }

    fun removeFile(uri: Uri) { _pendingFiles.removeIf { it.first == uri } }

    fun saveVisit(onDone: () -> Unit) {
        viewModelScope.launch {
            isSaving = true
            try {
                val docIds = _pendingFiles.map { (uri, name) ->
                    withContext(Dispatchers.IO) {
                        val localPath = copyToPrivateStorage(uri, name)
                        val mime = context.contentResolver.getType(uri) ?: "image/*"
                        val docId = documentRepository.addDocument(
                            DocumentEntity(
                                fileName = name,
                                documentType = "UNKNOWN",
                                mimeType = mime,
                                localPath = localPath,
                                providerName = providerName.trim(),
                                extractionState = "PENDING",
                            )
                        )
                        launchExtraction(docId, localPath, mime)
                        docId
                    }
                }
                withContext(Dispatchers.IO) {
                    visitRepository.addVisit(
                        VisitLogEntity(
                            visitDate = visitDate,
                            clinicName = providerName.trim(),
                            notes = notes.trim(),
                            conditionTags = _conditionTags.joinToString(","),
                            linkedDocumentIds = docIds.joinToString(","),
                        )
                    )
                }
                onDone()
            } finally {
                isSaving = false
            }
        }
    }

    private fun launchExtraction(docId: Long, localPath: String, mimeType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = extractionService.extract(localPath, mimeType)
            val existing = documentRepository.getDocumentById(docId) ?: return@launch
            documentRepository.updateDocument(
                existing.copy(
                    documentType = result.documentType.takeIf { it != "UNKNOWN" } ?: existing.documentType,
                    providerName = result.providerName.ifBlank { existing.providerName },
                    documentDate = result.documentDate ?: existing.documentDate,
                    extractedFields = result.extractedFieldsJson ?: result.errorMessage ?: existing.extractedFields,
                    extractionState = result.state,
                )
            )
            if (result.state == "DONE" && result.extractedFieldsJson != null) {
                suggestionRepository.processExtraction(docId, result.extractedFieldsJson)
            }
        }
    }

    private fun getDisplayName(uri: Uri): String {
        var name = "document_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col >= 0) cursor.getString(col)?.let { name = it }
            }
        }
        return name
    }

    private fun copyToPrivateStorage(uri: Uri, fileName: String): String {
        val docsDir = File(context.filesDir, "documents").also { it.mkdirs() }
        val dest = File(docsDir, "${System.currentTimeMillis()}_$fileName")
        context.contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
        return dest.absolutePath
    }
}
