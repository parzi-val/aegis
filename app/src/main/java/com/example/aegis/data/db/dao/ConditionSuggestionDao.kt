package com.example.aegis.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.aegis.data.db.entity.ConditionSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConditionSuggestionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(suggestion: ConditionSuggestionEntity): Long

    @Query("SELECT * FROM condition_suggestion WHERE dismissed = 0 ORDER BY createdAt DESC")
    fun getPending(): Flow<List<ConditionSuggestionEntity>>

    @Query("UPDATE condition_suggestion SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    // Returns true if name already exists (any state) — prevents re-suggesting dismissed items
    @Query("SELECT COUNT(*) > 0 FROM condition_suggestion WHERE LOWER(name) = LOWER(:name)")
    suspend fun existsByName(name: String): Boolean
}
