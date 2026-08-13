# Aegis Secure Transfer Protocol (ASTP) v1.1

## Changelog from v1.0

| Issue | Severity | Fix |
|---|---|---|
| Patient signing key reused for ECDH | High | Separate ephemeral ECDH keypair per transfer, signed by TEE |
| No doctor device verification by patient | Critical | Short Authentication String (SAS) comparison step added |
| Missing IV derivation | High | IV derived from HKDF alongside AES key |
| Timestamp not verified | Medium | Freshness check ±5 minutes added on doctor side |
| TOTP using SHA-1 | Low | Switched to SHA-256 |
| No key confirmation | Low | Known constant included in plaintext for decryption verification |

---

## Overview

ASTP is a cryptographic handshake protocol for patient-controlled medical record transfer. It enables a patient to share encrypted health records with a doctor's device such that no intermediary — including the relay server — has access to plaintext records at any point. The protocol requires explicit biometric approval from the patient and mutual visual verification of a Short Authentication String before any data is released.

---

## Actors

| Actor | Role |
|---|---|
| **Patient Device** | Holds encrypted records and non-exportable private signing key in TEE. Generates a fresh ephemeral ECDH keypair per transfer. |
| **Doctor Device** | Any device with a browser, no app required. Generates ephemeral ECDH keypair in browser memory. |
| **Relay Server** | Dumb message bus — forwards encrypted blobs, verifies signatures, stores nothing meaningful. |

---

## Cryptographic Primitives

| Primitive | Usage |
|---|---|
| ECDH (P-256) | Ephemeral key exchange — both patient and doctor generate fresh keypairs per session |
| AES-256-GCM | Symmetric encryption of health record payload |
| ECDSA (P-256) | Patient signs ephemeral public key — binds session to patient identity, non-repudiation |
| HKDF (SHA-256) | Key and IV derivation from shared secret |
| TOTP (HMAC-SHA256, 30s window) | Session binding and replay protection |
| SHA-256 | Public key fingerprinting, SAS derivation |

---

## Key Separation Principle

The patient device maintains two distinct keys:

- **Long-term signing key** (TEE, non-exportable) — used only to sign the ephemeral ECDH public key. Never touches key agreement.
- **Ephemeral ECDH keypair** (generated fresh per transfer, discarded after session) — used only for key agreement. Never used for signing.

This ensures forward secrecy: even if the long-term signing key is later compromised, past session keys cannot be recovered because the ephemeral private keys are gone.

---

## Protocol Flow

### Phase 1 — Session Initiation (Patient)

Patient selects records to share and authenticates biometrically. Biometric unlocks the long-term private signing key in TEE.

Inside TEE:
```
1. Generate ephemeral ECDH keypair:
   patient_eph_private, patient_eph_public = ECDH.generate()

2. Sign the ephemeral public key with the long-term signing key:
   patient_eph_signature = ECDSA(patient_signing_key, patient_eph_public + session_id)

3. patient_eph_private stays in TEE memory for this session only
```

App requests a session slot from relay server:

```
POST /session/create
→ { session_id, relay_url, totp_seed, expiry: 300s }
```

App generates QR containing:

```json
{
  "session_id": "uuid-v4",
  "relay_url": "https://relay.aegis.app/s/uuid-v4",
  "patient_key_fingerprint": "sha256(patient_long_term_public_key)",
  "patient_eph_public": "base64_encoded",
  "patient_eph_signature": "base64_encoded",
  "totp_seed": "base32_encoded_seed",
  "expires_in": 300
}
```

Patient's screen shows QR. Transfer has not happened. Payload has not been sent anywhere.

---

### Phase 2 — Doctor Handshake

Doctor scans QR with any camera app. Browser opens relay URL.

Browser immediately:

```javascript
// Generate ephemeral keypair — stays in browser memory only, never exported
const doc_keypair = await crypto.subtle.generateKey(
  { name: "ECDH", namedCurve: "P-256" },
  false, // non-exportable
  ["deriveKey", "deriveBits"]
)

// Derive current TOTP token from seed in QR (HMAC-SHA256)
const totp_token = TOTP.generate(totp_seed, { algorithm: "SHA256" })

// Derive shared secret immediately to compute SAS
const shared_secret = await crypto.subtle.deriveBits(
  { name: "ECDH", public: patient_eph_public },
  doc_keypair.privateKey,
  256
)

// Derive SAS — 6 digit code shown to doctor
const sas_raw = await crypto.subtle.digest(
  "SHA-256",
  concat(shared_secret, "aegis-sas-v1")
)
const sas = (new DataView(sas_raw).getUint32(0) % 1000000).toString().padStart(6, "0")

// Send doc public key + TOTP to relay
POST /session/{session_id}/join
{
  "doc_public_key": exportedDocPublicKey,
  "totp_token": totp_token
}
```

