package com.example.aegis.data.astp

// ── Relay API request / response ────────────────────────────────────────────

data class CreateSessionRequest(
    val sessionId: String,             // client-generated UUID v4 (needed to sign eph_pub || session_id before relay call)
    val patientEphPublic: String,      // base64 raw P-256 point (65 bytes)
    val patientEphSignature: String,   // base64 DER ECDSA over (eph_pub || session_id)
    val patientKeyFingerprint: String, // hex SHA-256 of long-term pub key raw bytes
    val expiresIn: Int = 300,
)

data class CreateSessionResponse(
    val sessionId: String,
    val totpSeed: String,   // base32 — used to compute TOTP in QR fragment only
    val expiry: Long,       // unix seconds
)

data class PayloadRequest(
    val ciphertext: String,                  // base64 AES-256-GCM ciphertext+tag
    val signature: String,                   // base64 DER ECDSA over (ct || session_id || timestamp)
    val patientLongTermPublicKey: String,     // base64 raw P-256 point (65 bytes)
    val patientEphPublic: String,
    val patientEphSignature: String,
    val timestamp: Long,                     // unix seconds
)

// ── SSE events ───────────────────────────────────────────────────────────────

sealed class AstpEvent {
    data class DoctorJoined(val docPublicKey: String) : AstpEvent()
    data class Audit(
        val action: String,
        val timestamp: Long,
        val deviceHint: String,
    ) : AstpEvent()
    data object SessionExpired : AstpEvent()
}

// ── Plaintext payload (serialised to JSON, then AES-GCM encrypted) ───────────

data class TransferPayload(
    val magic: String = "AEGIS-TRANSFER-V1",
    val sessionId: String,
    val timestamp: Long,
    val patient: PatientSummary,
    val conditions: List<String>,
    val medications: List<MedSummary>,
    val allergies: List<AllergySummary>,
    val documents: List<DocSummary>,
)

data class PatientSummary(
    val name: String,
    val ageSex: String,   // e.g. "26M"
    val bloodType: String,
)

data class MedSummary(
    val name: String,
    val dosage: String,
    val frequency: String,
)

data class AllergySummary(
    val substance: String,
    val severity: String,
)

data class DocSummary(
    val id: Long,
    val type: String,
    val title: String,
    val provider: String,
    val date: String,
    val extractedFields: String?,  // raw JSON blob from extraction
)

// ── Audit log entry (persisted locally) ──────────────────────────────────────

data class AuditLogEntry(
    val sessionId: String,
    val action: String,
    val timestamp: Long,
    val deviceHint: String,
)
