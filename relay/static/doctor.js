'use strict'

// ---------------------------------------------------------------------------
// Utilities
// ---------------------------------------------------------------------------

const enc = new TextEncoder()
const dec = new TextDecoder()

function b64decode(str) {
  const s = str.replace(/-/g, '+').replace(/_/g, '/')
  const bin = atob(s)
  const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
  return bytes
}

function b64encode(bytes) {
  const arr = new Uint8Array(bytes)
  let bin = ''
  for (const b of arr) bin += String.fromCharCode(b)
  return btoa(bin)
}

function concatBytes(...arrays) {
  const total = arrays.reduce((n, a) => n + a.length, 0)
  const out = new Uint8Array(total)
  let offset = 0
  for (const a of arrays) { out.set(a, offset); offset += a.length }
  return out
}

function base32Decode(encoded) {
  const alpha = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
  let buf = 0, bits = 0
  const bytes = []
  for (const ch of encoded.toUpperCase().replace(/=/g, '')) {
    const val = alpha.indexOf(ch)
    if (val < 0) continue
    buf = (buf << 5) | val
    bits += 5
    if (bits >= 8) { bytes.push((buf >> (bits - 8)) & 0xff); bits -= 8 }
  }
  return new Uint8Array(bytes)
}

// DER-encoded ECDSA signature → raw 64-byte (r||s) for WebCrypto
function derToRawECDSA(der) {
  let i = 0
  if (der[i++] !== 0x30) throw new Error('Expected SEQUENCE in DER signature')
  if (der[i] & 0x80) i += (der[i] & 0x7f) + 1; else i++
  if (der[i++] !== 0x02) throw new Error('Expected INTEGER r')
  const rLen = der[i++]
  let r = der.slice(i, i + rLen); i += rLen
  if (der[i++] !== 0x02) throw new Error('Expected INTEGER s')
  const sLen = der[i++]
  let s = der.slice(i, i + sLen)
  while (r.length > 32 && r[0] === 0) r = r.slice(1)
  while (s.length > 32 && s[0] === 0) s = s.slice(1)
  const raw = new Uint8Array(64)
  raw.set(r, 32 - r.length)
  raw.set(s, 64 - s.length)
  return raw
}

