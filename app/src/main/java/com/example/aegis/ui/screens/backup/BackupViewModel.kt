package com.example.aegis.ui.screens.backup

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aegis.data.backup.BackupManager
import com.example.aegis.data.backup.DriveBackupService
import com.example.aegis.data.backup.DriveFile
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class BackupUiState {
    data object Idle : BackupUiState()
    data class Working(val message: String) : BackupUiState()
    data class Done(val message: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val driveService: DriveBackupService,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state

    private val _backups = MutableStateFlow<List<DriveFile>>(emptyList())
    val backups: StateFlow<List<DriveFile>> = _backups

    private val driveScope = "oauth2:https://www.googleapis.com/auth/drive.appdata"

    fun backup(passphrase: String) {
        viewModelScope.launch {
            _state.value = BackupUiState.Working("Creating backup…")
            runCatching {
                val token = getAccessToken() ?: error("Not signed in to Google")
                _state.value = BackupUiState.Working("Encrypting data…")
                val blob = backupManager.createBackupBlob(passphrase)
                _state.value = BackupUiState.Working("Uploading to Drive…")
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                driveService.upload(token, blob, "aegis_backup_$ts.enc")
            }.onSuccess {
                _state.value = BackupUiState.Done("Backup uploaded successfully.")
            }.onFailure { e ->
                _state.value = BackupUiState.Error(e.message ?: "Backup failed")
            }
        }
    }

    fun loadBackups() {
        viewModelScope.launch {
            _state.value = BackupUiState.Working("Loading backups…")
            runCatching {
                val token = getAccessToken() ?: error("Not signed in to Google")
                driveService.listBackups(token)
            }.onSuccess { list ->
                _backups.value = list
                _state.value = BackupUiState.Idle
            }.onFailure { e ->
                _state.value = BackupUiState.Error(e.message ?: "Failed to load backups")
            }
        }
    }

    fun restore(fileId: String, passphrase: String) {
        viewModelScope.launch {
            _state.value = BackupUiState.Working("Downloading backup…")
            runCatching {
                val token = getAccessToken() ?: error("Not signed in to Google")
                val blob = driveService.download(token, fileId)
                _state.value = BackupUiState.Working("Decrypting and restoring…")
                backupManager.restoreFromBlob(blob, passphrase)
            }.onSuccess {
                _state.value = BackupUiState.Done("Restore complete. Restarting…")
                restartApp()
            }.onFailure { e ->
                _state.value = BackupUiState.Error(e.message ?: "Restore failed")
            }
        }
    }

    fun deleteBackup(fileId: String) {
        viewModelScope.launch {
            runCatching {
                val token = getAccessToken() ?: error("Not signed in to Google")
                driveService.deleteBackup(token, fileId)
            }.onSuccess {
                loadBackups()
            }.onFailure { e ->
                _state.value = BackupUiState.Error(e.message ?: "Delete failed")
            }
        }
    }

    fun dismissError() { _state.value = BackupUiState.Idle }

    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
        runCatching {
            GoogleAuthUtil.getToken(context, account.account!!, driveScope)
        }.getOrNull()
    }

    private fun restartApp() {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
