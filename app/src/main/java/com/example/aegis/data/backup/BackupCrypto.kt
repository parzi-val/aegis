package com.example.aegis.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {

    private const val ITERATIONS = 310_000
    private const val KEY_LEN = 256
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128

    fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LEN)
        val raw = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }

    /** Returns [16-byte salt][12-byte IV][ciphertext+GCM tag]. */
    fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return salt + iv + ciphertext
    }

    /** Expects blob in [16-byte salt][12-byte IV][ciphertext+GCM tag] format. */
    fun decrypt(blob: ByteArray, passphrase: String): ByteArray {
        val salt = blob.copyOfRange(0, SALT_LEN)
        val iv = blob.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
        val ciphertext = blob.copyOfRange(SALT_LEN + IV_LEN, blob.size)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
