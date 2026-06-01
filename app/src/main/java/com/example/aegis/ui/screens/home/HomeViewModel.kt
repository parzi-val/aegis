package com.example.aegis.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.db.entity.ConditionSuggestionEntity
import com.example.aegis.data.db.entity.MedicationSuggestionEntity
import com.example.aegis.data.db.entity.PatientEntity
import com.example.aegis.data.repository.HealthProfileRepository
import com.example.aegis.data.repository.PatientRepository
import com.example.aegis.data.repository.SuggestionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    patientRepository: PatientRepository,
    healthProfileRepository: HealthProfileRepository,
    private val suggestionRepository: SuggestionRepository,
) : ViewModel() {

    sealed interface ProfileState {
        data object Loading : ProfileState
        data object Empty : ProfileState
        data class Loaded(
            val patient: PatientEntity,
            val activeConditionsCount: Int,
            val activeMedicationsCount: Int,
            val allergiesCount: Int,
        ) : ProfileState
    }

    val profileState: StateFlow<ProfileState> = combine(
        patientRepository.patient,
        healthProfileRepository.activeConditions,
        healthProfileRepository.activeMedications,
        healthProfileRepository.allergies,
    ) { patient, conditions, medications, allergies ->
        if (patient == null) ProfileState.Empty
        else ProfileState.Loaded(
            patient = patient,
            activeConditionsCount = conditions.size,
            activeMedicationsCount = medications.size,
            allergiesCount = allergies.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileState.Loading,
    )

    val conditionSuggestions: StateFlow<List<ConditionSuggestionEntity>> =
        suggestionRepository.pendingConditions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val medicationSuggestions: StateFlow<List<MedicationSuggestionEntity>> =
        suggestionRepository.pendingMedications
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun acceptCondition(suggestion: ConditionSuggestionEntity) {
        viewModelScope.launch { suggestionRepository.acceptCondition(suggestion) }
    }

    fun dismissCondition(suggestion: ConditionSuggestionEntity) {
        viewModelScope.launch { suggestionRepository.dismissCondition(suggestion.id) }
    }

    fun acceptMedication(suggestion: MedicationSuggestionEntity) {
        viewModelScope.launch { suggestionRepository.acceptMedication(suggestion) }
    }

    fun dismissMedication(suggestion: MedicationSuggestionEntity) {
        viewModelScope.launch { suggestionRepository.dismissMedication(suggestion.id) }
    }
}
