package com.example.aegis.data.astp

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-function crypto primitives for ASTP v1.1.
 * All key material is passed in/out as ByteArrays — no Keystore involvement here.
 */
object AstpCrypto {

    // Fixed DER prefix for P-256 SubjectPublicKeyInfo (SPKI).
    // 26 bytes: SEQUENCE(SEQUENCE(OID ecPublicKey, OID prime256v1), BIT STRING header)
    // Append 65-byte uncompressed EC point to get a valid X.509 public key.
    private val P256_SPKI_PREFIX = byteArrayOf(
        0x30, 0x59.toByte(),
        0x30, 0x13,
        0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01,
        0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07,
        0x03, 0x42, 0x00,
    )

    /** Import a 65-byte uncompressed P-256 raw point as a JCE PublicKey. */
    fun importRawP256Key(rawBytes: ByteArray): PublicKey {
        require(rawBytes.size == 65 && rawBytes[0] == 0x04.toByte()) {
            "Expected 65-byte uncompressed P-256 point (0x04 || x || y)"
        }
        return KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(P256_SPKI_PREFIX + rawBytes))
    }

    /**
     * ECDH shared secret: ECDH(localPrivate, peerRawPublic) → 32 bytes (x-coordinate).
     * Both sides (Android + browser WebCrypto) produce identical output.
     */
    fun deriveSharedSecret(localPrivate: PrivateKey, peerRawPublic: ByteArray): ByteArray {
        val peerKey = importRawP256Key(peerRawPublic)
        return KeyAgreement.getInstance("ECDH").run {
            init(localPrivate)
            doPhase(peerKey, true)
            generateSecret()
        }
    }

    /**
     * HKDF-SHA256 (RFC 5869).
     * Parameters must match doctor.html exactly:
     *   salt = UTF-8(session_id), info = UTF-8("aegis-transfer-v1"), length = 44 bytes
     */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // Extract
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        // Expand
        val out = ByteArrayOutputStream()
        var prev = ByteArray(0)
        var counter = 1
        while (out.size() < length) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(prev)
            mac.update(info)
            mac.update(counter.toByte())
            prev = mac.doFinal()
            out.write(prev)
            counter++
        }
        return out.toByteArray().copyOf(length)
    }

    /** Derives 32-byte AES key and 12-byte IV from shared secret via HKDF. */
    fun deriveKeyAndIV(sharedSecret: ByteArray, sessionId: String): Pair<ByteArray, ByteArray> {
        val derived = hkdf(
            ikm = sharedSecret,
            salt = sessionId.toByteArray(),
            info = "aegis-transfer-v1".toByteArray(),
            length = 44,
        )
        return derived.copyOf(32) to derived.copyOfRange(32, 44)
    }

    /** AES-256-GCM encrypt. Returns ciphertext || 16-byte auth tag (Android appends tag). */
    fun aesGcmEncrypt(keyBytes: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plaintext)
    }

    /**
     * SAS derivation: SHA-256(sharedSecret || "aegis-sas-v1"), first uint32 big-endian mod 1,000,000.
     * Must match doctor.html: `new DataView(hash).getUint32(0, false) % 1_000_000`
     */
    fun deriveSAS(sharedSecret: ByteArray): String {
        val input = sharedSecret + "aegis-sas-v1".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(input)
        val uint32 = ((hash[0].toInt() and 0xff) shl 24) or
                     ((hash[1].toInt() and 0xff) shl 16) or
                     ((hash[2].toInt() and 0xff) shl 8)  or
                      (hash[3].toInt() and 0xff)
        return (uint32.toLong() and 0xFFFFFFFFL).rem(1_000_000L).toString().padStart(6, '0')
    }
}

// ── BigInteger → fixed 32-byte big-endian (strips DER leading zero, left-pads if short) ──

internal fun BigInteger.toFixed32Bytes(): ByteArray {
    val bytes = toByteArray()
    return when {
        bytes.size == 32 -> bytes
        bytes.size > 32  -> bytes.copyOfRange(bytes.size - 32, bytes.size)
        else             -> ByteArray(32 - bytes.size) + bytes
    }
}
