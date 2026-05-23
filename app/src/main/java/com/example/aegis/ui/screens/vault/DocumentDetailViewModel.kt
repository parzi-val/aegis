package com.example.aegis.ui.screens.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.db.entity.DocumentEntity
import com.example.aegis.data.ml.ExtractionLogger
import com.example.aegis.data.repository.DocumentRepository
import com.example.aegis.data.repository.HealthProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val healthRepository: HealthProfileRepository,
    extractionLogger: ExtractionLogger,
) : ViewModel() {

    val pipelineLogs = extractionLogger.logs

    private val documentId: Long = checkNotNull(savedStateHandle["documentId"])

    val document: StateFlow<DocumentEntity?> = documentRepository
        .getDocumentByIdAsFlow(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val conditionName: StateFlow<String?> = document
        .mapLatest { doc ->
            doc?.conditionId?.let { id -> healthRepository.getConditionById(id)?.name }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var showDeleteDialog by mutableStateOf(false)

    fun deleteDocument(doc: DocumentEntity, onDeleted: () -> Unit) {
        viewModelScope.launch {
            documentRepository.deleteDocument(doc)
            File(doc.localPath).delete()
            onDeleted()
        }
    }
}
