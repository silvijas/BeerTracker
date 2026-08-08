package com.beertracker.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beertracker.R
import com.beertracker.domain.BeerSort
import com.beertracker.ui.components.BeerListItem
import com.beertracker.ui.components.EmptyState
import com.beertracker.ui.components.ErrorState
import com.beertracker.ui.components.LoadingState
import com.beertracker.ui.theme.BeerTrackerSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onAddClick: () -> Unit,
    onBeerClick: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (state is OverviewUiState.Content) {
                ExtendedFloatingActionButton(
                    onClick = onAddClick,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_beer)) },
                )
            }
        },
    ) { padding ->
        when (val current = state) {
            OverviewUiState.Loading -> {
                LoadingState(
                    label = stringResource(R.string.load_cellar),
                    modifier = Modifier.padding(padding),
                )
            }
            OverviewUiState.Error -> {
                ErrorState(
                    title = stringResource(R.string.overview_error_title),
                    message = stringResource(R.string.overview_error_message),
                    actionLabel = stringResource(R.string.retry),
                    onAction = viewModel::tryAgain,
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
            }
            is OverviewUiState.Content -> {
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    OutlinedTextField(
                        value = current.filter.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = BeerTrackerSpacing.large),
                        label = { Text(stringResource(R.string.search_label)) },
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = if (current.filter.query.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.clear_search),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )
                    FilterRow(
                        state = current,
                        onToggleBuyAgain = viewModel::toggleBuyAgainOnly,
                        onToggleFavourite = viewModel::toggleFavouritesOnly,
                        onToggleNotTried = viewModel::toggleNotTriedOnly,
                        onToggleType = viewModel::toggleType,
                        onSort = viewModel::setSort,
                    )
                    when (current.emptyState) {
                        OverviewEmptyState.EMPTY_CELLAR -> {
                            EmptyState(
                                title = stringResource(R.string.empty_cellar_title),
                                message = stringResource(R.string.empty_cellar_message),
                                actionLabel = stringResource(R.string.add_beer),
                                onAction = onAddClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OverviewEmptyState.NO_RESULTS -> {
                            EmptyState(
                                title = stringResource(R.string.no_results_title),
                                message = stringResource(R.string.no_results_message),
                                actionLabel = stringResource(R.string.clear_filters),
                                onAction = viewModel::clearFilters,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        null -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = BeerTrackerSpacing.large,
                                    end = BeerTrackerSpacing.large,
                                    bottom = 96.dp,
                                ),
                            ) {
                                items(current.beers, key = { it.id }) { beer ->
                                    BeerListItem(
                                        beer = beer,
                                        onClick = { onBeerClick(beer.id) },
                                        modifier = Modifier.padding(
                                            vertical = BeerTrackerSpacing.xSmall,
                                        ),
                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                                    )
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
private fun FilterRow(
    state: OverviewUiState.Content,
    onToggleBuyAgain: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleNotTried: () -> Unit,
    onToggleType: (String) -> Unit,
    onSort: (BeerSort) -> Unit,
) {
    var typeMenuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = BeerTrackerSpacing.large, vertical = BeerTrackerSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.filter.buyAgainOnly,
            onClick = onToggleBuyAgain,
            label = { Text(stringResource(R.string.buy_again)) },
            leadingIcon = selectedIcon(state.filter.buyAgainOnly),
        )
        FilterChip(
            selected = state.filter.favouritesOnly,
            onClick = onToggleFavourite,
            label = { Text(stringResource(R.string.favourites)) },
            leadingIcon = selectedIcon(state.filter.favouritesOnly),
        )
        FilterChip(
            selected = state.filter.notTriedOnly,
            onClick = onToggleNotTried,
            label = { Text(stringResource(R.string.not_tried)) },
            leadingIcon = selectedIcon(state.filter.notTriedOnly),
        )
        Box {
            FilterChip(
                selected = state.filter.types.isNotEmpty(),
                onClick = { typeMenuOpen = true },
                label = {
                    Text(
                        if (state.filter.types.isEmpty()) {
                            stringResource(R.string.type)
                        } else {
                            stringResource(R.string.type_count, state.filter.types.size)
                        },
                    )
                },
                leadingIcon = selectedIcon(state.filter.types.isNotEmpty()),
                trailingIcon = {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                },
            )
            DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                if (state.availableTypes.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.no_types)) },
                        onClick = {},
                        enabled = false,
                    )
                }
                state.availableTypes.forEach { type ->
                    val isSelected = type in state.filter.types
                    DropdownMenuItem(
                        modifier = Modifier.semantics { selected = isSelected },
                        text = { Text(type) },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        } else {
                            null
                        },
                        onClick = { onToggleType(type) },
                    )
                }
            }
        }
        Box {
            TextButton(onClick = { sortMenuOpen = true }) {
                Text(stringResource(R.string.sort_by, sortLabel(state.sort)))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                BeerSort.entries.forEach { sort ->
                    val isSelected = sort == state.sort
                    DropdownMenuItem(
                        modifier = Modifier.semantics { selected = isSelected },
                        text = { Text(sortLabel(sort)) },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        } else {
                            null
                        },
                        onClick = {
                            onSort(sort)
                            sortMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun selectedIcon(selected: Boolean): (@Composable () -> Unit)? =
    if (selected) {
        { Icon(Icons.Filled.Check, contentDescription = null) }
    } else {
        null
    }

@Composable
private fun sortLabel(sort: BeerSort): String = stringResource(
    when (sort) {
        BeerSort.GRADE -> R.string.sort_grade
        BeerSort.PRICE -> R.string.sort_price
        BeerSort.NAME_BREWERY -> R.string.sort_name
        BeerSort.DATE_ADDED -> R.string.sort_newest
    },
)
