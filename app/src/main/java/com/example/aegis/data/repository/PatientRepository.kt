package com.example.aegis.data.repository

import com.example.aegis.data.db.dao.PatientDao
import com.example.aegis.data.db.entity.PatientEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientRepository @Inject constructor(
    private val patientDao: PatientDao,
) {
    val patient: Flow<PatientEntity?> = patientDao.getPatient()

    suspend fun savePatient(patient: PatientEntity) = patientDao.upsert(patient)

    suspend fun getPatientOnce(): PatientEntity? = patientDao.getPatientOnce()

    suspend fun deletePatient(patient: PatientEntity) = patientDao.delete(patient)
}