function rawKeyToJWK(rawBytes) {
  const x = rawBytes.slice(1, 33)
  const y = rawBytes.slice(33, 65)
  const toB64url = (b) => btoa(String.fromCharCode(...b)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
  return { kty: 'EC', crv: 'P-256', x: toB64url(x), y: toB64url(y) }
}

function escHtml(s) {
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
}

// ---------------------------------------------------------------------------
// TOTP — HMAC-SHA256, 30-second window (matches pyotp digest=sha256)
// ---------------------------------------------------------------------------

async function generateTOTP(base32Seed) {
  const keyBytes = base32Decode(base32Seed)
  const counter = Math.floor(Date.now() / 1000 / 30)
  const counterBuf = new Uint8Array(8)
  new DataView(counterBuf.buffer).setBigInt64(0, BigInt(counter), false)

  const key = await crypto.subtle.importKey('raw', keyBytes, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'])
  const hmacBuf = new Uint8Array(await crypto.subtle.sign('HMAC', key, counterBuf))

  const offset = hmacBuf[hmacBuf.length - 1] & 0x0f
  const code = (
    ((hmacBuf[offset]     & 0x7f) << 24) |
    ((hmacBuf[offset + 1] & 0xff) << 16) |
    ((hmacBuf[offset + 2] & 0xff) << 8)  |
     (hmacBuf[offset + 3] & 0xff)
  ) % 1_000_000
  return code.toString().padStart(6, '0')
}

// ---------------------------------------------------------------------------
// SAS — SHA-256(sharedSecret || "aegis-sas-v1"), first uint32 % 1M
// ---------------------------------------------------------------------------

async function deriveSAS(sharedSecretBytes) {
  const msg = concatBytes(sharedSecretBytes, enc.encode('aegis-sas-v1'))
  const hash = await crypto.subtle.digest('SHA-256', msg)
  const code = new DataView(hash).getUint32(0, false) % 1_000_000
  return code.toString().padStart(6, '0')
}

// ---------------------------------------------------------------------------
// HKDF — 32-byte AES key + 12-byte IV
// salt=UTF-8(session_id), info=UTF-8("aegis-transfer-v1"), hash=SHA-256
// ---------------------------------------------------------------------------

async function deriveKeyAndIV(sharedSecretBytes, sessionId) {
  const baseKey = await crypto.subtle.importKey('raw', sharedSecretBytes, { name: 'HKDF' }, false, ['deriveBits'])
  const bits = await crypto.subtle.deriveBits(
    { name: 'HKDF', hash: 'SHA-256', salt: enc.encode(sessionId), info: enc.encode('aegis-transfer-v1') },
    baseKey,
    (32 + 12) * 8
  )
  const keyBytes = new Uint8Array(bits, 0, 32)
  const iv = new Uint8Array(bits, 32, 12)
  const aesKey = await crypto.subtle.importKey('raw', keyBytes, { name: 'AES-GCM' }, false, ['decrypt'])
  return { aesKey, iv }
}

// ---------------------------------------------------------------------------
// Signature verification
// ---------------------------------------------------------------------------

async function importECDSAKey(rawBytes) {
  return crypto.subtle.importKey('jwk', rawKeyToJWK(rawBytes), { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify'])
}

async function verifyECDSA(verifyKey, derSigBytes, messageBytes) {
  return crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, verifyKey, derToRawECDSA(derSigBytes), messageBytes)
}

// ---------------------------------------------------------------------------
// UI helpers
// ---------------------------------------------------------------------------

function setState(title, sub, showSpinner = false) {
  document.getElementById('spinner').style.display = showSpinner ? 'block' : 'none'
  document.getElementById('status-title').textContent = title
  document.getElementById('status-sub').textContent = sub
  document.getElementById('sas-area').style.display = 'none'
}

function showSAS(sas) {
  const digits = document.getElementById('sas-digits')
  digits.innerHTML = sas.split('').map(d => `<div class="sas-digit">${d}</div>`).join('')
  document.getElementById('sas-area').style.display = 'block'
}

function showError(message) {
  document.getElementById('status-panel').innerHTML = `
    <div class="error-card">
      <div class="error-title">⚠ Verification failed</div>
      <div>${escHtml(message)}</div>
    </div>`
}

// ---------------------------------------------------------------------------
// Record rendering
// ---------------------------------------------------------------------------

function renderRecords(data, sessionId) {
  const p = data.patient || {}
  const conditions  = data.conditions  || []
  const medications = data.medications || []
  const allergies   = data.allergies   || []
  const documents   = data.documents   || []

  let html = `
    <div class="records-header">
      <div>
        <div class="patient-name">${escHtml(p.name || 'Unknown Patient')}</div>
        <div class="patient-meta">
          ${p.age_sex   ? `<span>${escHtml(p.age_sex)}</span>`   : ''}
          ${p.blood_type ? `<span>Blood Type: <strong>${escHtml(p.blood_type)}</strong></span>` : ''}
        </div>
      </div>
      <button class="btn-download" id="btn-download" onclick="downloadPDF('${escHtml(sessionId)}')">
        ⬇ Save as PDF
      </button>
    </div>`

  if (conditions.length) {
    html += `<div class="section">
      <div class="section-header">Active Conditions</div>
      <div class="section-body">
        <div class="tag-list">${conditions.map(c => `<span class="tag">${escHtml(c)}</span>`).join('')}</div>
      </div>
    </div>`
  }

  if (medications.length) {
    html += `<div class="section">
      <div class="section-header">Current Medications</div>
      <div class="section-body">
        ${medications.map(m => `
          <div class="med-row">
            <span class="med-name">${escHtml(m.name || '')}</span>
            <span class="med-detail">${escHtml([m.dosage, m.frequency].filter(Boolean).join(' · '))}</span>
          </div>`).join('')}
      </div>
    </div>`
  }

  if (allergies.length) {
    html += `<div class="section">
      <div class="section-header">Allergies</div>
      <div class="section-body">
        <div class="tag-list">${allergies.map(a => `<span class="tag allergy">${escHtml(a)}</span>`).join('')}</div>
      </div>
    </div>`
  }

  if (documents.length) {
    const SKIP_KEYS = new Set(['documentType','providerName','doctorName','documentDate',
                                'patientName','labValues','conditions','medications','clinicalNotes'])
    const docCards = documents.map(doc => {
      let fields = {}
      try { fields = JSON.parse(doc.extracted_fields || '{}') } catch(_) {}

      let fieldRows = ''
      for (const [k, v] of Object.entries(fields)) {
        if (SKIP_KEYS.has(k) || typeof v === 'object' || v === null || String(v).trim() === '') continue
        fieldRows += `<div class="field-row"><span class="field-key">${escHtml(k)}</span><span class="field-val">${escHtml(String(v))}</span></div>`
      }

      let labRows = ''
      for (const [test, val] of Object.entries(fields.labValues || {})) {
        labRows += `<div class="field-row"><span class="field-key">${escHtml(test)}</span><span class="field-val">${escHtml(String(val))}</span></div>`
      }

      const docConds = fields.conditions || []
      const condTags = docConds.length
        ? `<div class="tag-list" style="margin-top:10px">${docConds.map(c => `<span class="tag">${escHtml(c)}</span>`).join('')}</div>`
        : ''

      const medRows = (fields.medications || []).map(m =>
        `<div class="med-row">
          <span class="med-name">${escHtml(m.name || '')}</span>
          <span class="med-detail">${escHtml([m.dosage, m.frequency].filter(Boolean).join(' · '))}</span>
        </div>`
      ).join('')

      const hasBody = fieldRows || labRows || condTags || medRows
      return `<div class="doc-card">
        <div class="doc-card-header">
          <span class="doc-type-badge">${escHtml(doc.type || 'DOC')}</span>
          <span class="doc-provider">${escHtml(doc.provider || doc.title || 'Unknown')}</span>
          ${doc.date ? `<span class="doc-date">${escHtml(doc.date)}</span>` : ''}
        </div>
        ${hasBody ? `<div class="doc-fields">
          ${fieldRows}
          ${labRows ? `<div style="border-top:1px solid #f3f4f6;margin-top:8px;padding-top:8px">${labRows}</div>` : ''}
          ${condTags}
          ${medRows ? `<div style="margin-top:8px">${medRows}</div>` : ''}
        </div>` : ''}
      </div>`
    }).join('')

    html += `<div class="section">
      <div class="section-header">Documents (${documents.length})</div>
      <div class="section-body">${docCards}</div>
    </div>`
  }

  const transferTime = new Date(data.timestamp * 1000).toLocaleString()
  html += `<div class="transfer-footer">
    Transferred via Aegis ASTP v1.1 &middot; ${transferTime} &middot; Session ${escHtml(sessionId.slice(0,8))}&hellip;
  </div>`

  document.getElementById('records').innerHTML = html
  document.getElementById('records').style.display = 'block'
  document.getElementById('status-panel').style.display = 'none'
}

async function downloadPDF(sessionId) {
  const btn = document.getElementById('btn-download')
  btn.disabled = true
  btn.textContent = 'Recording…'
  try {
    await fetch(`/session/${sessionId}/audit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'download_pdf', device_hint: navigator.userAgent.slice(0, 80) }),
    })
  } catch (_) { /* non-fatal */ }
  btn.textContent = '⬇ Save as PDF'
  btn.disabled = false
  window.print()
}

// ---------------------------------------------------------------------------
// Main flow
// ---------------------------------------------------------------------------

async function main() {
  setState('Connecting…', '', true)

  const pathParts = window.location.pathname.split('/')
  const sessionId = pathParts[pathParts.length - 1]

  const fragment = window.location.hash.slice(1)
  if (!fragment) {
    setState('Invalid link', 'No session data found in this URL. Please scan the QR code from the patient\'s Aegis app.')
    return
  }

  let qrData
  try {
    qrData = JSON.parse(dec.decode(b64decode(fragment)))
  } catch {
    showError('Could not parse session data from URL. The QR code may be corrupted.')
    return
  }

  setState('Setting up secure channel…', 'Generating your device\'s cryptographic keypair.', true)

  const docKeypair = await crypto.subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits'])
  const docPubRaw = new Uint8Array(await crypto.subtle.exportKey('raw', docKeypair.publicKey))

  const patientEphBytes = b64decode(qrData.patient_eph_public)
  const patientEphKey = await crypto.subtle.importKey('raw', patientEphBytes, { name: 'ECDH', namedCurve: 'P-256' }, false, [])

  const sharedSecretBuf = await crypto.subtle.deriveBits({ name: 'ECDH', public: patientEphKey }, docKeypair.privateKey, 256)
  const sharedSecretBytes = new Uint8Array(sharedSecretBuf)

  const sas = await deriveSAS(sharedSecretBytes)
  const totpToken = await generateTOTP(qrData.totp_seed)

  setState('Waiting for patient approval…', 'The patient must confirm the verification code on their device.', true)
  showSAS(sas)

  let joinResp
  try {
    joinResp = await fetch(`/session/${sessionId}/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ doc_public_key: b64encode(docPubRaw), totp_token: totpToken }),
    })
  } catch (e) {
    showError('Network error when joining session: ' + e.message)
    return
  }

  if (!joinResp.ok) {
    const err = await joinResp.json().catch(() => ({}))
    if (joinResp.status === 403) {
      showError('TOTP token expired. The QR code is too old — ask the patient to generate a new one.')
    } else if (joinResp.status === 404 || joinResp.status === 410) {
      showError('Session not found or expired. Ask the patient to start a new transfer.')
    } else {
      showError(err.detail || `Server error (${joinResp.status})`)
    }
    return
  }

  const evtSource = new EventSource(`/session/${sessionId}/events?role=doctor`)

  evtSource.addEventListener('payload_ready', async (e) => {
    evtSource.close()
    setState('Verifying records…', 'Checking signatures and decrypting…', true)
    try {
      await verifyAndDecrypt(JSON.parse(e.data), sessionId, qrData, sharedSecretBytes)
    } catch (err) {
      showError(err.message)
    }
  })

  evtSource.addEventListener('session_expired', () => {
    evtSource.close()
    showError('Session expired before the patient approved the transfer.')
  })

  evtSource.onerror = () => {
    evtSource.close()
    showError('Connection to relay was lost. The session may have expired.')
  }
}

