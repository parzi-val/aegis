package com.example.aegis.ui.screens.transfer

import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.astp.AstpCrypto
import com.example.aegis.data.astp.AstpEvent
import com.example.aegis.data.astp.AstpKeyManager
import com.example.aegis.data.astp.AstpService
import com.example.aegis.data.astp.AuditLogEntry
import com.example.aegis.data.astp.CreateSessionRequest
import com.example.aegis.data.astp.PayloadRequest
import com.example.aegis.data.astp.toFixed32Bytes
import com.example.aegis.data.db.dao.AllergyDao
import com.example.aegis.data.db.dao.ConditionDao
import com.example.aegis.data.db.dao.DocumentDao
import com.example.aegis.data.db.dao.MedicationDao
import com.example.aegis.data.db.dao.PatientDao
import com.example.aegis.data.db.dao.ShareAuditDao
import com.example.aegis.data.db.entity.ShareAuditEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.sse.EventSource
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyPair
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

// ── State machine ─────────────────────────────────────────────────────────────

sealed class TransferState {
    data object Idle : TransferState()
    /**
     * ViewModel has prepared a Signature. UI must wrap it in
     * [androidx.biometric.BiometricPrompt.CryptoObject] and show the prompt.
     */
    data class AwaitingBiometric(
        val purpose: BiometricPurpose,
        val signature: Signature,
    ) : TransferState()
    data object PreparingSession : TransferState()
    data class ShowingQR(val url: String, val expiresAt: Long) : TransferState()
    data class ShowingSAS(val sas: String) : TransferState()
    data object Encrypting : TransferState()
    data class Complete(val auditLog: List<AuditLogEntry>) : TransferState()
    data class Cancelled(val reason: String) : TransferState()
    data class Error(val message: String) : TransferState()
}

