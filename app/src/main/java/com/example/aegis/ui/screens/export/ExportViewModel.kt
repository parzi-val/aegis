package com.example.aegis.ui.screens.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.db.dao.AllergyDao
import com.example.aegis.data.db.dao.ConditionDao
import com.example.aegis.data.db.dao.DocumentDao
import com.example.aegis.data.db.dao.MedicationDao
import com.example.aegis.data.db.dao.PatientDao
import com.example.aegis.data.db.entity.DocumentEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    patientDao: PatientDao,
    conditionDao: ConditionDao,
    medicationDao: MedicationDao,
    allergyDao: AllergyDao,
    documentDao: DocumentDao,
) : ViewModel() {

    val patient = patientDao.getPatient()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val conditions = conditionDao.getActiveConditions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val medications = medicationDao.getActiveMedications()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allergies = allergyDao.getAllAllergies()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val documents: StateFlow<List<DocumentEntity>> = documentDao.getAllByDate()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedDocIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedDocIds: StateFlow<Set<Long>> = _selectedDocIds

    fun toggleDocument(id: Long) {
        _selectedDocIds.update { current -> if (id in current) current - id else current + id }
    }

    fun selectAll() {
        _selectedDocIds.value = documents.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedDocIds.value = emptySet()
    }
}
