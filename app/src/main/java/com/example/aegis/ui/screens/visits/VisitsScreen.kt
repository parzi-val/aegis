package com.example.aegis.ui.screens.visits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aegis.data.db.entity.VisitLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayFmt   = SimpleDateFormat("dd",        Locale.getDefault())
private val monFmt   = SimpleDateFormat("MMM",       Locale.getDefault())
private val groupFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

@Composable
fun VisitsScreen(
    onAddVisit: () -> Unit = {},
    onVisitDetail: (Long) -> Unit = {},
) {
    val vm: VisitsViewModel = hiltViewModel()
    val visits by vm.visits.collectAsStateWithLifecycle()
    val pendingDocIds by vm.pendingDocIds.collectAsStateWithLifecycle()

    val grouped = remember(visits) {
        visits
            .sortedByDescending { it.visitDate }
            .groupBy { groupFmt.format(Date(it.visitDate)) }
    }
    val groupKeys = remember(grouped) { grouped.keys.toList() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddVisit) {
                Icon(Icons.Default.Add, contentDescription = "Log visit")
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Visit Log", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(16.dp))
            }

            if (visits.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No visits yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tap + to log a visit",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            grouped.entries.forEachIndexed { groupIndex, (monthYear, monthVisits) ->
                item(key = "header_$monthYear") {
                    MonthHeader(monthYear, showTopSpacing = groupIndex > 0)
                }
                itemsIndexed(monthVisits, key = { _, v -> v.id }) { index, visit ->
                    val isProcessing = visit.linkedDocumentIds
                        .split(",").mapNotNull { it.trim().toLongOrNull() }
                        .any { it in pendingDocIds }
                    val isLast = groupIndex == groupKeys.lastIndex && index == monthVisits.lastIndex
                    TimelineEntry(
                        visit = visit,
                        isLast = isLast,
                        isProcessing = isProcessing,
                        onClick = { onVisitDetail(visit.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun MonthHeader(label: String, showTopSpacing: Boolean) {
    Column {
        if (showTopSpacing) Spacer(Modifier.height(8.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TimelineEntry(
    visit: VisitLogEntity,
    isLast: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val date = Date(visit.visitDate)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
    ) {
        // Left: large day + month abbrev
        Column(
            modifier = Modifier
                .width(52.dp)
                .padding(top = 4.dp, end = 4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = dayFmt.format(date),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = monFmt.format(date).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Center: vertical line + dot
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!isLast) {
                Canvas(modifier = Modifier.fillMaxHeight()) {
                    drawLine(
                        color = lineColor,
                        start = Offset(size.width / 2f, 24.dp.toPx()),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(primaryColor),
            )
        }

        // Right: visit info
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 6.dp, bottom = 20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = visit.clinicName.ifBlank { "Unknown Provider" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (visit.conditionTags.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = visit.conditionTags.replace(",", " · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (visit.notes.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = visit.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (visit.linkedDocumentIds.isNotBlank()) {
                val count = visit.linkedDocumentIds.split(",").count { it.isNotBlank() }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$count doc${if (count != 1) "s" else ""} attached",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
