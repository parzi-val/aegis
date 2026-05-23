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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aegis.ui.theme.AegisTheme

@Composable
fun HomeScreen(
    onNeedsOnboarding: () -> Unit = {},
    onGemmaPoc: () -> Unit = {},
) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.profileState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is HomeViewModel.ProfileState.Empty) onNeedsOnboarding()
    }

    when (val s = state) {
        HomeViewModel.ProfileState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        HomeViewModel.ProfileState.Empty -> {
            // onNeedsOnboarding already fired via LaunchedEffect — render nothing
        }
        is HomeViewModel.ProfileState.Loaded -> ProfileSummary(s, onGemmaPoc = onGemmaPoc)
    }
}

@Composable
private fun ProfileSummary(state: HomeViewModel.ProfileState.Loaded, onGemmaPoc: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "Hello,",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.patient.name,
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Blood type: ${state.patient.bloodType}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SummaryCard(label = "Conditions", count = state.activeConditionsCount, modifier = Modifier.weight(1f))
            SummaryCard(label = "Medications", count = state.activeMedicationsCount, modifier = Modifier.weight(1f))
            SummaryCard(label = "Allergies", count = state.allergiesCount, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(40.dp))

        // TODO Phase 5: recent documents section
        // TODO Phase 8: upcoming visits section

        // DEV ONLY — remove after Gemma PoC validated
        OutlinedButton(onClick = onGemmaPoc, modifier = Modifier.fillMaxWidth()) {
            Text("Gemma 3n PoC →")
        }
        Spacer(Modifier.height(16.dp))

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
