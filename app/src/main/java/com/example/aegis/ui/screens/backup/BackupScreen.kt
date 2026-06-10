@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.aegis.ui.screens.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aegis.data.backup.DriveFile
import com.example.aegis.data.backup.DriveBackupService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

@Composable
fun BackupScreen(onBack: () -> Unit = {}) {
    val vm: BackupViewModel = hiltViewModel()
    val uiState by vm.state.collectAsStateWithLifecycle()
    val backups by vm.backups.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var isSignedIn by remember {
        mutableStateOf(GoogleSignIn.getLastSignedInAccount(context) != null)
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching {
                GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
            }.onSuccess {
                isSignedIn = true
                vm.loadBackups()
            }
        }
    }

    LaunchedEffect(isSignedIn) {
        if (isSignedIn) vm.loadBackups()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!isSignedIn) {
                GoogleSignInCard {
                    val client = GoogleSignIn.getClient(
                        context, DriveBackupService.getSignInOptions()
                    )
                    signInLauncher.launch(client.signInIntent)
                }
            } else {
                BackupCard(uiState = uiState, onBackup = vm::backup)
                HorizontalDivider()
                RestoreCard(
                    uiState = uiState,
                    backups = backups,
                    onRestore = vm::restore,
                    onDelete = vm::deleteBackup,
                    onRefresh = vm::loadBackups,
                )
            }

            // Error dialog
            if (uiState is BackupUiState.Error) {
                AlertDialog(
                    onDismissRequest = vm::dismissError,
                    title = { Text("Error") },
                    text = { Text((uiState as BackupUiState.Error).message) },
                    confirmButton = {
                        TextButton(onClick = vm::dismissError) { Text("OK") }
                    },
                )
            }
        }
    }
}

@Composable
private fun GoogleSignInCard(onSignIn: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Sign in with Google", style = MaterialTheme.typography.titleMedium)
            Text(
                "Required to store encrypted backups in your Google Drive app data folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                Text("Sign in with Google")
            }
        }
    }
}

@Composable
private fun BackupCard(uiState: BackupUiState, onBackup: (String) -> Unit) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val isWorking = uiState is BackupUiState.Working
    val isDone = uiState is BackupUiState.Done

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Create Backup", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Your data is encrypted with AES-256-GCM before upload. Remember your passphrase — it cannot be recovered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PassphraseField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = "Backup passphrase",
                showPassword = showPass,
                onToggleVisibility = { showPass = !showPass },
            )
            PassphraseField(
                value = confirm,
                onValueChange = { confirm = it },
                label = "Confirm passphrase",
                showPassword = showPass,
                onToggleVisibility = { showPass = !showPass },
            )
            AnimatedVisibility(isDone) {
                Text(
                    (uiState as? BackupUiState.Done)?.message ?: "",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = { onBackup(passphrase) },
                modifier = Modifier.fillMaxWidth(),
                enabled = passphrase.isNotBlank() && passphrase == confirm && !isWorking,
            ) {
                if (isWorking) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text((uiState as BackupUiState.Working).message)
                } else {
                    Text("Upload Encrypted Backup")
                }
            }
        }
    }
}

@Composable
private fun RestoreCard(
    uiState: BackupUiState,
    backups: List<DriveFile>,
    onRestore: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var selectedFileId by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val isWorking = uiState is BackupUiState.Working

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Restore Backup", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onRefresh, enabled = !isWorking) { Text("Refresh") }
            }

            if (backups.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text("No backups found.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                backups.forEach { file ->
                    BackupFileRow(
                        file = file,
                        isSelected = file.id == selectedFileId,
                        onSelect = { selectedFileId = file.id },
                        onDelete = { confirmDelete = file.id },
                    )
                }
            }

            AnimatedVisibility(selectedFileId.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.height(4.dp))
                    PassphraseField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = "Backup passphrase",
                        showPassword = showPass,
                        onToggleVisibility = { showPass = !showPass },
                    )
                    FilledTonalButton(
                        onClick = { onRestore(selectedFileId, passphrase) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = passphrase.isNotBlank() && !isWorking,
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text((uiState as BackupUiState.Working).message)
                        } else {
                            Text("Restore Selected Backup")
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete backup?") },
            text = { Text("This backup will be permanently deleted from Google Drive.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(confirmDelete!!)
                    if (selectedFileId == confirmDelete) selectedFileId = ""
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BackupFileRow(
    file: DriveFile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateStr = remember(file.createdTime) {
        runCatching {
            val iso = java.time.OffsetDateTime.parse(file.createdTime)
            java.time.format.DateTimeFormatter
                .ofPattern("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                .format(iso)
        }.getOrDefault(file.createdTime)
    }

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSelected) "✓  $dateStr" else dateStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    showPassword: Boolean,
    onToggleVisibility: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showPassword) "Hide" else "Show",
                )
            }
        },
    )
}
