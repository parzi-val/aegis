package com.example.aegis.ui.screens.model

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ModelSetupScreen(onReady: () -> Unit) {
    val vm: ModelSetupViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Request POST_NOTIFICATIONS on Android 13+, then start download regardless of grant result
    val notifLauncher = rememberLauncherForActivityResult(RequestPermission()) {
        vm.startDownload()
    }
    fun requestDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startDownload()
        }
    }

    LaunchedEffect(state) {
        if (state is ModelSetupViewModel.State.Done) onReady()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.HealthAndSafety,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text("Aegis", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(40.dp))

            when (val s = state) {
                ModelSetupViewModel.State.Done -> Unit

                ModelSetupViewModel.State.NotFound -> {
                    Text("AI model required", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The on-device AI model (${com.example.aegis.data.ml.GemmaInferenceHolder.MODEL_FILE_NAME}) " +
                        "wasn't found on this device. Download it now via your relay server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { requestDownload() }) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Download model")
                    }
                }

                is ModelSetupViewModel.State.Downloading -> {
                    Text("Downloading AI model…", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    if (s.total > 0) {
                        LinearProgressIndicator(
                            progress = { s.received.toFloat() / s.total.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${s.received.toMb()} / ${s.total.toMb()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Connecting…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is ModelSetupViewModel.State.Error -> {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { vm.retry() }) { Text("Retry") }
                }
            }
        }
    }
}

private fun Long.toMb() = "%.1f MB".format(this / 1_048_576.0)
