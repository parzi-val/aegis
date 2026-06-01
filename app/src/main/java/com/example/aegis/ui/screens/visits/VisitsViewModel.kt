package com.example.aegis.ui.screens.visits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.db.entity.VisitLogEntity
import com.example.aegis.data.repository.DocumentRepository
import com.example.aegis.data.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class VisitsViewModel @Inject constructor(
    visitRepository: VisitRepository,
    documentRepository: DocumentRepository,
) : ViewModel() {
    val visits: StateFlow<List<VisitLogEntity>> = visitRepository.allVisits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingDocIds: StateFlow<Set<Long>> = documentRepository.allDocuments
        .map { docs -> docs.filter { it.extractionState == "PENDING" }.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
}
