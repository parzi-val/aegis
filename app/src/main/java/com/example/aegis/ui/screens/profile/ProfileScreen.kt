@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.aegis.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aegis.data.db.entity.AllergyEntity
import com.example.aegis.data.db.entity.ConditionEntity
import com.example.aegis.data.db.entity.MedicationEntity
import com.example.aegis.data.suggestions.Suggestions

private val bloodTypes = listOf("Unknown", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
private val genders = listOf("", "Male", "Female", "Non-binary", "Prefer not to say")
private val severities = listOf("Unknown", "Mild", "Moderate", "Severe", "Life-threatening")

@Composable
fun ProfileScreen(onBack: () -> Unit = {}, onNavigateToBackup: () -> Unit = {}) {
    val vm: ProfileViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            ProfileViewModel.State.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ProfileViewModel.State.Loaded -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    PersonalInfoSection(vm)
                    HorizontalDivider()
                    ConditionsSection(vm, s.data.conditions)
                    HorizontalDivider()
                    MedicationsSection(vm, s.data.medications)
                    HorizontalDivider()
                    AllergiesSection(vm, s.data.allergies)
                    HorizontalDivider()
                    BackupRestoreRow(onNavigateToBackup)
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

// ── Personal Info ─────────────────────────────────────────────────────────────

@Composable
private fun PersonalInfoSection(vm: ProfileViewModel) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Personal Info")
        OutlinedTextField(
            value = vm.name,
            onValueChange = { vm.name = it },
            label = { Text("Full name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        ProfileDropdown(
            label = "Blood type",
            selected = vm.bloodType,
            options = bloodTypes,
            onSelect = { vm.bloodType = it },
        )
        ProfileDropdown(
            label = "Gender",
            selected = vm.gender,
            options = genders,
            displayTransform = { it.ifEmpty { "Not specified" } },
            onSelect = { vm.gender = it },
        )
        Text(
            "Emergency Contact",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = vm.emergencyContactName,
            onValueChange = { vm.emergencyContactName = it },
            label = { Text("Contact name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        OutlinedTextField(
            value = vm.emergencyContactPhone,
            onValueChange = { vm.emergencyContactPhone = it },
            label = { Text("Phone number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
        FilledTonalButton(
            onClick = { focusManager.clearFocus(); vm.savePersonalInfo() },
            modifier = Modifier.fillMaxWidth(),
            enabled = vm.name.isNotBlank() && !vm.isSaving,
        ) {
            if (vm.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Save Changes")
        }
    }
}

// ── Conditions ────────────────────────────────────────────────────────────────

@Composable
private fun ConditionsSection(vm: ProfileViewModel, conditions: List<ConditionEntity>) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Medical Conditions")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = vm.newCondition,
                onValueChange = { vm.newCondition = it },
                label = { Text("Condition name") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    vm.addCondition(); focusManager.clearFocus()
                }),
            )
            FilledTonalIconButton(
                onClick = { vm.addCondition(); focusManager.clearFocus() },
                enabled = vm.newCondition.isNotBlank(),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add condition")
            }
        }
        ProfileSuggestionsRow(
            query = vm.newCondition,
            pool = Suggestions.conditions,
            onSelect = { vm.newCondition = it; vm.addCondition(); focusManager.clearFocus() },
        )
        if (conditions.isEmpty()) {
            Text("No conditions added.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            conditions.forEach { condition ->
                ProfileChipRow(text = condition.name, onRemove = { vm.deleteCondition(condition) })
            }
        }
    }
}

// ── Medications ───────────────────────────────────────────────────────────────

@Composable
private fun MedicationsSection(vm: ProfileViewModel, medications: List<MedicationEntity>) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Medications")
        OutlinedTextField(
            value = vm.newMedName,
            onValueChange = { vm.newMedName = it },
            label = { Text("Medication name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        ProfileSuggestionsRow(
            query = vm.newMedName,
            pool = Suggestions.medications,
            onSelect = { vm.newMedName = it },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = vm.newMedDosage,
                onValueChange = { vm.newMedDosage = it },
                label = { Text("Dosage") },
                placeholder = { Text("e.g. 500 mg") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = vm.newMedFrequency,
                onValueChange = { vm.newMedFrequency = it },
                label = { Text("Frequency") },
                placeholder = { Text("e.g. Twice daily") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    vm.addMedication(); focusManager.clearFocus()
                }),
            )
        }
        Button(
            onClick = { vm.addMedication(); focusManager.clearFocus() },
            enabled = vm.newMedName.isNotBlank() && vm.newMedDosage.isNotBlank() && vm.newMedFrequency.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add Medication") }
        if (medications.isEmpty()) {
            Text("No medications added.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            medications.forEach { med ->
                ProfileMedCard(med = med, onRemove = { vm.deleteMedication(med) })
            }
        }
    }
}

// ── Allergies ─────────────────────────────────────────────────────────────────

@Composable
private fun AllergiesSection(vm: ProfileViewModel, allergies: List<AllergyEntity>) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Allergies")
        OutlinedTextField(
            value = vm.newAllergySubstance,
            onValueChange = { vm.newAllergySubstance = it },
            label = { Text("Substance or allergen") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
        ProfileSuggestionsRow(
            query = vm.newAllergySubstance,
            pool = Suggestions.allergens,
            onSelect = { vm.newAllergySubstance = it; focusManager.clearFocus() },
        )
        ProfileDropdown(
            label = "Severity",
            selected = vm.newAllergySeverity,
            options = severities,
            onSelect = { vm.newAllergySeverity = it },
        )
        Button(
            onClick = { vm.addAllergy(); focusManager.clearFocus() },
            enabled = vm.newAllergySubstance.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add Allergy") }
        if (allergies.isEmpty()) {
            Text("No allergies added.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            allergies.forEach { allergy ->
                ProfileChipRow(
                    text = "${allergy.substance}  ·  ${allergy.severity}",
                    onRemove = { vm.deleteAllergy(allergy) },
                )
            }
        }
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun ProfileSuggestionsRow(query: String, pool: List<String>, onSelect: (String) -> Unit) {
    val matches = remember(query) {
        if (query.length >= 2) pool.filter { it.contains(query, ignoreCase = true) }.take(5)
        else emptyList()
    }
    if (matches.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        matches.forEach { suggestion ->
            SuggestionChip(onClick = { onSelect(suggestion) }, label = { Text(suggestion, maxLines = 1) })
        }
    }
}

@Composable
private fun ProfileDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    displayTransform: (String) -> String = { it },
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = displayTransform(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(displayTransform(option)) },
                    onClick = { onSelect(option); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun ProfileChipRow(text: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BackupRestoreRow(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Backup & Restore", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Encrypted backup to Google Drive",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileMedCard(med: MedicationEntity, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(med.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${med.dosage}  ·  ${med.frequency}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
