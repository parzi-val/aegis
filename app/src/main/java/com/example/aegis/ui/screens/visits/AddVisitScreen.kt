package com.example.aegis.ui.screens.visits

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddVisitScreen(
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
) {
    val vm: AddVisitViewModel = hiltViewModel()
    val providerSuggestions by vm.providerSuggestions.collectAsStateWithLifecycle()
    val profileConditions by vm.profileConditionNames.collectAsStateWithLifecycle()
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var conditionDropdownExpanded by remember { mutableStateOf(false) }

    val filteredSuggestions = remember(providerSuggestions, vm.providerName) {
        providerSuggestions.filter { it.contains(vm.providerName, ignoreCase = true) }
    }
    val filteredConditions = remember(profileConditions, vm.conditionTagInput) {
        if (vm.conditionTagInput.isBlank()) emptyList()
        else profileConditions.filter {
            it.contains(vm.conditionTagInput, ignoreCase = true) && it !in vm.conditionTags
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) vm.cameraImageUri?.let { vm.onFilePicked(it) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        uri?.let { vm.onFilePicked(it) }
    }
    val fileLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let { vm.onFilePicked(it) }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = vm.visitDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { vm.visitDate = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Visit") },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Provider with autocomplete
            ExposedDropdownMenuBox(
                expanded = providerDropdownExpanded && filteredSuggestions.isNotEmpty(),
                onExpandedChange = { providerDropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = vm.providerName,
                    onValueChange = { vm.providerName = it; providerDropdownExpanded = true },
                    label = { Text("Provider / Clinic") },
                    trailingIcon = {
                        if (filteredSuggestions.isNotEmpty())
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = providerDropdownExpanded && filteredSuggestions.isNotEmpty()
                            )
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = providerDropdownExpanded && filteredSuggestions.isNotEmpty(),
                    onDismissRequest = { providerDropdownExpanded = false },
                ) {
                    filteredSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = { vm.providerName = suggestion; providerDropdownExpanded = false },
                        )
                    }
                }
            }

            // Date
            OutlinedTextField(
                value = dateFmt.format(Date(vm.visitDate)),
                onValueChange = {},
                label = { Text("Visit Date") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) { Text("Change") }
                },
            )

            // Conditions
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Conditions", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExposedDropdownMenuBox(
                        expanded = conditionDropdownExpanded && filteredConditions.isNotEmpty(),
                        onExpandedChange = { conditionDropdownExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = vm.conditionTagInput,
                            onValueChange = { vm.conditionTagInput = it; conditionDropdownExpanded = true },
                            label = { Text("Add condition") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = conditionDropdownExpanded && filteredConditions.isNotEmpty(),
                            onDismissRequest = { conditionDropdownExpanded = false },
                        ) {
                            filteredConditions.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = { vm.conditionTagInput = name; conditionDropdownExpanded = false },
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { vm.addConditionTag(); conditionDropdownExpanded = false },
                        enabled = vm.conditionTagInput.isNotBlank(),
                    ) { Text("Add") }
                }
                if (vm.conditionTags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.conditionTags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(tag) },
                                trailingIcon = {
                                    IconButton(onClick = { vm.removeConditionTag(tag) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove")
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // Notes
            OutlinedTextField(
                value = vm.notes,
                onValueChange = { vm.notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )

            // Attachments
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Attachments", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(
                    onClick = { showSourceSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add attachment")
                }
                if (vm.pendingFiles.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.pendingFiles.forEach { (uri, name) ->
                            FilterChip(
                                selected = false,
                                onClick = {},
                                label = { Text(name, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = {
                                    IconButton(onClick = { vm.removeFile(uri) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove")
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { vm.saveVisit(onDone = onSaved) },
                enabled = !vm.isSaving && vm.providerName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.isSaving) "Saving…" else "Save Visit") }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSourceSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AttachmentSourceContent(
                onCamera = {
                    showSourceSheet = false
                    cameraLauncher.launch(vm.createCameraImageUri())
                },
                onGallery = {
                    showSourceSheet = false
                    galleryLauncher.launch("image/*")
                },
                onFile = {
                    showSourceSheet = false
                    fileLauncher.launch(arrayOf("*/*"))
                },
            )
        }
    }
}

// ── Attachment source picker ──────────────────────────────────────────────────

@Composable
private fun AttachmentSourceContent(
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
            "Add attachment",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        AttachSourceRow(icon = Icons.Outlined.CameraAlt, label = "Take a photo", onClick = onCamera)
        AttachSourceRow(icon = Icons.Outlined.PhotoLibrary, label = "Choose from gallery", onClick = onGallery)
        AttachSourceRow(icon = Icons.Outlined.FolderOpen, label = "Choose a file", onClick = onFile)
    }
}

@Composable
private fun AttachSourceRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
