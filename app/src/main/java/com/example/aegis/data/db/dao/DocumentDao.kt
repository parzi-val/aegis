package com.example.aegis.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aegis.data.db.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity): Long

    @Update
    suspend fun update(document: DocumentEntity)

    @Delete
    suspend fun delete(document: DocumentEntity)

    @Query("SELECT * FROM document ORDER BY uploadedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    // Multi-dimensional vault queries
    @Query("SELECT * FROM document WHERE documentType = :type ORDER BY uploadedAt DESC")
    fun getByType(type: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM document WHERE conditionId = :conditionId ORDER BY uploadedAt DESC")
    fun getByCondition(conditionId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM document WHERE providerName = :providerName ORDER BY uploadedAt DESC")
    fun getByProvider(providerName: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM document ORDER BY documentDate DESC")
    fun getAllByDate(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM document WHERE id = :id")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT * FROM document WHERE id = :id")
    fun getByIdAsFlow(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM document WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<DocumentEntity>

    @Query("SELECT DISTINCT providerName FROM document WHERE providerName != '' ORDER BY providerName ASC")
    fun getDistinctProviderNames(): Flow<List<String>>
}