Relay verifies TOTP token (HMAC-SHA256). If valid, stores `doc_public_key` against session.

Doctor's browser shows:

> **"Waiting for patient approval..."**
> **Verification code: 4 8 2 1 5 3**
> *Ask the patient to confirm this code matches their screen.*

---

### Phase 3 — SAS Verification (Critical)

Relay notifies patient's app with `doc_public_key`.

Patient's app derives the same SAS independently:

```kotlin
val sharedSecret = ECDH.derive(patient_eph_private, doc_public_key)
val sasRaw = SHA256(sharedSecret + "aegis-sas-v1")
val sas = (DataView(sasRaw).getUint32(0) % 1_000_000).toString().padStart(6, '0')
```

Patient's app shows:

> **"A device is requesting your records."**
> **Verification code: 4 8 2 1 5 3**
> *Confirm this matches the doctor's screen before approving.*
> **[Approve] [Deny]**

Patient verbally confirms the 6-digit code with the doctor. Only then taps Approve.

**If codes do not match:** an attacker has substituted their public key. Patient taps Deny. Session is invalidated.

**Second biometric prompt fires on Approve** — confirms intent, not just identity.

---

### Phase 4 — Cryptographic Authorization (TEE)

Inside the TEE, after patient approval:

```
1. Derive shared secret:
   shared_secret = ECDH(patient_eph_private, doc_public_key)

2. Derive AES key AND IV from shared secret:
   aes_key (256-bit), iv (96-bit) = HKDF(
     shared_secret,
     salt = session_id,
     info = "aegis-transfer-v1",
     length = 256 + 96 bits
   )

3. Build plaintext payload:
   plaintext = {
     magic: "AEGIS-TRANSFER-V1",   // known constant for decryption verification
     session_id: session_id,
     records: selected_records,
     timestamp: unix_timestamp_now
   }

4. Encrypt:
   ciphertext = AES-256-GCM(aes_key, iv, plaintext)

5. Sign ciphertext:
   signature = ECDSA(
     patient_signing_key,
     ciphertext + session_id + timestamp
   )

6. Send to relay:
   POST /session/{session_id}/payload
   {
     "ciphertext": base64(ciphertext),
     "signature": base64(signature),
     "patient_long_term_public_key": base64(patient_long_term_public_key),
     "patient_eph_public": base64(patient_eph_public),
     "patient_eph_signature": base64(patient_eph_signature),
     "timestamp": unix_timestamp
   }

7. Discard patient_eph_private from TEE memory.
```

---

### Phase 5 — Doctor Receives, Verifies, Decrypts

Relay forwards payload to doctor's browser.

Browser verifies in order:

```javascript
// 1. Verify patient long-term key matches QR fingerprint
const fingerprint = sha256(patient_long_term_public_key)
assert(fingerprint === qr.patient_key_fingerprint)

// 2. Verify ephemeral public key was signed by patient long-term key
const ephValid = await crypto.subtle.verify(
  "ECDSA",
  patient_long_term_public_key,
  patient_eph_signature,
  patient_eph_public + session_id
)
assert(ephValid)

// 3. Verify timestamp freshness (±5 minutes)
const age = Math.abs(Date.now() / 1000 - payload.timestamp)
assert(age < 300)

// 4. Verify ciphertext signature
const sigValid = await crypto.subtle.verify(
  "ECDSA",
  patient_long_term_public_key,
  signature,
  ciphertext + session_id + timestamp
)
assert(sigValid)

// 5. Derive same AES key and IV
const shared_secret = await crypto.subtle.deriveBits(
  { name: "ECDH", public: patient_eph_public },
  doc_keypair.privateKey,
  256
)
const { aes_key, iv } = HKDF(shared_secret, session_id, "aegis-transfer-v1")

// 6. Decrypt
const plaintext = AES_GCM_decrypt(aes_key, iv, ciphertext)

// 7. Verify magic constant
assert(plaintext.magic === "AEGIS-TRANSFER-V1")

// 8. Display records
render(plaintext.records)
```

Records displayed inline in browser. No download prompt. No server storage. Browser memory cleared on tab close. `doc_keypair.privateKey` discarded.

---

## Security Properties

