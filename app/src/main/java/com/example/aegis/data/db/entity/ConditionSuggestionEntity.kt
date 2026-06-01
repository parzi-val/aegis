package com.example.aegis.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "condition_suggestion")
data class ConditionSuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceDocumentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val dismissed: Boolean = false,
)
