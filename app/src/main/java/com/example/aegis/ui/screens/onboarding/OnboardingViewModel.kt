package com.example.aegis.ui.screens.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val healthProfileRepository: HealthProfileRepository,
) : ViewModel() {

    // ── Step 0 — Profile ──────────────────────────────────────────────────────
    var name by mutableStateOf("")
    var bloodType by mutableStateOf("Unknown")
    var gender by mutableStateOf("")
    var emergencyContactName by mutableStateOf("")
    var emergencyContactPhone by mutableStateOf("")

    // ── Step 1 — Conditions ───────────────────────────────────────────────────
    val conditions = mutableStateListOf<String>()
    var newCondition by mutableStateOf("")

    fun addCondition() {
        val t = newCondition.trim()
        if (t.isNotEmpty()) { conditions.add(t); newCondition = "" }
    }
    fun removeCondition(index: Int) = conditions.removeAt(index)

    // ── Step 2 — Medications ──────────────────────────────────────────────────
    data class MedEntry(val name: String, val dosage: String, val frequency: String)

    val medications = mutableStateListOf<MedEntry>()
    var newMedName by mutableStateOf("")
    var newMedDosage by mutableStateOf("")
    var newMedFrequency by mutableStateOf("")

    fun addMedication() {
        if (newMedName.isNotBlank() && newMedDosage.isNotBlank() && newMedFrequency.isNotBlank()) {
            medications.add(MedEntry(newMedName.trim(), newMedDosage.trim(), newMedFrequency.trim()))
            newMedName = ""; newMedDosage = ""; newMedFrequency = ""
        }
    }
    fun removeMedication(index: Int) = medications.removeAt(index)

    // ── Step 3 — Allergies ────────────────────────────────────────────────────
    data class AllergyEntry(val substance: String, val severity: String)

    val allergies = mutableStateListOf<AllergyEntry>()
    var newAllergySubstance by mutableStateOf("")
    var newAllergySeverity by mutableStateOf("Unknown")

    fun addAllergy() {
        val t = newAllergySubstance.trim()
        if (t.isNotEmpty()) {
            allergies.add(AllergyEntry(t, newAllergySeverity))
            newAllergySubstance = ""
            newAllergySeverity = "Unknown"
        }
    }
    fun removeAllergy(index: Int) = allergies.removeAt(index)

    // ── Save ──────────────────────────────────────────────────────────────────
    var isSaving by mutableStateOf(false)
        private set

    fun save(onComplete: () -> Unit) {
        viewModelScope.launch {
            isSaving = true
            patientRepository.savePatient(
                PatientEntity(
                    name = name.trim(),
                    bloodType = bloodType,
                    gender = gender.ifEmpty { null },
                    emergencyContactName = emergencyContactName.trim().ifEmpty { null },
                    emergencyContactPhone = emergencyContactPhone.trim().ifEmpty { null },
                )
            )
            conditions.forEach {
                healthProfileRepository.addCondition(ConditionEntity(name = it, isActive = true))
            }
            medications.forEach {
                healthProfileRepository.addMedication(
                    MedicationEntity(name = it.name, dosage = it.dosage, frequency = it.frequency)
                )
            }
            allergies.forEach {
                healthProfileRepository.addAllergy(
                    AllergyEntity(substance = it.substance, severity = it.severity)
                )
            }
            isSaving = false
            onComplete()
        }
    }
}
