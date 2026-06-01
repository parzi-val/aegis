package com.example.aegis.ui.screens.visits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.db.entity.DocumentEntity
import com.example.aegis.data.db.entity.VisitLogEntity
import com.example.aegis.data.repository.DocumentRepository
import com.example.aegis.data.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VisitDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val visitRepository: VisitRepository,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    private val visitId: Long = checkNotNull(savedStateHandle["visitId"])

    private val _visit = MutableStateFlow<VisitLogEntity?>(null)
    val visit: StateFlow<VisitLogEntity?> = _visit

    private val _linkedDocs = MutableStateFlow<List<DocumentEntity>>(emptyList())
    val linkedDocs: StateFlow<List<DocumentEntity>> = _linkedDocs

    init {
        viewModelScope.launch {
            val v = visitRepository.getVisitById(visitId)
            _visit.value = v
            if (v != null && v.linkedDocumentIds.isNotEmpty()) {
                val ids = v.linkedDocumentIds.split(",").mapNotNull { it.trim().toLongOrNull() }
                if (ids.isNotEmpty()) _linkedDocs.value = documentRepository.getDocumentsByIds(ids)
            }
        }
    }

    fun deleteVisit(deleteLinkedFiles: Boolean, onDeleted: () -> Unit) {
        val v = _visit.value ?: return
        viewModelScope.launch {
            if (deleteLinkedFiles) {
                _linkedDocs.value.forEach { doc ->
                    documentRepository.deleteDocument(doc)
                    java.io.File(doc.localPath).delete()
                }
            }
            visitRepository.deleteVisit(v)
            onDeleted()
        }
    }
}
