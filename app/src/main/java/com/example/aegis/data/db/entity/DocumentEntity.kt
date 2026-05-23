package com.example.aegis.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// documentType stores DocumentType.name() — converted in the repository layer
@Entity(tableName = "document")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val documentType: String = "UNKNOWN",
    val mimeType: String = "",
    val localPath: String,              // path inside app's encrypted private storage
    val uploadedAt: Long = System.currentTimeMillis(),
    val documentDate: Long? = null,     // date printed on the document (e.g. lab report date)
    val conditionId: Long? = null,      // links to ConditionEntity.id
    val providerName: String = "",      // clinic / hospital name
    val tags: String = "",              // comma-separated free-text tags
    val ocrText: String? = null,        // raw text from ML Kit (Phase 6)
    val extractedFields: String? = null,// JSON blob of structured NLP output (Phase 6)
    val thumbnailPath: String? = null,  // cached thumbnail path (Phase 5)
    val extractionState: String = "PENDING", // PENDING | DONE | SKIPPED | ERROR
)
