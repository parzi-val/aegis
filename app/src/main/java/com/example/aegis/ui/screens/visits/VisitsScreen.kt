package com.example.aegis.ui.screens.visits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aegis.ui.theme.AegisTheme

// TODO Phase 8: add "New Visit" FAB, consultation list (LazyColumn sorted by date),
//   detail screen with diagnosis, prescription, notes, follow-up date fields.
@Composable
fun VisitsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Visit Log", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Your consultation records will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VisitsScreenPreview() {
    AegisTheme { VisitsScreen() }
}
