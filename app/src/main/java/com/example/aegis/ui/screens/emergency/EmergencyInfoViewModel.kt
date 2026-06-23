package com.example.aegis.ui.screens.emergency

import androidx.lifecycle.ViewModel
import com.example.aegis.data.repository.HealthProfileRepository
import com.example.aegis.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import androidx.lifecycle.viewModelScope

@HiltViewModel
class EmergencyInfoViewModel @Inject constructor(
    patientRepository: PatientRepository,
    healthProfileRepository: HealthProfileRepository,
) : ViewModel() {

    val patient = patientRepository.patient
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val allergies = healthProfileRepository.allergies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conditions = healthProfileRepository.activeConditions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val medications = healthProfileRepository.activeMedications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
