package com.example.aegis.data.astp

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages two distinct key types per ASTP key separation principle:
 *
 *   Long-term signing key (TEE, non-exportable) — ECDSA P-256, biometric-gated per use.
 *     Used only to sign ephemeral ECDH public keys and ciphertext. Never for key agreement.
 *
 *   Ephemeral ECDH keypair (in-memory, per-session) — generated fresh each transfer, discarded after.
 *     Used only for key agreement. Never for signing.
 */
@Singleton
class AstpKeyManager @Inject constructor() {

    // ── Long-term signing key ────────────────────────────────────────────────

    fun ensureSigningKeyExists() {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (ks.containsAlias(SIGNING_ALIAS)) return

        val spec = KeyGenParameterSpec.Builder(
            SIGNING_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Per-use auth (duration = 0 → each use requires strong biometric)
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
                // Below API 30: setUserAuthenticationRequired(true) alone = per-use with any biometric
            }
            .build()

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
            .apply { initialize(spec) }
            .generateKeyPair()
    }

    /** Raw 65-byte uncompressed P-256 point of the long-term signing key. */
    fun getSigningPublicKeyRaw(): ByteArray {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val cert = ks.getCertificate(SIGNING_ALIAS) ?: error("Signing key not found — call ensureSigningKeyExists() first")
        val ecKey = cert.publicKey as ECPublicKey
        return byteArrayOf(0x04) +
            ecKey.w.affineX.toFixed32Bytes() +
            ecKey.w.affineY.toFixed32Bytes()
    }

    /** Hex SHA-256 fingerprint of the long-term signing public key (for QR + relay verification). */
    fun getSigningKeyFingerprint(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(getSigningPublicKeyRaw())
            .joinToString("") { "%02x".format(it) }

    /**
     * Returns a [Signature] initialised with the signing key private key.
     * Wrap in [android.hardware.biometrics.BiometricPrompt.CryptoObject] and pass to BiometricPrompt.
     * The authenticated Signature can sign ONE message.
     */
    fun prepareSignature(): Signature {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val privateKey = ks.getKey(SIGNING_ALIAS, null) as PrivateKey
        return Signature.getInstance("SHA256withECDSA").apply { initSign(privateKey) }
    }

    // ── Ephemeral ECDH keypair ────────────────────────────────────────────────

    /**
     * Generates a fresh in-memory P-256 ECDH keypair.
     * The private key stays in memory for this session only; discard after payload is sent.
     */
    fun generateEphemeralKeyPair(): java.security.KeyPair =
        KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val SIGNING_ALIAS = "aegis_signing_key"
    }
}
