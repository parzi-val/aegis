package com.example.aegis.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.aegis.data.db.entity.MedicationSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationSuggestionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(suggestion: MedicationSuggestionEntity): Long

    @Query("SELECT * FROM medication_suggestion WHERE dismissed = 0 ORDER BY createdAt DESC")
    fun getPending(): Flow<List<MedicationSuggestionEntity>>

    @Query("UPDATE medication_suggestion SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("SELECT COUNT(*) > 0 FROM medication_suggestion WHERE LOWER(name) = LOWER(:name)")
    suspend fun existsByName(name: String): Boolean
}
