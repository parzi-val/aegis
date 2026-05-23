package com.example.aegis.data.repository

import com.example.aegis.data.db.dao.AllergyDao
import com.example.aegis.data.db.dao.ConditionDao
import com.example.aegis.data.db.dao.MedicationDao
import com.example.aegis.data.db.entity.AllergyEntity
import com.example.aegis.data.db.entity.ConditionEntity
import com.example.aegis.data.db.entity.MedicationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthProfileRepository @Inject constructor(
    private val conditionDao: ConditionDao,
    private val medicationDao: MedicationDao,
    private val allergyDao: AllergyDao,
) {
    val conditions: Flow<List<ConditionEntity>> = conditionDao.getAllConditions()
    val activeConditions: Flow<List<ConditionEntity>> = conditionDao.getActiveConditions()
    val medications: Flow<List<MedicationEntity>> = medicationDao.getAllMedications()
    val activeMedications: Flow<List<MedicationEntity>> = medicationDao.getActiveMedications()
    val allergies: Flow<List<AllergyEntity>> = allergyDao.getAllAllergies()

    suspend fun addCondition(condition: ConditionEntity) = conditionDao.insert(condition)
    suspend fun updateCondition(condition: ConditionEntity) = conditionDao.update(condition)
    suspend fun deleteCondition(condition: ConditionEntity) = conditionDao.delete(condition)

    suspend fun addMedication(medication: MedicationEntity) = medicationDao.insert(medication)
    suspend fun updateMedication(medication: MedicationEntity) = medicationDao.update(medication)
    suspend fun deleteMedication(medication: MedicationEntity) = medicationDao.delete(medication)

    // TODO Phase 10: run drug interaction check before persisting a new medication

    suspend fun getConditionById(id: Long): ConditionEntity? = conditionDao.getById(id)

    suspend fun addAllergy(allergy: AllergyEntity) = allergyDao.insert(allergy)
    suspend fun updateAllergy(allergy: AllergyEntity) = allergyDao.update(allergy)
    suspend fun deleteAllergy(allergy: AllergyEntity) = allergyDao.delete(allergy)
}
