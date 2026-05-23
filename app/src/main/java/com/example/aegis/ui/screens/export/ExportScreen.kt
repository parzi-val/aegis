package com.example.aegis.ui.screens.export

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

// TODO Phase 9: add two export paths —
//   (1) QR emergency snapshot (blood type + allergies + current meds, no network required)
//   (2) Selective PDF export — patient picks which records leave the device.
@Composable
fun ExportScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Export", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "QR snapshot and PDF export options will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExportScreenPreview() {
    AegisTheme { ExportScreen() }
}
