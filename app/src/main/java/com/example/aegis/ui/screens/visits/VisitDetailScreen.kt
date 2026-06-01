package com.example.aegis.ui.screens.visits

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aegis.data.db.entity.DocumentEntity
import com.example.aegis.data.db.entity.VisitLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VisitDetailScreen(
    onBack: () -> Unit = {},
    onNavigateToDocument: (Long) -> Unit = {},
) {
    val vm: VisitDetailViewModel = hiltViewModel()
    val visit by vm.visit.collectAsStateWithLifecycle()
    val linkedDocs by vm.linkedDocs.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteLinkedFiles by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; deleteLinkedFiles = false },
            title = { Text("Delete visit?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This will permanently remove this visit log.")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deleteLinkedFiles = !deleteLinkedFiles },
                    ) {
                        Checkbox(
                            checked = deleteLinkedFiles,
                            onCheckedChange = { deleteLinkedFiles = it },
                        )
                        Text(
                            text = "Delete linked files too",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteVisit(deleteLinkedFiles, onBack)
                    showDeleteDialog = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; deleteLinkedFiles = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(visit?.clinicName?.ifBlank { "Visit" } ?: "Visit") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete visit", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
        if (visit == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        VisitDetailContent(
            visit = visit!!,
            linkedDocs = linkedDocs,
            onNavigateToDocument = onNavigateToDocument,
            modifier = Modifier.padding(padding),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisitDetailContent(
    visit: VisitLogEntity,
    linkedDocs: List<DocumentEntity>,
    onNavigateToDocument: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header — provider + date
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = visit.clinicName.ifBlank { "Unknown Provider" },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = dateFmt.format(Date(visit.visitDate)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        // Conditions
        if (visit.conditionTags.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Conditions", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    visit.conditionTags.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { tag ->
                            SuggestionChip(onClick = {}, label = { Text(tag) })
                        }
                }
            }
        }

        // Notes
        if (visit.notes.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Notes", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = visit.notes,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Doctor
        if (visit.doctorName.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Doctor", style = MaterialTheme.typography.labelLarge)
                Text(visit.doctorName, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Linked documents
        if (linkedDocs.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Documents", style = MaterialTheme.typography.labelLarge)
                linkedDocs.forEach { doc ->
                    DocumentChip(doc = doc, onClick = { onNavigateToDocument(doc.id) })
                }
            }
        } else if (visit.linkedDocumentIds.isNotBlank()) {
            // IDs exist but docs haven't loaded yet
            CircularProgressIndicator()
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DocumentChip(doc: DocumentEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = doc.fileName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val stateLabel = when (doc.extractionState) {
                "DONE" -> "Extracted"
                "PENDING" -> "Processing"
                "ERROR" -> "Error"
                else -> doc.extractionState
            }
            val stateColor = when (doc.extractionState) {
                "DONE" -> MaterialTheme.colorScheme.primary
                "ERROR" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = stateColor)
        }
    }
}
