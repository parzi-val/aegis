package com.example.aegis.ui.screens.transfer

import android.graphics.Bitmap
import android.graphics.Color
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aegis.data.astp.AuditLogEntry
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private val tsFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    onBack: () -> Unit = {},
) {
    val vm: TransferViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity

    // Auto-start protocol when screen opens
    LaunchedEffect(Unit) { vm.start() }

    // Handle biometric request — fires each time state becomes AwaitingBiometric
    val awaitingBio = state as? TransferState.AwaitingBiometric
    LaunchedEffect(awaitingBio) {
        if (awaitingBio == null) return@LaunchedEffect
        val executor = Executors.newSingleThreadExecutor()
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val sig = result.cryptoObject?.signature ?: return
                    vm.onBiometricSuccess(sig)
                }
                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    vm.onBiometricError(code, msg)
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(
                if (awaitingBio.purpose == BiometricPurpose.SIGN_EPH_KEY)
                    "Authenticate to share"
                else
                    "Confirm sharing"
            )
            .setSubtitle(
                if (awaitingBio.purpose == BiometricPurpose.SIGN_EPH_KEY)
                    "Verify your identity to start the transfer session"
                else
                    "Confirm you verified the code with the doctor before approving"
            )
            .setNegativeButtonText("Cancel")
            .build()
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(awaitingBio.signature))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure Transfer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                TransferState.Idle,
                is TransferState.AwaitingBiometric,
                TransferState.PreparingSession -> LoadingPanel("Setting up secure channel…")

                is TransferState.ShowingQR -> QRPanel(s)

                is TransferState.ShowingSAS -> SASPanel(
                    sas     = s.sas,
                    onApprove = vm::approve,
                    onDeny    = vm::deny,
                )

                TransferState.Encrypting -> LoadingPanel("Encrypting records…")

                is TransferState.Complete -> CompletePanel(s.auditLog)

                is TransferState.Cancelled -> StatusPanel(
                    icon    = "🚫",
                    title   = "Transfer Cancelled",
                    message = s.reason,
                    onBack  = onBack,
                )

                is TransferState.Error -> StatusPanel(
                    icon    = "⚠",
                    title   = "Transfer Failed",
                    message = s.message,
                    onBack  = onBack,
                )
            }
        }
    }
}

@Composable
private fun LoadingPanel(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QRPanel(state: TransferState.ShowingQR) {
    val qrBitmap = remember(state.url) { generateQR(state.url, 600) }
    val clipboard = LocalClipboardManager.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Scan with Doctor's Device", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Show this QR to the doctor. Do not share it with anyone else.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Box {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Transfer QR code",
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            SmallFloatingActionButton(
                onClick = { clipboard.setText(AnnotatedString(state.url)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy URL", modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Waiting for doctor to scan…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SASPanel(sas: String, onApprove: () -> Unit, onDeny: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Verify Security Code", style = MaterialTheme.typography.titleLarge)
        Text(
            "Ask the doctor to read out their 6-digit code. Confirm it matches before approving.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))

        // Large spaced digit display
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sas.forEach { digit ->
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = digit.toString(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "⚠  If codes don't match, tap Deny — an attacker may be present.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))

        Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) {
            Text("Approve — codes match")
        }
        OutlinedButton(
            onClick = onDeny,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Deny")
        }
    }
}

@Composable
private fun CompletePanel(auditLog: List<AuditLogEntry>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✓", fontSize = 48.sp)
        Spacer(Modifier.height(8.dp))
        Text("Transfer Complete", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Records delivered. Ephemeral keys discarded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        if (auditLog.isNotEmpty()) {
            Text(
                "AUDIT LOG",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(auditLog) { entry ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            tsFmt.format(Date(entry.timestamp * 1000)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp),
                        )
                        Text(
                            entry.action.replace('_', ' '),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        } else {
            Text(
                "Waiting for doctor activity…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPanel(icon: String, title: String, message: String, onBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}

private fun generateQR(content: String, size: Int): Bitmap? = try {
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
    }
    bmp
} catch (_: Exception) { null }
