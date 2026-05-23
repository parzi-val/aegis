package com.example.aegis.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "allergy")
data class AllergyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val substance: String,
    val severity: String = "Unknown",   // Mild / Moderate / Severe / Unknown
    val reaction: String = "",
)
