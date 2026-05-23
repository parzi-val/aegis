package com.example.aegis.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "condition")
data class ConditionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val diagnosedAt: Long? = null,
    val isActive: Boolean = true,
    val notes: String = "",
)
