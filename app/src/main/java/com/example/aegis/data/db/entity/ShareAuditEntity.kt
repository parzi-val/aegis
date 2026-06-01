package com.example.aegis.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "share_audit")
data class ShareAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val documentId: Long,
    val timestamp: Long,
    val action: String,
    val deviceHint: String,
    val userNote: String = "",
)
