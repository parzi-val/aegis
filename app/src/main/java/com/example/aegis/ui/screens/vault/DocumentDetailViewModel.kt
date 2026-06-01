package com.example.aegis.ui.screens.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.db.dao.ShareAuditDao
import com.example.aegis.data.db.entity.DocumentEntity
import com.example.aegis.data.db.entity.ShareAuditEntity
import com.example.aegis.data.ml.ExtractionLogger
import com.example.aegis.data.repository.DocumentRepository
import com.example.aegis.data.repository.HealthProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
    private val shareAuditDao: ShareAuditDao,
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

    val shareAudits: StateFlow<List<ShareAuditEntity>> = shareAuditDao
        .getAuditsForDocument(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var showDeleteDialog by mutableStateOf(false)

    fun saveAuditNote(entryId: Long, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = shareAudits.value.find { it.id == entryId } ?: return@launch
            shareAuditDao.update(current.copy(userNote = note))
        }
    }

    fun deleteDocument(doc: DocumentEntity, onDeleted: () -> Unit) {
        viewModelScope.launch {
            documentRepository.deleteDocument(doc)
            File(doc.localPath).delete()
            onDeleted()
        }
    }
}
