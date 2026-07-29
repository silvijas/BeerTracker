package com.beertracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beertracker.domain.BeerSort
import com.beertracker.domain.TriedBeer

private val sortLabels = mapOf(
    BeerSort.GRADE to "Grade",
    BeerSort.PRICE to "Price",
    BeerSort.NAME_BREWERY to "Name",
    BeerSort.DATE_ADDED to "Newest",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onAddClick: () -> Unit,
    onBeerClick: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("BeerTracker") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Add beer")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search name, brewery, type") },
                singleLine = true,
            )
            FilterRow(state, viewModel)
            if (state.beers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No beers yet. Tap + to add your first one.")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.beers, key = { it.id }) { beer ->
                        BeerRow(beer, onClick = { onBeerClick(beer.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(state: OverviewUiState, viewModel: OverviewViewModel) {
    var typeMenuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.filter.buyAgainOnly,
            onClick = viewModel::toggleBuyAgainOnly,
            label = { Text("Buy again") },
        )
        FilterChip(
            selected = state.filter.favouritesOnly,
            onClick = viewModel::toggleFavouritesOnly,
            label = { Text("Favourites") },
        )
        FilterChip(
            selected = state.filter.notTriedOnly,
            onClick = viewModel::toggleNotTriedOnly,
            label = { Text("Not tried") },
        )
        Box {
            FilterChip(
                selected = state.filter.types.isNotEmpty(),
                onClick = { typeMenuOpen = true },
                label = {
                    val count = state.filter.types.size
                    Text(if (count == 0) "Type" else "Type ($count)")
                },
            )
            DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                if (state.availableTypes.isEmpty()) {
                    DropdownMenuItem(text = { Text("No types yet") }, onClick = {})
                }
                state.availableTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(if (type in state.filter.types) "[x] $type" else type) },
                        onClick = { viewModel.toggleType(type) },
                    )
                }
            }
        }
        Box {
            TextButton(onClick = { sortMenuOpen = true }) {
                Text("Sort: ${sortLabels.getValue(state.sort)}")
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                BeerSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sortLabels.getValue(sort)) },
                        onClick = {
                            viewModel.setSort(sort)
                            sortMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BeerRow(beer: TriedBeer, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(beer.name, style = MaterialTheme.typography.titleMedium)
                if (beer.favourite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Star, contentDescription = "Favourite",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (beer.buyAgain) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Check, contentDescription = "Buy again",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            val subtitle = listOf(beer.brewery, beer.type)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
        val grade = beer.grade
        when {
            grade != null -> Text("$grade", style = MaterialTheme.typography.headlineMedium)
            beer.tried -> Text(
                "No grade",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Text(
                "Not tried",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
