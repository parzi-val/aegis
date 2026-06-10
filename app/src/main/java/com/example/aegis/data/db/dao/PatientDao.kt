package com.example.aegis.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.aegis.data.db.entity.PatientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Upsert
    suspend fun upsert(patient: PatientEntity)

    @Query("SELECT * FROM patient WHERE id = 1")
    fun getPatient(): Flow<PatientEntity?>

    @Query("SELECT * FROM patient WHERE id = 1")
    suspend fun getPatientOnce(): PatientEntity?

    @Delete
    suspend fun delete(patient: PatientEntity)

    @Query("DELETE FROM patient")
    suspend fun deleteAll()
}
