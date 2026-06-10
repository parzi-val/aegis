package com.example.aegis.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aegis.data.db.entity.ConditionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConditionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(condition: ConditionEntity): Long

    @Update
    suspend fun update(condition: ConditionEntity)

    @Delete
    suspend fun delete(condition: ConditionEntity)

    @Query("SELECT * FROM condition ORDER BY name ASC")
    fun getAllConditions(): Flow<List<ConditionEntity>>

    @Query("SELECT * FROM condition WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveConditions(): Flow<List<ConditionEntity>>

    @Query("SELECT * FROM condition WHERE id = :id")
    suspend fun getById(id: Long): ConditionEntity?

    @Query("SELECT name FROM condition")
    suspend fun getAllNames(): List<String>

    @Query("SELECT * FROM condition")
    suspend fun getAllOnce(): List<ConditionEntity>

    @Query("DELETE FROM condition")
    suspend fun deleteAll()
}
