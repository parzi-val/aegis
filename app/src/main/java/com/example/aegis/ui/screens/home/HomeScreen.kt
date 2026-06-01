package com.example.aegis.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aegis.data.db.entity.ConditionSuggestionEntity
import com.example.aegis.data.db.entity.MedicationSuggestionEntity
import com.example.aegis.ui.theme.AegisTheme

@Composable
fun HomeScreen(
    onNeedsOnboarding: () -> Unit = {},
    onAddVisit: () -> Unit = {},
    onProfile: () -> Unit = {},
) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.profileState.collectAsStateWithLifecycle()
    val conditionSuggestions by vm.conditionSuggestions.collectAsStateWithLifecycle()
    val medicationSuggestions by vm.medicationSuggestions.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is HomeViewModel.ProfileState.Empty) onNeedsOnboarding()
    }

    when (val s = state) {
        HomeViewModel.ProfileState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        HomeViewModel.ProfileState.Empty -> {}
        is HomeViewModel.ProfileState.Loaded -> ProfileSummary(
            state = s,
            conditionSuggestions = conditionSuggestions,
            medicationSuggestions = medicationSuggestions,
            onAddVisit = onAddVisit,
            onProfile = onProfile,
            onAcceptCondition = vm::acceptCondition,
            onDismissCondition = vm::dismissCondition,
            onAcceptMedication = vm::acceptMedication,
            onDismissMedication = vm::dismissMedication,
        )
    }
}

@Composable
private fun ProfileSummary(
    state: HomeViewModel.ProfileState.Loaded,
    conditionSuggestions: List<ConditionSuggestionEntity>,
    medicationSuggestions: List<MedicationSuggestionEntity>,
    onAddVisit: () -> Unit = {},
    onProfile: () -> Unit = {},
    onAcceptCondition: (ConditionSuggestionEntity) -> Unit = {},
    onDismissCondition: (ConditionSuggestionEntity) -> Unit = {},
    onAcceptMedication: (MedicationSuggestionEntity) -> Unit = {},
    onDismissMedication: (MedicationSuggestionEntity) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = "Hello,",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = state.patient.name, style = MaterialTheme.typography.headlineLarge)
            }
            IconButton(onClick = onProfile) {
                Icon(Icons.Default.Person, contentDescription = "Edit profile")
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Blood type: ${state.patient.bloodType}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard(label = "Conditions", count = state.activeConditionsCount, modifier = Modifier.weight(1f))
            SummaryCard(label = "Medications", count = state.activeMedicationsCount, modifier = Modifier.weight(1f))
            SummaryCard(label = "Allergies", count = state.allergiesCount, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(40.dp))

        // TODO Phase 5: recent documents section

        Button(onClick = onAddVisit, modifier = Modifier.fillMaxWidth()) {
            Text("Log Visit")
        }

        Spacer(Modifier.height(24.dp))

        val hasSuggestions = conditionSuggestions.isNotEmpty() || medicationSuggestions.isNotEmpty()
        Text(
            text = "Actions Required",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (!hasSuggestions) {
            Text(
                text = "No actions required",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        } else {
            conditionSuggestions.forEach { suggestion ->
                SuggestionCard(
                    label = "Add condition to your profile?",
                    name = suggestion.name,
                    detail = null,
                    onAccept = { onAcceptCondition(suggestion) },
                    onDismiss = { onDismissCondition(suggestion) },
                )
                Spacer(Modifier.height(8.dp))
            }
            medicationSuggestions.forEach { suggestion ->
                SuggestionCard(
                    label = "Add medication to your profile?",
                    name = suggestion.name,
                    detail = suggestion.dosage.ifBlank { null },
                    onAccept = { onAcceptMedication(suggestion) },
                    onDismiss = { onDismissMedication(suggestion) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Your health vault is secured on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outlineVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SuggestionCard(
    label: String,
    name: String,
    detail: String?,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (detail != null) "$name  ·  $detail" else name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                OutlinedButton(onClick = onAccept) { Text("Add") }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    AegisTheme { HomeScreen() }
}
