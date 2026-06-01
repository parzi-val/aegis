package com.example.aegis.ui.screens.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.db.entity.AllergyEntity
import com.example.aegis.data.db.entity.ConditionEntity
import com.example.aegis.data.db.entity.MedicationEntity
import com.example.aegis.data.db.entity.PatientEntity
import com.example.aegis.data.repository.HealthProfileRepository
import com.example.aegis.data.repository.PatientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val healthProfileRepository: HealthProfileRepository,
) : ViewModel() {

    data class ProfileData(
        val patient: PatientEntity,
        val conditions: List<ConditionEntity>,
        val medications: List<MedicationEntity>,
        val allergies: List<AllergyEntity>,
    )

    sealed interface State {
        data object Loading : State
        data class Loaded(val data: ProfileData) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    // edit fields for personal info
    var name by mutableStateOf("")
    var bloodType by mutableStateOf("Unknown")
    var gender by mutableStateOf("")
    var emergencyContactName by mutableStateOf("")
    var emergencyContactPhone by mutableStateOf("")

    // add-condition input
    var newCondition by mutableStateOf("")

    // add-medication inputs
    var newMedName by mutableStateOf("")
    var newMedDosage by mutableStateOf("")
    var newMedFrequency by mutableStateOf("")

    // add-allergy inputs
    var newAllergySubstance by mutableStateOf("")
    var newAllergySeverity by mutableStateOf("Unknown")

    var isSaving by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            combine(
                patientRepository.patient,
                healthProfileRepository.conditions,
                healthProfileRepository.medications,
                healthProfileRepository.allergies,
            ) { patient, conditions, medications, allergies ->
                if (patient == null) null
                else ProfileData(patient, conditions, medications, allergies)
            }.collect { data ->
                if (data != null) {
                    if (_state.value is State.Loading) {
                        name = data.patient.name
                        bloodType = data.patient.bloodType
                        gender = data.patient.gender ?: ""
                        emergencyContactName = data.patient.emergencyContactName ?: ""
                        emergencyContactPhone = data.patient.emergencyContactPhone ?: ""
                    }
                    _state.value = State.Loaded(data)
                }
            }
        }
    }

    fun savePersonalInfo() {
        val current = (_state.value as? State.Loaded)?.data?.patient ?: return
        viewModelScope.launch {
            isSaving = true
            patientRepository.savePatient(
                current.copy(
                    name = name.trim(),
                    bloodType = bloodType,
                    gender = gender.ifEmpty { null },
                    emergencyContactName = emergencyContactName.trim().ifEmpty { null },
                    emergencyContactPhone = emergencyContactPhone.trim().ifEmpty { null },
                    updatedAt = System.currentTimeMillis(),
                )
            )
            isSaving = false
        }
    }

    fun addCondition() {
        val t = newCondition.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            healthProfileRepository.addCondition(ConditionEntity(name = t, isActive = true))
            newCondition = ""
        }
    }

    fun deleteCondition(condition: ConditionEntity) {
        viewModelScope.launch { healthProfileRepository.deleteCondition(condition) }
    }

    fun addMedication() {
        if (newMedName.isBlank() || newMedDosage.isBlank() || newMedFrequency.isBlank()) return
        viewModelScope.launch {
            healthProfileRepository.addMedication(
                MedicationEntity(
                    name = newMedName.trim(),
                    dosage = newMedDosage.trim(),
                    frequency = newMedFrequency.trim(),
                )
            )
            newMedName = ""; newMedDosage = ""; newMedFrequency = ""
        }
    }

    fun deleteMedication(medication: MedicationEntity) {
        viewModelScope.launch { healthProfileRepository.deleteMedication(medication) }
    }

    fun addAllergy() {
        val t = newAllergySubstance.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            healthProfileRepository.addAllergy(AllergyEntity(substance = t, severity = newAllergySeverity))
            newAllergySubstance = ""
            newAllergySeverity = "Unknown"
        }
    }

    fun deleteAllergy(allergy: AllergyEntity) {
        viewModelScope.launch { healthProfileRepository.deleteAllergy(allergy) }
    }
}
