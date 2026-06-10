package com.example.aegis.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aegis.data.db.entity.AllergyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AllergyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(allergy: AllergyEntity): Long

    @Update
    suspend fun update(allergy: AllergyEntity)

    @Delete
    suspend fun delete(allergy: AllergyEntity)

    @Query("SELECT * FROM allergy ORDER BY substance ASC")
    fun getAllAllergies(): Flow<List<AllergyEntity>>

    @Query("SELECT * FROM allergy")
    suspend fun getAllOnce(): List<AllergyEntity>

    @Query("DELETE FROM allergy")
    suspend fun deleteAll()
}
