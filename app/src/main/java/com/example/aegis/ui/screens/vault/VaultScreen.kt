@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.aegis.ui.screens.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.aegis.data.db.entity.ConditionEntity
import com.example.aegis.data.db.entity.DocumentEntity
import com.example.aegis.data.model.DocumentType
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VaultScreen(onNavigateToDetail: (Long) -> Unit = {}) {
    val vm: VaultViewModel = hiltViewModel()
    val docs by vm.documents.collectAsStateWithLifecycle()
    val activeFilter by vm.activeFilter.collectAsStateWithLifecycle()
    val conditions by vm.conditions.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val isFabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) vm.cameraImageUri?.let { vm.onFilePicked(it) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri?.let { vm.onFilePicked(it) }
    }
    val fileLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let { vm.onFilePicked(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Upload") },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                expanded = isFabExpanded,
                onClick = { vm.showSourceSheet = true },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FilterRow(activeFilter = activeFilter, onSelect = { vm.setFilter(it) })
            if (docs.isEmpty()) {
                EmptyVaultState(onUpload = { vm.showSourceSheet = true })
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(docs, key = { it.id }) { doc ->
                        DocumentCard(doc = doc, onClick = { onNavigateToDetail(doc.id) })
                    }
                }
            }
        }
    }

    if (vm.showSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.showSourceSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            UploadSourceContent(
                onCamera = {
                    vm.showSourceSheet = false
                    cameraLauncher.launch(vm.createCameraImageUri())
                },
                onGallery = { vm.showSourceSheet = false; galleryLauncher.launch("image/*") },
                onFile = { vm.showSourceSheet = false; fileLauncher.launch(arrayOf("*/*")) },
            )
        }
    }

    if (vm.pendingUri != null) {
        ModalBottomSheet(
            onDismissRequest = { vm.dismissTagSheet() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            TagDocumentContent(vm = vm, conditions = conditions)
        }
    }
}

// ── Filter row ────────────────────────────────────────────────────────────────

@Composable
private fun FilterRow(activeFilter: DocumentType?, onSelect: (DocumentType?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = activeFilter == null,
            onClick = { onSelect(null) },
            label = { Text("All") },
        )
        DocumentType.entries.filter { it != DocumentType.UNKNOWN }.forEach { type ->
            FilterChip(
                selected = activeFilter == type,
                onClick = { onSelect(type) },
                label = { Text(type.displayName) },
            )
        }
    }
}

// ── Document card ─────────────────────────────────────────────────────────────

@Composable
private fun DocumentCard(doc: DocumentEntity, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentThumbnail(doc)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = doc.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (doc.extractionState == "PENDING") {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DocTypeBadge(doc.documentType)
                    if (doc.providerName.isNotBlank()) {
                        Text(
                            text = "· ${doc.providerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = (doc.documentDate ?: doc.uploadedAt).toDisplayDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DocumentThumbnail(doc: DocumentEntity) {
    if (doc.mimeType.startsWith("image/")) {
        AsyncImage(
            model = File(doc.localPath),
            contentDescription = null,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = typeContainerColor(doc.documentType),
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = typeIcon(doc.documentType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun DocTypeBadge(typeName: String) {
    val type = DocumentType.entries.find { it.name == typeName } ?: DocumentType.UNKNOWN
    Surface(shape = RoundedCornerShape(4.dp), color = typeContainerColor(typeName)) {
        Text(
            text = type.displayName,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun typeContainerColor(typeName: String) = when (typeName) {
    DocumentType.PRESCRIPTION.name -> MaterialTheme.colorScheme.primaryContainer
    DocumentType.LAB_REPORT.name -> MaterialTheme.colorScheme.tertiaryContainer
    DocumentType.DISCHARGE_SUMMARY.name -> MaterialTheme.colorScheme.secondaryContainer
    DocumentType.SCAN.name -> MaterialTheme.colorScheme.tertiaryContainer
    DocumentType.INSURANCE.name -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHighest
}

private fun typeIcon(typeName: String): ImageVector = when (typeName) {
    DocumentType.PRESCRIPTION.name -> Icons.Outlined.Description
    DocumentType.LAB_REPORT.name -> Icons.Outlined.Science
    DocumentType.DISCHARGE_SUMMARY.name -> Icons.Outlined.LocalHospital
    DocumentType.SCAN.name -> Icons.Outlined.Image
    DocumentType.INSURANCE.name -> Icons.Outlined.Shield
    else -> Icons.Outlined.InsertDriveFile
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyVaultState(onUpload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("No documents yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Upload a prescription, lab report, scan, or any health document.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUpload) { Text("Upload your first document") }
    }
}

// ── Upload source picker ──────────────────────────────────────────────────────

@Composable
private fun UploadSourceContent(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Text(
            "Upload document",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        SourceRow(icon = Icons.Outlined.CameraAlt, label = "Take a photo", onClick = onCamera)
        SourceRow(icon = Icons.Outlined.PhotoLibrary, label = "Choose from gallery", onClick = onGallery)
        SourceRow(icon = Icons.Outlined.FolderOpen, label = "Choose a file", onClick = onFile)
    }
}

@Composable
private fun SourceRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ── Tag document sheet ────────────────────────────────────────────────────────

@Composable
private fun TagDocumentContent(vm: VaultViewModel, conditions: List<ConditionEntity>) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = vm.tagDate)

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.tagDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = datePickerState) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Tag document", style = MaterialTheme.typography.titleMedium)

        // Filename display
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = vm.pendingFileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Document type
        TagDropdown(
            label = "Document type",
            selected = vm.tagDocType.displayName,
            options = DocumentType.entries.map { it.displayName },
            onSelect = { name -> vm.tagDocType = DocumentType.entries.first { it.displayName == name } },
        )

        // Provider
        OutlinedTextField(
            value = vm.tagProvider,
            onValueChange = { vm.tagProvider = it },
            label = { Text("Provider / clinic (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardOptions.Default.capitalization),
        )

        // Condition link
        if (conditions.isNotEmpty()) {
            val options = listOf("None") + conditions.map { it.name }
            val selectedName = conditions.indexOfFirst { it.id == vm.tagConditionId }
                .takeIf { it >= 0 }?.let { conditions[it].name } ?: "None"
            TagDropdown(
                label = "Linked condition (optional)",
                selected = selectedName,
                options = options,
                onSelect = { name ->
                    vm.tagConditionId = conditions.find { it.name == name }?.id
                },
            )
        }

        // Date — Box overlay makes the disabled field clickable
        Box {
            OutlinedTextField(
                value = vm.tagDate?.toDisplayDate() ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Document date (optional)") },
                placeholder = { Text("Tap to select") },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
        }

        // Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { vm.dismissTagSheet() }, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = { vm.saveDocument() },
                modifier = Modifier.weight(1f),
                enabled = !vm.isSaving,
            ) {
                if (vm.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Save")
            }
        }
    }
}

@Composable
private fun TagDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

// ── Date helper ───────────────────────────────────────────────────────────────

private val displayDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun Long.toDisplayDate(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(displayDateFmt)
