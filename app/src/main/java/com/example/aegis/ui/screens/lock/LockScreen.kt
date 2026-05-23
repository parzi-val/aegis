package com.example.aegis.ui.screens.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LockScreen(onAuthenticated: () -> Unit) {
    val viewModel: LockViewModel = hiltViewModel()
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val currentOnAuthenticated by rememberUpdatedState(onAuthenticated)

    var authError by remember { mutableStateOf<String?>(null) }
    var showUnlockButton by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val callback = remember {
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject?.cipher ?: return
                viewModel.onAuthSuccess(cipher)
                currentOnAuthenticated()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    // User dismissed the prompt — show the button so they can re-open it
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> showUnlockButton = true
                    else -> authError = errString.toString()
                }
            }
            override fun onAuthenticationFailed() { /* unrecognized biometric — prompt persists */ }
        }
    }

    val biometricPrompt = remember {
        BiometricPrompt(activity, ContextCompat.getMainExecutor(context), callback)
    }

    // BIOMETRIC_STRONG covers fingerprint + any Class 3 face unlock.
    // DEVICE_CREDENTIAL provides PIN / pattern / password fallback.
    // The two cannot be combined with setNegativeButtonText — the system provides its own fallback UI.
    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Aegis")
            .setSubtitle("Verify your identity to access your health vault")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
    }

    LaunchedEffect(retryTrigger) {
        authError = null
        showUnlockButton = false
        try {
            val cipher = withContext(Dispatchers.IO) { viewModel.prepareUnlock() }
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Exception) {
            authError = "Could not initialise security key: ${e.message}"
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.HealthAndSafety,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Aegis", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "Your health, your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(40.dp))
            when {
                authError != null -> {
                    Text(
                        text = authError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { retryTrigger++ }) { Text("Retry") }
                }
                showUnlockButton -> {
                    Button(onClick = { retryTrigger++ }) { Text("Unlock") }
                }
                else -> {
                    Text(
                        text = "Waiting for authentication…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
