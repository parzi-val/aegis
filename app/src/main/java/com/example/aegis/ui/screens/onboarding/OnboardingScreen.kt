@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.aegis.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.aegis.data.suggestions.Suggestions

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val vm: OnboardingViewModel = hiltViewModel()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    LaunchedEffect(step) { scrollState.animateScrollTo(0) }

    // imePadding() on the outer column makes the Back/Next buttons float above the keyboard
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
        ) {
            StepProgressBar(current = step, total = 4)
            Spacer(Modifier.height(32.dp))
            when (step) {
                0 -> ProfileStep(vm)
                1 -> ConditionsStep(vm)
                2 -> MedicationsStep(vm)
                3 -> AllergiesStep(vm)
            }
            Spacer(Modifier.height(24.dp))
        }

        Surface(
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(1f),
                    ) { Text("Back") }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = { if (step < 3) step++ else vm.save(onComplete) },
                    modifier = Modifier.weight(1f),
                    enabled = !vm.isSaving && isStepValid(vm, step),
                ) {
                    if (vm.isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (step < 3) "Next" else "Save Profile")
                    }
                }
            }
        }
    }
}

private fun isStepValid(vm: OnboardingViewModel, step: Int): Boolean = when (step) {
    0 -> vm.name.isNotBlank() && vm.emergencyContactName.isNotBlank() && vm.emergencyContactPhone.isNotBlank()
    else -> true
}

// ── Progress bar ──────────────────────────────────────────────────────────────

@Composable
private fun StepProgressBar(current: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
            )
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))
}

// ── Step 0: Profile ───────────────────────────────────────────────────────────

private val bloodTypes = listOf("Unknown", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
private val genders = listOf("", "Male", "Female", "Non-binary", "Prefer not to say")

@Composable
private fun ProfileStep(vm: OnboardingViewModel) {
    val focusManager = LocalFocusManager.current
    StepHeader(
        title = "Your Profile",
        subtitle = "All three fields below are required",
    )
    OutlinedTextField(
        value = vm.name,
        onValueChange = { vm.name = it },
        label = { Text("Full name *") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
        ),
    )
    Spacer(Modifier.height(16.dp))
    AegisDropdown(
        label = "Blood type",
        selected = vm.bloodType,
        options = bloodTypes,
        onSelect = { vm.bloodType = it },
    )
    Spacer(Modifier.height(16.dp))
    AegisDropdown(
        label = "Gender",
        selected = vm.gender,
        options = genders,
        displayTransform = { it.ifEmpty { "Not specified" } },
        onSelect = { vm.gender = it },
    )
    Spacer(Modifier.height(24.dp))
    Text(
        "Emergency Contact",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = vm.emergencyContactName,
        onValueChange = { vm.emergencyContactName = it },
        label = { Text("Contact name *") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
        ),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.emergencyContactPhone,
        onValueChange = { vm.emergencyContactPhone = it },
        label = { Text("Phone number *") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
}

// ── Step 1: Conditions ────────────────────────────────────────────────────────

@Composable
private fun ConditionsStep(vm: OnboardingViewModel) {
    val focusManager = LocalFocusManager.current
    StepHeader(
        title = "Medical Conditions",
        subtitle = "Add any chronic or currently active conditions",
    )
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
            Icon(Icons.Filled.Add, contentDescription = "Add condition")
        }
    }
    SuggestionsRow(
        query = vm.newCondition,
        pool = Suggestions.conditions,
        onSelect = { vm.newCondition = it; vm.addCondition(); focusManager.clearFocus() },
    )
    Spacer(Modifier.height(16.dp))
    if (vm.conditions.isEmpty()) {
        Text(
            "No conditions added yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        vm.conditions.forEachIndexed { index, condition ->
            ChipRow(text = condition, onRemove = { vm.removeCondition(index) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Step 2: Medications ───────────────────────────────────────────────────────

@Composable
private fun MedicationsStep(vm: OnboardingViewModel) {
    val focusManager = LocalFocusManager.current
    StepHeader(
        title = "Current Medications",
        subtitle = "Natural language is fine — e.g. \"500 mg after meals\"",
    )
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
    SuggestionsRow(
        query = vm.newMedName,
        pool = Suggestions.medications,
        onSelect = { vm.newMedName = it },
    )
    Spacer(Modifier.height(12.dp))
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
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { vm.addMedication(); focusManager.clearFocus() },
        enabled = vm.newMedName.isNotBlank() && vm.newMedDosage.isNotBlank() && vm.newMedFrequency.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Add Medication") }
    Spacer(Modifier.height(20.dp))
    if (vm.medications.isEmpty()) {
        Text(
            "No medications added yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        vm.medications.forEachIndexed { index, med ->
            MedCard(med = med, onRemove = { vm.removeMedication(index) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Step 3: Allergies ─────────────────────────────────────────────────────────

private val severities = listOf("Unknown", "Mild", "Moderate", "Severe", "Life-threatening")

@Composable
private fun AllergiesStep(vm: OnboardingViewModel) {
    val focusManager = LocalFocusManager.current
    StepHeader(
        title = "Allergies",
        subtitle = "Note any known allergies or adverse reactions",
    )
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
    SuggestionsRow(
        query = vm.newAllergySubstance,
        pool = Suggestions.allergens,
        onSelect = { vm.newAllergySubstance = it; focusManager.clearFocus() },
    )
    Spacer(Modifier.height(12.dp))
    AegisDropdown(
        label = "Severity",
        selected = vm.newAllergySeverity,
        options = severities,
        onSelect = { vm.newAllergySeverity = it },
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { vm.addAllergy(); focusManager.clearFocus() },
        enabled = vm.newAllergySubstance.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Add Allergy") }
    Spacer(Modifier.height(20.dp))
    if (vm.allergies.isEmpty()) {
        Text(
            "No allergies added yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        vm.allergies.forEachIndexed { index, allergy ->
            ChipRow(
                text = "${allergy.substance}  ·  ${allergy.severity}",
                onRemove = { vm.removeAllergy(index) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Reusable components ───────────────────────────────────────────────────────

@Composable
private fun SuggestionsRow(
    query: String,
    pool: List<String>,
    onSelect: (String) -> Unit,
) {
    val matches = remember(query) {
        if (query.length >= 2) pool.filter { it.contains(query, ignoreCase = true) }.take(5)
        else emptyList()
    }
    if (matches.isEmpty()) return
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        matches.forEach { suggestion ->
            SuggestionChip(
                onClick = { onSelect(suggestion) },
                label = { Text(suggestion, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun AegisDropdown(
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
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
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
private fun ChipRow(text: String, onRemove: () -> Unit) {
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
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MedCard(med: OnboardingViewModel.MedEntry, onRemove: () -> Unit) {
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
                Text(
                    text = med.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${med.dosage}  ·  ${med.frequency}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
