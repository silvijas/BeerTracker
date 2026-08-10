package com.beertracker.ui.brewery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beertracker.R
import com.beertracker.domain.BrewerySort
import com.beertracker.ui.components.CatalogListItem
import com.beertracker.ui.components.EmptyState
import com.beertracker.ui.components.LoadingState
import com.beertracker.ui.theme.BeerTrackerSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreweryBeersScreen(
    viewModel: BreweryBeersViewModel,
    breweryName: String,
    onAddProduct: (String) -> Unit,
    onOpenBeer: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(breweryName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when (val current = state) {
            BreweryBeersUiState.Loading -> {
                LoadingState(
                    label = stringResource(R.string.catalog_loading),
                    modifier = Modifier.padding(padding),
                )
            }
            is BreweryBeersUiState.Content -> {
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    BreweryControlsRow(
                        sort = current.sort,
                        filter = current.filter,
                        onSetSort = viewModel::setSort,
                        onSetFilter = viewModel::setFilter,
                    )
                    when (current.emptyState) {
                        BreweryBeersEmptyState.NO_CATALOG_MATCHES -> {
                            EmptyState(
                                title = stringResource(R.string.brewery_no_catalog_beers_title),
                                message = stringResource(
                                    R.string.brewery_no_catalog_beers_message,
                                    breweryName,
                                ),
                                actionLabel = stringResource(R.string.back),
                                onAction = onBack,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        BreweryBeersEmptyState.FILTERED_EMPTY -> {
                            EmptyState(
                                title = stringResource(R.string.no_results_title),
                                message = if (current.filter == BreweryTriedFilter.TRIED) {
                                    stringResource(R.string.brewery_no_tried_message, breweryName)
                                } else {
                                    stringResource(R.string.brewery_no_untried_message, breweryName)
                                },
                                actionLabel = stringResource(R.string.show_all_action),
                                onAction = { viewModel.setFilter(BreweryTriedFilter.ALL) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        null -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = BeerTrackerSpacing.large,
                                    end = BeerTrackerSpacing.large,
                                    bottom = BeerTrackerSpacing.section,
                                ),
                            ) {
                                items(current.rows, key = { it.product.articleNumber }) { row ->
                                    CatalogListItem(
                                        row = row,
                                        subtitle = row.product.type.trim().ifEmpty { null },
                                        onClick = {
                                            val beerId = row.triedBeerId
                                            if (beerId != null) {
                                                onOpenBeer(beerId)
                                            } else {
                                                onAddProduct(row.product.articleNumber)
                                            }
                                        },
                                        modifier = Modifier.padding(vertical = BeerTrackerSpacing.xSmall),
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreweryControlsRow(
    sort: BrewerySort,
    filter: BreweryTriedFilter,
    onSetSort: (BrewerySort) -> Unit,
    onSetFilter: (BreweryTriedFilter) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    var filterMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BeerTrackerSpacing.large, vertical = BeerTrackerSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small),
    ) {
        Box {
            TextButton(onClick = { sortMenuOpen = true }) {
                Text(stringResource(R.string.sort_by, sortLabel(sort)))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                BrewerySort.entries.forEach { option ->
                    val isSelected = option == sort
                    DropdownMenuItem(
                        modifier = Modifier.semantics { selected = isSelected },
                        text = { Text(sortLabel(option)) },
                        trailingIcon = if (isSelected) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            onSetSort(option)
                            sortMenuOpen = false
                        },
                    )
                }
            }
        }
        Box {
            TextButton(onClick = { filterMenuOpen = true }) {
                Text(stringResource(R.string.show_by, filterLabel(filter)))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                BreweryTriedFilter.entries.forEach { option ->
                    val isSelected = option == filter
                    DropdownMenuItem(
                        modifier = Modifier.semantics { selected = isSelected },
                        text = { Text(filterLabel(option)) },
                        trailingIcon = if (isSelected) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            onSetFilter(option)
                            filterMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun sortLabel(sort: BrewerySort): String = stringResource(
    when (sort) {
        BrewerySort.NAME -> R.string.sort_name
        BrewerySort.TYPE -> R.string.sort_type
    },
)

@Composable
private fun filterLabel(filter: BreweryTriedFilter): String = stringResource(
    when (filter) {
        BreweryTriedFilter.ALL -> R.string.filter_all
        BreweryTriedFilter.TRIED -> R.string.tried
        BreweryTriedFilter.NOT_TRIED -> R.string.not_tried
    },
)
