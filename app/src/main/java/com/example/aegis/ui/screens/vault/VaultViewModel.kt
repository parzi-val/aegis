package com.example.aegis.ui.screens.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.example.aegis.data.db.entity.DocumentEntity
import com.example.aegis.data.model.DocumentType
import com.example.aegis.data.repository.DocumentRepository
import com.example.aegis.data.repository.HealthProfileRepository
import com.example.aegis.data.worker.ExtractionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    healthProfileRepository: HealthProfileRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // ── Filter ────────────────────────────────────────────────────────────────
    private val _activeFilter = MutableStateFlow<DocumentType?>(null)
    val activeFilter: StateFlow<DocumentType?> = _activeFilter

    fun setFilter(type: DocumentType?) { _activeFilter.value = type }

    val documents: StateFlow<List<DocumentEntity>> = _activeFilter
        .flatMapLatest { filter ->
            if (filter == null) documentRepository.allDocuments
            else documentRepository.getByType(filter)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conditions = healthProfileRepository.activeConditions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Re-enqueue any docs left PENDING from a previous session that may never have been queued.
    // WorkManager will skip any that already have a pending/running worker.
    init {
        val wm = WorkManager.getInstance(context)
        viewModelScope.launch(Dispatchers.IO) {
            documentRepository.allDocuments.first()
                .filter { it.extractionState == "PENDING" }
                .forEach { doc -> ExtractionWorker.enqueue(wm, doc.id) }
        }
    }

    // ── Upload sheet state ────────────────────────────────────────────────────
    var showSourceSheet by mutableStateOf(false)

    var cameraImageUri by mutableStateOf<Uri?>(null)
        private set

    // ── Pending tag sheet state ───────────────────────────────────────────────
    var pendingUri by mutableStateOf<Uri?>(null)
        private set
    var pendingFileName by mutableStateOf("")
        private set
    var pendingMimeType by mutableStateOf("")
        private set

    var tagDocType by mutableStateOf(DocumentType.UNKNOWN)
    var tagProvider by mutableStateOf("")
    var tagConditionId by mutableStateOf<Long?>(null)
    var tagDate by mutableStateOf<Long?>(null)

    var isSaving by mutableStateOf(false)
        private set

    // ── Actions ───────────────────────────────────────────────────────────────

    fun createCameraImageUri(): Uri {
        val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            .also { cameraImageUri = it }
    }

    // Single file (camera or gallery/file with exactly one selection) → show tag sheet.
    fun onFilePicked(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val (name, mime) = getFileInfo(uri)
            withContext(Dispatchers.Main) {
                pendingUri = uri
                pendingFileName = name
                pendingMimeType = mime
                tagDocType = DocumentType.UNKNOWN
                tagProvider = ""
                tagConditionId = null
                tagDate = null
                showSourceSheet = false
            }
        }
    }

    // Multi-file selection: skip tag sheet entirely, let Gemma infer metadata.
    // Single-item list still routes through the tag sheet for manual tagging.
    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) { onFilePicked(uris.first()); return }
        saveBatch(uris)
    }

    private fun saveBatch(uris: List<Uri>) {
        viewModelScope.launch {
            isSaving = true
            showSourceSheet = false
            try {
                val wm = WorkManager.getInstance(context)
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        val (name, mime) = getFileInfo(uri)
                        val localPath = copyToPrivateStorage(uri, name)
                        val docId = documentRepository.addDocument(
                            DocumentEntity(
                                fileName = name,
                                mimeType = mime,
                                localPath = localPath,
                                extractionState = "PENDING",
                            )
                        )
                        ExtractionWorker.enqueue(wm, docId)
                    }
                }
            } finally {
                isSaving = false
            }
        }
    }

    fun dismissTagSheet() {
        pendingUri = null
    }

    fun saveDocument() {
        val uri = pendingUri ?: return
        val mimeType = pendingMimeType   // capture before sheet clears
        viewModelScope.launch {
            isSaving = true
            try {
                val localPath = withContext(Dispatchers.IO) {
                    copyToPrivateStorage(uri, pendingFileName)
                }
                val docId = documentRepository.addDocument(
                    DocumentEntity(
                        fileName = pendingFileName,
                        documentType = tagDocType.name,
                        mimeType = mimeType,
                        localPath = localPath,
                        documentDate = tagDate,
                        conditionId = tagConditionId,
                        providerName = tagProvider.trim(),
                        extractionState = "PENDING",
                    ),
                )
                pendingUri = null   // dismiss sheet immediately
                ExtractionWorker.enqueue(WorkManager.getInstance(context), docId)
            } finally {
                isSaving = false
            }
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            documentRepository.deleteDocument(doc)
            File(doc.localPath).delete()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun getFileInfo(uri: Uri): Pair<String, String> {
        var name = "document_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col >= 0) cursor.getString(col)?.let { name = it }
            }
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        return Pair(name, mime)
    }

    private fun copyToPrivateStorage(uri: Uri, fileName: String): String {
        val docsDir = File(context.filesDir, "documents").also { it.mkdirs() }
        val dest = File(docsDir, "${System.currentTimeMillis()}_$fileName")
        context.contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
        return dest.absolutePath
    }
}
