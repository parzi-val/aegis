package com.example.aegis.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.aegis.data.db.entity.ShareAuditEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShareAuditDao {
    @Query("SELECT * FROM share_audit WHERE documentId = :docId ORDER BY timestamp DESC")
    fun getAuditsForDocument(docId: Long): Flow<List<ShareAuditEntity>>

    @Insert
    suspend fun insert(entry: ShareAuditEntity): Long

    @Update
    suspend fun update(entry: ShareAuditEntity)
}
