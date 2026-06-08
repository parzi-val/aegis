package com.example.aegis.ui.screens.export

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aegis.data.db.entity.DocumentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun ExportScreen(
    onStartTransfer: (selectedDocIds: List<Long>) -> Unit = {},
) {
    val vm: ExportViewModel = hiltViewModel()
    val patient     by vm.patient.collectAsStateWithLifecycle()
    val conditions  by vm.conditions.collectAsStateWithLifecycle()
    val medications by vm.medications.collectAsStateWithLifecycle()
    val allergies   by vm.allergies.collectAsStateWithLifecycle()
    val documents   by vm.documents.collectAsStateWithLifecycle()
    val selectedIds by vm.selectedDocIds.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(
                    onClick = { onStartTransfer(selectedIds.toList()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Transfer via Relay")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Share Records", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Profile, conditions, medications, and allergies are always included. Select which documents to attach.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            item {
                SectionCard("Profile") {
                    if (patient != null) {
                        Text(patient!!.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                patient!!.gender,
                                patient!!.bloodType.takeIf { it != "Unknown" }?.let { "Blood type $it" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("No profile — complete onboarding first",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (conditions.isNotEmpty()) {
                item {
                    SectionCard("Active Conditions (${conditions.size})") {
                        conditions.forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            if (medications.isNotEmpty()) {
                item {
                    SectionCard("Current Medications (${medications.size})") {
                        medications.forEach {
                            Text("• ${it.name} — ${it.dosage} — ${it.frequency}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (allergies.isNotEmpty()) {
                item {
                    SectionCard("Allergies (${allergies.size})") {
                        allergies.forEach {
                            Text("• ${it.substance} (${it.severity})",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Documents", style = MaterialTheme.typography.titleMedium)
                    Row {
                        TextButton(onClick = { vm.selectAll() }) { Text("All") }
                        TextButton(onClick = { vm.clearSelection() }) { Text("None") }
                    }
                }
                HorizontalDivider()
            }

            if (documents.isEmpty()) {
                item {
                    Text(
                        "No documents in vault",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(documents, key = { it.id }) { doc ->
                    DocumentRow(
                        doc      = doc,
                        selected = doc.id in selectedIds,
                        onToggle = { vm.toggleDocument(doc.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            content()
        }
    }
}

@Composable
private fun DocumentRow(doc: DocumentEntity, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = doc.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    doc.documentType.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                    doc.providerName.ifBlank { null },
                    doc.documentDate?.let { dateFmt.format(Date(it)) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