| Threat | Mitigation |
|---|---|
| QR intercepted / photographed | TOTP window limits join attempts. SAS verification catches key substitution. |
| Relay server compromised | Sees only ciphertext encrypted with doc's ephemeral key — useless without `doc_private_key` |
| MITM key substitution | SAS verification — attacker cannot force SAS to match without controlling both endpoints |
| Replay attack | TOTP 30s window + session expiry + timestamp freshness check |
| Stale QR reuse | 300s session expiry, one-time session token |
| Patient long-term key theft | Non-exportable TEE key — biometric-gated, physically tied to device |
| Patient ephemeral key theft | Discarded from TEE immediately after payload sent — forward secrecy |
| Doc private key theft | Ephemeral, non-exportable, lives only in browser memory, discarded after session |
| Wrong decryption key | AES-GCM authentication tag + magic constant verification catches any mismatch |
| Timestamp manipulation | Freshness check ±5 minutes — old signed payloads cannot be replayed |

---

## Relay Server Contract

The relay server is intentionally dumb:

- Creates session slots
- Verifies TOTP tokens (HMAC-SHA256)
- Forwards encrypted payloads between parties
- Enforces session expiry (configurable 1–30 min, default 5 min)
- Writes signed audit log (session_id, timestamps, key fingerprints — no plaintext, no ciphertext content)
- Deletes all session data after transfer completes or expiry
- Exposes read-only audit API to patient: *"Shared X records with device fingerprint Y on date Z"*

The relay is open source and self-hostable. Clinics and hospitals that want full control can run their own instance as a simple container.

---

## Protocol Sequence Diagram

```
Patient                         Relay                    Doctor Browser
   |                               |                          |
   | Generate eph keypair (TEE)    |                          |
   | Sign eph_pub with signing key |                          |
   |                               |                          |
   |--POST /session/create-------->|                          |
   |<--{session_id, totp_seed}-----|                          |
   |                               |                          |
   |       [QR displayed]          |<--POST /session/join-----|
   |                               |   {doc_pub, totp_token}  |
   |                               |--verify TOTP (SHA-256)-->|
   |<--notify {doc_pub_key}--------|                          |
   |                               |              [Derive SAS from shared secret]
   | [Derive same SAS]             |              "Waiting... Code: 482153"
   | "Code: 482153 — confirm?"     |                          |
   |                               |                          |
   |       [Verbal SAS check]<=========================>      |
   |                               |                          |
   | [Biometric x2]                |                          |
   | Derive shared secret (TEE)    |                          |
   | Derive aes_key + iv (HKDF)    |                          |
   | Encrypt + sign payload        |                          |
   | Discard eph_private           |                          |
   |                               |                          |
   |--POST /session/payload------->|                          |
   |  {ciphertext, sig, keys}      |--forward--------------->|
   |                               |                          |
   |                               |          [Verify fingerprint]
   |                               |          [Verify eph_sig]
   |                               |          [Verify timestamp ±5min]
   |                               |          [Verify ciphertext sig]
   |                               |          [Derive shared secret]
   |                               |          [Derive aes_key + iv]
   |                               |          [Decrypt + verify magic]
   |                               |          [Display records]
   |                               |          [Discard doc_private]
```

---

## Open Questions (Resolved)

| Question | Decision |
|---|---|
| Self-hostable from day one? | Yes — open source container, hospitals demand it |
| Printing or on-screen only? | Printing allowed with explicit patient consent flag in share request |
| Audit log patient-accessible? | Yes — read-only API exposing session_id, timestamps, key fingerprints |
| Offline clinic fallback? | Local hotspot relay on patient device — v2 |
| Session expiry? | Configurable 1–30 min, default 5 min |

---

## Relationship to Existing Standards

ASTP draws from and is philosophically aligned with:

- **WebAuthn / FIDO2** — biometric-gated non-exportable TEE key usage
- **Signal Protocol** — ephemeral keypairs, forward secrecy, SAS verification, no server plaintext access
- **TLS 1.3** — ephemeral ECDH for forward secrecy, key separation
- **TOTP (RFC 6238)** — time-based session binding, updated to HMAC-SHA256
- **HKDF (RFC 5869)** — structured key and IV derivation

The novel contribution is the application of these primitives to patient-controlled health record transfer with explicit per-transfer biometric approval and SAS verification, in a context where the receiving party has no pre-enrolled identity in the system.

---

*Aegis Secure Transfer Protocol v1.1 — K. R. Balasubramanian — May 2026*
*Reviewed and updated following security analysis — critical MITM and key separation issues resolved.*
