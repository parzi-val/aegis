package com.example.aegis.ui.screens.lock

import androidx.lifecycle.ViewModel
import com.example.aegis.data.crypto.CryptoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val cryptoManager: CryptoManager,
) : ViewModel() {

    // Captured once so prepareUnlock() and onAuthSuccess() use the same mode.
    private val firstRun = cryptoManager.isFirstRun()

    /** Generates the Keystore key if absent and returns an auth-ready Cipher. Run on IO. */
    fun prepareUnlock(): Cipher {
        cryptoManager.generateKeyIfNeeded()
        return if (firstRun) cryptoManager.prepareCipherForFirstRun()
               else cryptoManager.prepareCipherForUnlock()
    }

    /** Called from BiometricPrompt.AuthenticationCallback.onAuthenticationSucceeded. */
    fun onAuthSuccess(cipher: Cipher) {
        if (firstRun) cryptoManager.onFirstRunAuthSuccess(cipher)
        else cryptoManager.onUnlockAuthSuccess(cipher)
    }
}
