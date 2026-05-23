package com.example.aegis.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String,
    val frequency: String,
    val startDate: Long? = null,
    val endDate: Long? = null,          // null = ongoing
    val prescribedBy: String = "",
    val isActive: Boolean = true,
    val notes: String = "",
)
