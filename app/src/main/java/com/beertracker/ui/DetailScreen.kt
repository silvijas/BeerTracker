package com.beertracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val beer by viewModel.beer.collectAsStateWithLifecycle()
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val b = beer

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(b?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (b != null) {
                        IconButton(onClick = { onEdit(b.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (b == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    if (b.brewery.isNotBlank()) {
                        Text(b.brewery, style = MaterialTheme.typography.titleMedium)
                    }
                    if (b.type.isNotBlank()) {
                        Text(b.type, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val grade = b.grade
                when {
                    grade != null -> Text(
                        "$grade",
                        style = MaterialTheme.typography.displayMedium,
                    )
                    b.tried -> Text(
                        "No grade",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Text(
                        "Not tried",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = b.favourite,
                    onClick = viewModel::toggleFavourite,
                    label = { Text("Favourite") },
                )
                FilterChip(
                    selected = b.buyAgain,
                    onClick = viewModel::toggleBuyAgain,
                    label = { Text("Buy again") },
                )
            }
            b.alcoholPercent?.let { InfoRow("Alcohol", "$it %") }
            b.volumeMl?.let { InfoRow("Volume", "$it ml") }
            b.price?.let { InfoRow("Price", "$it kr") }
            InfoRow(
                "Added",
                Instant.ofEpochMilli(b.dateAdded).atZone(ZoneId.systemDefault())
                    .toLocalDate().toString(),
            )
            if (b.note.isNotBlank()) InfoRow("Note", b.note)
            if (b.aftertaste.isNotBlank()) InfoRow("Aftertaste", b.aftertaste)
            if (b.goesWellWith.isNotEmpty()) {
                InfoRow("Goes well with", b.goesWellWith.joinToString(", "))
            }
        }
    }

    if (showDeleteDialog && b != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${b.name}?") },
            text = { Text("This removes the beer from your list.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
