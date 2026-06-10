package com.example.aegis.data.astp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AstpService @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val json = "application/json".toMediaType()

    suspend fun createSession(req: CreateSessionRequest): CreateSessionResponse =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("session_id",             req.sessionId)
                put("patient_eph_public",     req.patientEphPublic)
                put("patient_eph_signature",  req.patientEphSignature)
                put("patient_key_fingerprint",req.patientKeyFingerprint)
                put("expires_in",             req.expiresIn)
            }.toString().toRequestBody(json)

            val resp = okHttpClient.newCall(
                Request.Builder().url("$BASE_URL/session/create").post(body).build()
            ).execute()

            check(resp.isSuccessful) { "createSession failed: ${resp.code}" }
            val j = JSONObject(resp.body!!.string())
            CreateSessionResponse(
                sessionId = j.getString("session_id"),
                totpSeed  = j.getString("totp_seed"),
                expiry    = j.getLong("expiry"),
            )
        }

    suspend fun postPayload(sessionId: String, req: PayloadRequest) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("ciphertext",                   req.ciphertext)
                put("signature",                    req.signature)
                put("patient_long_term_public_key", req.patientLongTermPublicKey)
                put("patient_eph_public",           req.patientEphPublic)
                put("patient_eph_signature",        req.patientEphSignature)
                put("timestamp",                    req.timestamp)
            }.toString().toRequestBody(json)

            val resp = okHttpClient.newCall(
                Request.Builder()
                    .url("$BASE_URL/session/$sessionId/payload")
                    .post(body)
                    .build()
            ).execute()

            check(resp.isSuccessful) { "postPayload failed: ${resp.code}" }
        }

    /**
     * Opens an SSE stream for the patient role.
     * Events are delivered on OkHttp's internal thread — call site must dispatch to main if needed.
     * Returns the [EventSource]; call [EventSource.cancel] to close.
     */
    fun listenForEvents(
        sessionId: String,
        onEvent: (AstpEvent) -> Unit,
        onError: (String) -> Unit,
    ): EventSource {
        // SSE requires no read timeout
        val sseClient = okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/session/$sessionId/events?role=patient")
            .header("Accept", "text/event-stream")
            .build()

        return EventSources.createFactory(sseClient).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    try {
                        when (type) {
                            "doctor_joined" -> {
                                val j = JSONObject(data)
                                onEvent(AstpEvent.DoctorJoined(j.getString("doc_public_key")))
                            }
                            "audit" -> {
                                val j = JSONObject(data)
                                onEvent(AstpEvent.Audit(
                                    action      = j.getString("action"),
                                    timestamp   = j.getLong("timestamp"),
                                    deviceHint  = j.optString("device_hint", ""),
                                ))
                            }
                            "session_expired" -> onEvent(AstpEvent.SessionExpired)
                        }
                    } catch (e: Exception) {
                        onError("SSE parse error: ${e.message}")
                    }
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: okhttp3.Response?,
                ) {
                    onError(t?.message ?: "SSE connection failed (${response?.code})")
                }
            },
        )
    }

    companion object {
        val BASE_URL get() = com.example.aegis.BuildConfig.RELAY_URL
    }
}
