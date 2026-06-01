@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package com.example.aegis.ui.screens.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.json.JSONObject
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.aegis.data.db.entity.DocumentEntity
import com.example.aegis.data.db.entity.ShareAuditEntity
import com.example.aegis.data.model.DocumentType
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DocumentDetailScreen(onBack: () -> Unit) {
    val vm: DocumentDetailViewModel = hiltViewModel()
    val doc by vm.document.collectAsStateWithLifecycle()
    val conditionName by vm.conditionName.collectAsStateWithLifecycle()
    val pipelineLogs by vm.pipelineLogs.collectAsStateWithLifecycle()
    val shareAudits by vm.shareAudits.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = doc?.fileName ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (doc != null) {
                        IconButton(onClick = { vm.showDeleteDialog = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (doc == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            DetailContent(
                doc = doc!!,
                conditionName = conditionName,
                pipelineLogs = pipelineLogs,
                shareAudits = shareAudits,
                onSaveAuditNote = vm::saveAuditNote,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (vm.showDeleteDialog) {
        doc?.let { d ->
            AlertDialog(
                onDismissRequest = { vm.showDeleteDialog = false },
                title = { Text("Delete document?") },
                text = {
                    Text("\"${d.fileName}\" will be permanently removed from your vault.")
                },
                confirmButton = {
                    TextButton(onClick = { vm.deleteDocument(d, onBack) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.showDeleteDialog = false }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun DetailContent(
    doc: DocumentEntity,
    conditionName: String?,
    pipelineLogs: List<String>,
    shareAudits: List<ShareAuditEntity>,
    onSaveAuditNote: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Preview area
        if (doc.mimeType.startsWith("image/")) {
            AsyncImage(
                model = File(doc.localPath),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(260.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Surface(
                color = detailTypeColor(doc.documentType),
                modifier = Modifier.fillMaxWidth().height(160.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = detailTypeIcon(doc.documentType),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        val type = DocumentType.entries.find { it.name == doc.documentType }
                            ?: DocumentType.UNKNOWN
                        Text(
                            text = type.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetaRow("File name", doc.fileName)

            val type = DocumentType.entries.find { it.name == doc.documentType } ?: DocumentType.UNKNOWN
            MetaRow("Type", type.displayName)

            if (doc.providerName.isNotBlank()) MetaRow("Provider", doc.providerName)
            if (conditionName != null) MetaRow("Condition", conditionName)

            if (doc.documentDate != null) {
                MetaRow("Document date", doc.documentDate.toDocDate())
            }
            MetaRow("Uploaded", doc.uploadedAt.toUploadDate())

            if (doc.tags.isNotBlank()) MetaRow("Tags", doc.tags)

            if (doc.extractionState == "DONE" && doc.extractedFields != null) {
                ExtractionBlock(doc.extractedFields)
            }
            if (shareAudits.isNotEmpty()) ShareHistoryPanel(shareAudits, onSaveAuditNote)
            if (pipelineLogs.isNotEmpty()) PipelineLogsPanel(pipelineLogs)
        }
    }
}

@Composable
private fun ExtractionBlock(json: String) {
    val data = remember(json) { parseExtractedData(json) }
    val hasContent = data.summary.isNotBlank() || data.doctorName.isNotBlank() ||
        data.patientName.isNotBlank() || data.conditions.isNotEmpty() ||
        data.labValues.isNotEmpty() || data.medications.isNotEmpty()
    if (!hasContent) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Extracted data",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (data.summary.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = data.summary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        if (data.doctorName.isNotBlank()) MetaRow("Doctor", data.doctorName)
        if (data.patientName.isNotBlank()) MetaRow("Patient", data.patientName)

        if (data.conditions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                data.conditions.forEach { condition ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = condition,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        if (data.labValues.isNotEmpty()) {
            ExtractionSection("Lab Results") {
                data.labValues.forEachIndexed { i, (test, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = test,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (i < data.labValues.size - 1) HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                }
            }
        }

        if (data.medications.isNotEmpty()) {
            ExtractionSection("Medications") {
                data.medications.forEachIndexed { i, med ->
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(med.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        val detail = listOf(med.dosage, med.frequency).filter { it.isNotBlank() }.joinToString(" · ")
                        if (detail.isNotBlank()) {
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (i < data.medications.size - 1) HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                }
            }
        }
    }
}

@Composable
private fun ExtractionSection(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.35f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.65f),
            )
        }
    }
}

// ── Share history panel ───────────────────────────────────────────────────────

@Composable
private fun ShareHistoryPanel(
    audits: List<ShareAuditEntity>,
    onSaveNote: (Long, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Share history (${audits.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                HorizontalDivider()
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    audits.forEach { entry ->
                        ShareAuditEntryRow(entry = entry, onSaveNote = onSaveNote)
                        if (entry != audits.last()) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareAuditEntryRow(
    entry: ShareAuditEntity,
    onSaveNote: (Long, String) -> Unit,
) {
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.userNote) }
    var isDirty by remember(entry.id) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.action.replace('_', ' ').replaceFirstChar { it.uppercaseChar() },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = entry.timestamp.toShareDate(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.deviceHint.isNotBlank()) {
            Text(
                text = entry.deviceHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it; isDirty = true },
                placeholder = {
                    Text("Add a note…", style = MaterialTheme.typography.bodySmall)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (isDirty) { onSaveNote(entry.id, note); isDirty = false }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            if (isDirty) {
                IconButton(onClick = { onSaveNote(entry.id, note); isDirty = false }) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Save note",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// ── Pipeline debug panel ──────────────────────────────────────────────────────

@Composable
private fun PipelineLogsPanel(logs: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pipeline logs (${logs.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(logs.joinToString("\n"))) },
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy logs",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
            if (expanded) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    logs.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// ── Extracted data model ──────────────────────────────────────────────────────

private data class ExtractedData(
    val summary: String = "",
    val doctorName: String = "",
    val patientName: String = "",
    val conditions: List<String> = emptyList(),
    val labValues: List<Pair<String, String>> = emptyList(),
    val medications: List<MedEntry> = emptyList(),
)

private data class MedEntry(val name: String, val dosage: String, val frequency: String)

private fun parseExtractedData(json: String): ExtractedData = try {
    val obj = JSONObject(json)
    val conditions = buildList {
        val arr = obj.optJSONArray("conditions") ?: return@buildList
        for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
    }
    val labValues = buildList {
        val lv = obj.optJSONObject("labValues") ?: return@buildList
        lv.keys().forEach { key -> add(key to lv.optString(key, "")) }
    }
    val medications = buildList {
        val arr = obj.optJSONArray("medications") ?: return@buildList
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val name = m.optString("name", "")
            if (name.isNotBlank()) add(MedEntry(name, m.optString("dosage", ""), m.optString("frequency", "")))
        }
    }
    ExtractedData(
        summary = obj.optString("summary", ""),
        doctorName = obj.optString("doctorName", ""),
        patientName = obj.optString("patientName", ""),
        conditions = conditions,
        labValues = labValues,
        medications = medications,
    )
} catch (e: Exception) {
    ExtractedData()
}

// ── Type helpers (local to this file) ────────────────────────────────────────

@Composable
private fun detailTypeColor(typeName: String): Color = when (typeName) {
    DocumentType.PRESCRIPTION.name -> MaterialTheme.colorScheme.primaryContainer
    DocumentType.LAB_REPORT.name -> MaterialTheme.colorScheme.tertiaryContainer
    DocumentType.DISCHARGE_SUMMARY.name -> MaterialTheme.colorScheme.secondaryContainer
    DocumentType.SCAN.name -> MaterialTheme.colorScheme.tertiaryContainer
    DocumentType.INSURANCE.name -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHighest
}

private fun detailTypeIcon(typeName: String): ImageVector = when (typeName) {
    DocumentType.PRESCRIPTION.name -> Icons.Outlined.Description
    DocumentType.LAB_REPORT.name -> Icons.Outlined.Science
    DocumentType.DISCHARGE_SUMMARY.name -> Icons.Outlined.LocalHospital
    DocumentType.SCAN.name -> Icons.Outlined.Image
    DocumentType.INSURANCE.name -> Icons.Outlined.Shield
    else -> Icons.Outlined.InsertDriveFile
}

// ── Date helpers ──────────────────────────────────────────────────────────────

private val docDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
private val uploadDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

private val shareDateFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a")

private fun Long.toShareDate(): String =
    Instant.ofEpochSecond(this).atZone(ZoneId.systemDefault()).format(shareDateFmt)

private fun Long.toDocDate(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(docDateFmt)

private fun Long.toUploadDate(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(uploadDateFmt)
