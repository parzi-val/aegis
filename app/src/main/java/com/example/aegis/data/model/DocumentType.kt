package com.example.aegis.data.model

enum class DocumentType(val displayName: String) {
    PRESCRIPTION("Prescription"),
    LAB_REPORT("Lab Report"),
    DISCHARGE_SUMMARY("Discharge Summary"),
    SCAN("Scan / Imaging"),
    INSURANCE("Insurance"),
    UNKNOWN("Unknown"),
}
