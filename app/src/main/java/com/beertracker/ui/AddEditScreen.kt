package com.beertracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(
    viewModel: AddEditBeerViewModel,
    beerId: String?,
    onDone: () -> Unit,
) {
    LaunchedEffect(beerId) {
        if (beerId != null) viewModel.load(beerId)
    }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val typeOptions by viewModel.typeOptions.collectAsStateWithLifecycle()
    val pairingOptions by viewModel.pairingOptions.collectAsStateWithLifecycle()

    LaunchedEffect(form.saved) {
        if (form.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (beerId == null) "Add beer" else "Edit beer") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v, nameError = false) } },
                label = { Text("Name *") },
                isError = form.nameError,
                supportingText = { if (form.nameError) Text("Name is required") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.brewery,
                onValueChange = { v -> viewModel.update { it.copy(brewery = v) } },
                label = { Text("Brewery") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.type,
                onValueChange = { v -> viewModel.update { it.copy(type = v) } },
                label = { Text("Type") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                typeOptions.forEach { option ->
                    SuggestionChip(
                        onClick = { viewModel.update { it.copy(type = option) } },
                        label = { Text(option) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.alcoholPercent,
                    onValueChange = { v -> viewModel.update { it.copy(alcoholPercent = v) } },
                    label = { Text("Alc %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.volumeMl,
                    onValueChange = { v -> viewModel.update { it.copy(volumeMl = v) } },
                    label = { Text("Volume ml") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.price,
                    onValueChange = { v -> viewModel.update { it.copy(price = v) } },
                    label = { Text("Price kr") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = form.tried,
                    onCheckedChange = viewModel::setTried,
                )
                Text("  Tried")
            }
            Text("Grade", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (5..10).forEach { g ->
                    FilterChip(
                        selected = form.grade == g,
                        onClick = { viewModel.setGrade(if (form.grade == g) null else g) },
                        label = { Text("$g") },
                    )
                }
            }
            Text(
                if (form.gradeError) {
                    "Grade must be 5 to 10, or left empty"
                } else {
                    "Tap a selected grade to clear it. No grade means not graded yet."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (form.gradeError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> viewModel.update { it.copy(note = v) } },
                label = { Text("Note") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.aftertaste,
                onValueChange = { v -> viewModel.update { it.copy(aftertaste = v) } },
                label = { Text("Aftertaste") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Goes well with", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pairingOptions.forEach { option ->
                    FilterChip(
                        selected = option in form.pairings,
                        onClick = {
                            viewModel.update {
                                val s = it.pairings
                                it.copy(pairings = if (option in s) s - option else s + option)
                            }
                        },
                        label = { Text(option) },
                    )
                }
            }
            OutlinedTextField(
                value = form.customPairing,
                onValueChange = { v -> viewModel.update { it.copy(customPairing = v) } },
                label = { Text("Other pairing") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = form.buyAgain,
                    onCheckedChange = { v -> viewModel.update { it.copy(buyAgain = v) } },
                )
                Text("  Buy again")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = form.favourite,
                    onCheckedChange = { v -> viewModel.update { it.copy(favourite = v) } },
                )
                Text("  Favourite")
            }
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}
