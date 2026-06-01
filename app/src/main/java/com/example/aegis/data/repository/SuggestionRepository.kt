package com.example.aegis.data.repository

import com.example.aegis.data.db.dao.ConditionDao
import com.example.aegis.data.db.dao.ConditionSuggestionDao
import com.example.aegis.data.db.dao.MedicationDao
import com.example.aegis.data.db.dao.MedicationSuggestionDao
import com.example.aegis.data.db.entity.ConditionEntity
import com.example.aegis.data.db.entity.ConditionSuggestionEntity
import com.example.aegis.data.db.entity.MedicationEntity
import com.example.aegis.data.db.entity.MedicationSuggestionEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuggestionRepository @Inject constructor(
    private val conditionSuggestionDao: ConditionSuggestionDao,
    private val medicationSuggestionDao: MedicationSuggestionDao,
    private val conditionDao: ConditionDao,
    private val medicationDao: MedicationDao,
) {
    val pendingConditions: Flow<List<ConditionSuggestionEntity>> = conditionSuggestionDao.getPending()
    val pendingMedications: Flow<List<MedicationSuggestionEntity>> = medicationSuggestionDao.getPending()

    suspend fun processExtraction(docId: Long, extractedJson: String) {
        val json = try { JSONObject(extractedJson) } catch (_: Exception) { return }

        val existingConditionNames = conditionDao.getAllNames().map { it.lowercase() }.toSet()
        val conditionsArr = json.optJSONArray("conditions")
        if (conditionsArr != null) {
            for (i in 0 until conditionsArr.length()) {
                // conditions[] may be strings or objects with a "name" key
                val name = when (val v = conditionsArr.opt(i)) {
                    is String -> v.trim()
                    is JSONObject -> v.optString("name").trim()
                    else -> ""
                }.ifBlank { continue }

                if (name.lowercase() !in existingConditionNames &&
                    !conditionSuggestionDao.existsByName(name)
                ) {
                    conditionSuggestionDao.insert(
                        ConditionSuggestionEntity(name = name, sourceDocumentId = docId)
                    )
                }
            }
        }

        val existingMedNames = medicationDao.getAllNames().map { it.lowercase() }.toSet()
        val medsArr = json.optJSONArray("medications")
        if (medsArr != null) {
            for (i in 0 until medsArr.length()) {
                val medObj = medsArr.optJSONObject(i) ?: continue
                val name = medObj.optString("name").trim().ifBlank { continue }
                if (name.lowercase() !in existingMedNames &&
                    !medicationSuggestionDao.existsByName(name)
                ) {
                    medicationSuggestionDao.insert(
                        MedicationSuggestionEntity(
                            name = name,
                            dosage = medObj.optString("dosage").trim(),
                            frequency = medObj.optString("frequency").trim(),
                            sourceDocumentId = docId,
                        )
                    )
                }
            }
        }
    }

    suspend fun acceptCondition(suggestion: ConditionSuggestionEntity) {
        conditionDao.insert(ConditionEntity(name = suggestion.name))
        conditionSuggestionDao.dismiss(suggestion.id)
    }

    suspend fun dismissCondition(id: Long) = conditionSuggestionDao.dismiss(id)

    suspend fun acceptMedication(suggestion: MedicationSuggestionEntity) {
        medicationDao.insert(
            MedicationEntity(
                name = suggestion.name,
                dosage = suggestion.dosage,
                frequency = suggestion.frequency,
            )
        )
        medicationSuggestionDao.dismiss(suggestion.id)
    }

    suspend fun dismissMedication(id: Long) = medicationSuggestionDao.dismiss(id)
}
