package com.example.aegis.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patient")
data class PatientEntity(
    @PrimaryKey val id: Int = 1,        // singleton — one profile per device
    val name: String,
    val bloodType: String = "Unknown",  // e.g. "A+", "O-", "Unknown"
    val dateOfBirth: Long? = null,      // Unix ms; null if not provided
    val gender: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
