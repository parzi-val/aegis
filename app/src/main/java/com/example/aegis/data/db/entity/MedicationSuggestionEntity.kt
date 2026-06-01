package com.example.aegis.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_suggestion")
data class MedicationSuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String = "",
    val frequency: String = "",
    val sourceDocumentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dismissed: Boolean = false,
)
