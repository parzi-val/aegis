package com.example.aegis.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aegis.data.db.entity.VisitLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(visit: VisitLogEntity): Long

    @Update
    suspend fun update(visit: VisitLogEntity)

    @Delete
    suspend fun delete(visit: VisitLogEntity)

    @Query("SELECT * FROM visit_log ORDER BY visitDate DESC")
    fun getAllVisits(): Flow<List<VisitLogEntity>>

    @Query("SELECT * FROM visit_log WHERE id = :id")
    suspend fun getById(id: Long): VisitLogEntity?

    @Query("SELECT * FROM visit_log")
    suspend fun getAllOnce(): List<VisitLogEntity>

    @Query("DELETE FROM visit_log")
    suspend fun deleteAll()
}