enum class BiometricPurpose { SIGN_EPH_KEY, SIGN_PAYLOAD }

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class TransferViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val patientDao: PatientDao,
    private val conditionDao: ConditionDao,
    private val medicationDao: MedicationDao,
    private val allergyDao: AllergyDao,
    private val documentDao: DocumentDao,
    private val shareAuditDao: ShareAuditDao,
    private val keyManager: AstpKeyManager,
    private val astpService: AstpService,
) : ViewModel() {

    private val selectedDocIds: List<Long> = savedStateHandle
        .get<String>("docIds")
        .orEmpty()
        .split(",")
        .mapNotNull { it.trim().toLongOrNull() }

    val state: StateFlow<TransferState> get() = _state
    private val _state = MutableStateFlow<TransferState>(TransferState.Idle)

    // Session-scoped fields (all mutated only from coroutines, none exposed to UI directly)
    private var ephKeyPair: KeyPair? = null
    private var ephPublicRaw: ByteArray = ByteArray(0)
    private var storedEphSigBytes: ByteArray = ByteArray(0)  // saved from biometric #1 for payload reuse
    private var sessionId: String = ""
    private var totpSeed: String = ""
    private var sharedSecret: ByteArray? = null
    private var sseSource: EventSource? = null
    private val auditLog = mutableListOf<AuditLogEntry>()

    // ── Public entry points ───────────────────────────────────────────────────

    fun start() {
        if (_state.value !is TransferState.Idle) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                keyManager.ensureSigningKeyExists()

                // Generate session_id on client so we can bind it into the ephemeral key signature
                sessionId = UUID.randomUUID().toString()

                // Generate ephemeral ECDH keypair (in-memory, not TEE)
                val kp = keyManager.generateEphemeralKeyPair()
                ephKeyPair = kp
                val ecPub = kp.public as ECPublicKey
                ephPublicRaw = byteArrayOf(0x04) +
                    ecPub.w.affineX.toFixed32Bytes() +
                    ecPub.w.affineY.toFixed32Bytes()

                // Prepare TEE signing key — needs biometric auth before use
                val sig = keyManager.prepareSignature()
                _state.value = TransferState.AwaitingBiometric(BiometricPurpose.SIGN_EPH_KEY, sig)
            } catch (e: Exception) {
                _state.value = TransferState.Error("Key setup failed: ${e.message}")
            }
        }
    }

    /** Called by UI after BiometricPrompt succeeds. The authenticated Signature is single-use. */
    fun onBiometricSuccess(authenticatedSig: Signature) {
        when (val s = _state.value) {
            is TransferState.AwaitingBiometric -> when (s.purpose) {
                BiometricPurpose.SIGN_EPH_KEY -> signAndCreateSession(authenticatedSig)
                BiometricPurpose.SIGN_PAYLOAD -> encryptAndSend(authenticatedSig)
            }
            else -> Unit
        }
    }

    fun onBiometricError(code: Int, message: CharSequence) {
        _state.value = TransferState.Error("Biometric failed ($code): $message")
    }

    /** User tapped Approve after verifying SAS. Triggers biometric #2 for payload signing. */
    fun approve() {
        if (_state.value !is TransferState.ShowingSAS) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sig = keyManager.prepareSignature()
                _state.value = TransferState.AwaitingBiometric(BiometricPurpose.SIGN_PAYLOAD, sig)
            } catch (e: Exception) {
                _state.value = TransferState.Error("Could not prepare signing key: ${e.message}")
            }
        }
    }

    fun deny() {
        sseSource?.cancel()
        _state.value = TransferState.Cancelled("Transfer denied by patient.")
    }

    // ── Phase 1: sign eph key → POST /session/create → show QR ───────────────

    private fun signAndCreateSession(sig: Signature) {
        _state.value = TransferState.PreparingSession
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Sign: eph_pub_bytes || UTF-8(session_id)
                // session_id was generated on client so it's known before this call
                sig.update(ephPublicRaw)
                sig.update(sessionId.toByteArray())
                val ephSig = sig.sign()
                storedEphSigBytes = ephSig   // save for Phase 3 payload field

                val longTermPubRaw = keyManager.getSigningPublicKeyRaw()
                val fingerprint    = keyManager.getSigningKeyFingerprint()
                val b64            = { bytes: ByteArray -> Base64.encodeToString(bytes, Base64.NO_WRAP) }

                val resp = astpService.createSession(
                    CreateSessionRequest(
                        sessionId             = sessionId,
                        patientEphPublic      = b64(ephPublicRaw),
                        patientEphSignature   = b64(ephSig),
                        patientKeyFingerprint = fingerprint,
                    )
                )
                totpSeed = resp.totpSeed

                // Build QR URL: relay_url/s/{session_id}#{base64url(json)}
                // The fragment carries everything the browser needs and never touches relay logs.
                val qrFragmentJson = JSONObject().apply {
                    put("patient_eph_public",      b64(ephPublicRaw))
                    put("patient_eph_signature",   b64(ephSig))
                    put("patient_key_fingerprint", fingerprint)
                    put("totp_seed",               totpSeed)
                    put("expires_in",              300)
                }.toString()

                val fragment = Base64.encodeToString(
                    qrFragmentJson.toByteArray(),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                )
                val qrUrl = "${AstpService.BASE_URL}/s/$sessionId#$fragment"

                startSSE()

                _state.value = TransferState.ShowingQR(url = qrUrl, expiresAt = resp.expiry)
            } catch (e: Exception) {
                _state.value = TransferState.Error("Session creation failed: ${e.message}")
            }
        }
    }

    // ── Phase 2: doctor_joined → derive shared secret + SAS ──────────────────

    private fun startSSE() {
        sseSource = astpService.listenForEvents(
            sessionId = sessionId,
            onEvent   = { event -> viewModelScope.launch { handleSseEvent(event) } },
            onError   = { msg ->
                viewModelScope.launch {
                    if (_state.value !is TransferState.Complete &&
                        _state.value !is TransferState.Cancelled) {
                        _state.value = TransferState.Error("Connection lost: $msg")
                    }
                }
            },
        )
    }

    private fun handleSseEvent(event: AstpEvent) {
        when (event) {
            is AstpEvent.DoctorJoined -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val docPubRaw = Base64.decode(event.docPublicKey, Base64.DEFAULT)
                        val secret = AstpCrypto.deriveSharedSecret(
                            localPrivate  = ephKeyPair!!.private,
                            peerRawPublic = docPubRaw,
                        )
                        sharedSecret = secret
                        val sas = AstpCrypto.deriveSAS(secret)
                        _state.value = TransferState.ShowingSAS(sas)
                    } catch (e: Exception) {
                        _state.value = TransferState.Error("Key agreement failed: ${e.message}")
                    }
                }
            }
            is AstpEvent.Audit -> {
                val entry = AuditLogEntry(
                    sessionId  = sessionId,
                    action     = event.action,
                    timestamp  = event.timestamp,
                    deviceHint = event.deviceHint,
                )
                auditLog.add(entry)
                // Persist to DB for each selected document
                viewModelScope.launch(Dispatchers.IO) {
                    selectedDocIds.forEach { docId ->
                        shareAuditDao.insert(
                            ShareAuditEntity(
                                sessionId  = sessionId,
                                documentId = docId,
                                timestamp  = event.timestamp,
                                action     = event.action,
                                deviceHint = event.deviceHint,
                            )
                        )
                    }
                }
                _state.update { current ->
                    if (current is TransferState.Complete)
                        TransferState.Complete(auditLog.toList())
                    else current
                }
            }
            AstpEvent.SessionExpired -> {
                if (_state.value !is TransferState.Complete &&
                    _state.value !is TransferState.Cancelled) {
                    _state.value = TransferState.Error("Session expired before transfer completed.")
                }
            }
        }
    }

    // ── Phase 3: encrypt + sign + POST /session/payload ──────────────────────

    private fun encryptAndSend(sig: Signature) {
        _state.value = TransferState.Encrypting
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val secret    = sharedSecret ?: error("Shared secret not derived")
                val timestamp = System.currentTimeMillis() / 1000L
                val b64       = { bytes: ByteArray -> Base64.encodeToString(bytes, Base64.NO_WRAP) }

                val plaintext  = buildPlaintext(timestamp)
                val (aesKey, iv) = AstpCrypto.deriveKeyAndIV(secret, sessionId)
                val ciphertext = AstpCrypto.aesGcmEncrypt(aesKey, iv, plaintext)

                // Sign: ciphertext_bytes || UTF-8(session_id) || UTF-8(timestamp_decimal)
                // Must match doctor.html: concat(ciphertextBytes, enc.encode(sessionId), enc.encode(String(timestamp)))
                sig.update(ciphertext)
                sig.update(sessionId.toByteArray())
                sig.update(timestamp.toString().toByteArray())
                val ctSig = sig.sign()

                astpService.postPayload(
                    sessionId = sessionId,
                    req = PayloadRequest(
                        ciphertext               = b64(ciphertext),
                        signature                = b64(ctSig),
                        patientLongTermPublicKey = b64(keyManager.getSigningPublicKeyRaw()),
                        patientEphPublic         = b64(ephPublicRaw),
                        patientEphSignature      = b64(storedEphSigBytes),
                        timestamp                = timestamp,
                    ),
                )

                // Forward secrecy: discard ephemeral private key immediately
                ephKeyPair   = null
                sharedSecret = null

                // Save a transfer_completed entry for each selected document
                val completedAt = System.currentTimeMillis() / 1000L
                selectedDocIds.forEach { docId ->
                    shareAuditDao.insert(
                        ShareAuditEntity(
                            sessionId  = sessionId,
                            documentId = docId,
                            timestamp  = completedAt,
                            action     = "transfer_completed",
                            deviceHint = "",
                        )
                    )
                }

                _state.value = TransferState.Complete(auditLog.toList())
            } catch (e: Exception) {
                _state.value = TransferState.Error("Encryption or send failed: ${e.message}")
            }
        }
    }

    private suspend fun buildPlaintext(timestamp: Long): ByteArray = withContext(Dispatchers.IO) {
        val patient   = patientDao.getPatientOnce()
        val conditions = conditionDao.getActiveConditions().first().map { it.name }
        val meds      = medicationDao.getActiveMedications().first()
        val allergies = allergyDao.getAllAllergies().first()
        val docs      = if (selectedDocIds.isEmpty()) emptyList()
                        else documentDao.getByIds(selectedDocIds)
        val dateFmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        JSONObject().apply {
            put("magic",      "AEGIS-TRANSFER-V1")
            put("session_id", sessionId)
            put("timestamp",  timestamp)

            put("patient", JSONObject().apply {
                put("name",       patient?.name ?: "")
                put("blood_type", patient?.bloodType ?: "Unknown")
                put("age_sex", patient?.let { p ->
                    val age = p.dateOfBirth?.let { dob ->
                        ((System.currentTimeMillis() - dob) / (365.25 * 24 * 3600 * 1000)).toInt()
                    }
                    listOfNotNull(age?.toString(), p.gender?.take(1)?.uppercase()).joinToString("")
                } ?: "")
            })

            put("conditions", JSONArray(conditions))

            put("medications", JSONArray().also { arr ->
                meds.forEach { m ->
                    arr.put(JSONObject().apply {
                        put("name",      m.name)
                        put("dosage",    m.dosage)
                        put("frequency", m.frequency)
                    })
                }
            })

            put("allergies", JSONArray().also { arr ->
                allergies.forEach { a ->
                    arr.put(JSONObject().apply {
                        put("substance", a.substance)
                        put("severity",  a.severity)
                    })
                }
            })

            put("documents", JSONArray().also { arr ->
                docs.forEach { d ->
                    arr.put(JSONObject().apply {
                        put("id",               d.id)
                        put("type",             d.documentType)
                        put("title",            d.fileName)
                        put("provider",         d.providerName)
                        put("date",             d.documentDate?.let { dateFmt.format(Date(it)) } ?: "")
                        put("extracted_fields", d.extractedFields ?: "{}")
                    })
                }
            })
        }.toString().toByteArray()
    }

    override fun onCleared() {
        super.onCleared()
        sseSource?.cancel()
        ephKeyPair   = null
        sharedSecret = null
    }
}
