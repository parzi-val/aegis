package com.example.aegis.data.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the Android Keystore AES-GCM key and the in-memory DB passphrase.
 *
 * Flow:
 *  First run  — generateKeyIfNeeded() → prepareCipherForFirstRun() → BiometricPrompt →
 *               onFirstRunAuthSuccess(cipher) [generates random passphrase, encrypts+stores it]
 *  Later runs — generateKeyIfNeeded() → prepareCipherForUnlock() → BiometricPrompt →
 *               onUnlockAuthSuccess(cipher) [decrypts stored passphrase]
 *
 * The plaintext passphrase only lives in memory; it is never written to disk.
 */
@Singleton
class CryptoManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var passphrase: ByteArray? = null

    val isUnlocked: Boolean get() = passphrase != null

    fun getPassphrase(): ByteArray =
        passphrase ?: error("DB passphrase requested before biometric authentication")

    fun generateKeyIfNeeded() {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (ks.containsAlias(KEY_ALIAS)) return
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // API 30+: allow both strong biometric and device credential (PIN/pattern)
                    setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                } else {
                    setUserAuthenticationRequired(true)
                }
            }
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    fun isFirstRun(): Boolean =
        !context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(PREF_ENC_PASSPHRASE)

    fun prepareCipherForFirstRun(): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, loadKey()) }

    fun prepareCipherForUnlock(): Cipher {
        val iv = loadIv() ?: error("No IV found — call prepareCipherForFirstRun on first run")
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, loadKey(), GCMParameterSpec(128, iv))
        }
    }

    /** Called after BiometricPrompt succeeds on first run. Generates + stores the DB passphrase. */
    fun onFirstRunAuthSuccess(cipher: Cipher) {
        val plain = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encrypted = cipher.doFinal(plain)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(PREF_ENC_PASSPHRASE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
        passphrase = plain
    }

    /** Called after BiometricPrompt succeeds on subsequent runs. Decrypts the stored passphrase. */
    fun onUnlockAuthSuccess(cipher: Cipher) {
        val encrypted = Base64.decode(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_ENC_PASSPHRASE, "")!!,
            Base64.NO_WRAP,
        )
        passphrase = cipher.doFinal(encrypted)
    }

    fun clearPassphrase() {
        passphrase?.fill(0)
        passphrase = null
    }

    private fun loadKey(): SecretKey =
        KeyStore.getInstance(KEYSTORE).run {
            load(null)
            getKey(KEY_ALIAS, null) as SecretKey
        }

    private fun loadIv(): ByteArray? {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_IV, null) ?: return null
        return Base64.decode(s, Base64.NO_WRAP)
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "aegis_db_key"
        private const val PREFS_NAME = "aegis_crypto"
        private const val PREF_ENC_PASSPHRASE = "enc_passphrase"
        private const val PREF_IV = "enc_iv"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
