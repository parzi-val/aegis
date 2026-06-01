package com.example.aegis.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "visit_log")
data class VisitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitDate: Long = System.currentTimeMillis(),
    val doctorName: String = "",
    val clinicName: String = "",
    val diagnosis: String = "",
    val prescription: String = "",
    val notes: String = "",
    val followUpDate: Long? = null,
    val linkedDocumentIds: String = "", // comma-separated DocumentEntity ids
    val conditionTags: String = "",    // comma-separated free-text condition labels
)