// ---------------------------------------------------------------------------
// Verify + decrypt
// ---------------------------------------------------------------------------

async function verifyAndDecrypt(payload, sessionId, qrData, sharedSecretBytes) {
  const patientLongTermBytes = b64decode(payload.patient_long_term_public_key)
  const patientEphBytes      = b64decode(payload.patient_eph_public)
  const ciphertextBytes      = b64decode(payload.ciphertext)
  const ephSigBytes          = b64decode(payload.patient_eph_signature)
  const ctSigBytes           = b64decode(payload.signature)

  const fingerprint = Array.from(
    new Uint8Array(await crypto.subtle.digest('SHA-256', patientLongTermBytes))
  ).map(b => b.toString(16).padStart(2, '0')).join('')

  if (fingerprint !== qrData.patient_key_fingerprint) {
    throw new Error('Patient key fingerprint does not match QR. Possible MITM — abort.')
  }

  const patientLTKey = await importECDSAKey(patientLongTermBytes)

  const ephMsg = concatBytes(patientEphBytes, enc.encode(sessionId))
  if (!await verifyECDSA(patientLTKey, ephSigBytes, ephMsg)) {
    throw new Error('Ephemeral key signature verification failed. The session may be tampered.')
  }

  if (payload.patient_eph_public !== qrData.patient_eph_public) {
    throw new Error('Ephemeral key in payload does not match QR data.')
  }

  const age = Math.abs(Math.floor(Date.now() / 1000) - payload.timestamp)
  if (age > 300) {
    throw new Error(`Payload timestamp is ${age}s old — exceeds 5-minute freshness window.`)
  }

  const ctMsg = concatBytes(ciphertextBytes, enc.encode(sessionId), enc.encode(String(payload.timestamp)))
  if (!await verifyECDSA(patientLTKey, ctSigBytes, ctMsg)) {
    throw new Error('Ciphertext signature verification failed. Data may be tampered.')
  }

  const { aesKey, iv } = await deriveKeyAndIV(sharedSecretBytes, sessionId)

  let plaintext
  try {
    const ptBuf = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, aesKey, ciphertextBytes)
    plaintext = JSON.parse(dec.decode(ptBuf))
  } catch {
    throw new Error('Decryption failed. The AES-GCM authentication tag did not match — data is corrupt or the key is wrong.')
  }

  if (plaintext.magic !== 'AEGIS-TRANSFER-V1') {
    throw new Error('Magic constant missing or wrong in decrypted payload.')
  }

  renderRecords(plaintext, sessionId)
}

// ---------------------------------------------------------------------------
// Boot
// ---------------------------------------------------------------------------

main().catch(err => showError('Unexpected error: ' + err.message))
