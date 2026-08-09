package com.beertracker.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.beertracker.R
import com.beertracker.domain.CatalogProduct
import com.beertracker.ui.components.EmptyState
import com.beertracker.ui.components.GradeMark
import com.beertracker.ui.components.LoadingState
import com.beertracker.ui.components.beerListSubtitle
import com.beertracker.ui.theme.BeerTrackerSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogBrowserScreen(
    viewModel: CatalogBrowserViewModel,
    onAddProduct: (String) -> Unit,
    onOpenBeer: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.catalog_title)) },
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
            CatalogBrowserUiState.Loading -> {
                LoadingState(
                    label = stringResource(R.string.catalog_loading),
                    modifier = Modifier.padding(padding),
                )
            }
            is CatalogBrowserUiState.Content -> {
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    OutlinedTextField(
                        value = current.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = BeerTrackerSpacing.large),
                        label = { Text(stringResource(R.string.catalog_search_label)) },
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = if (current.query.isNotEmpty()) {
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
                    when (current.emptyState) {
                        CatalogBrowserEmptyState.EMPTY_CATALOG -> {
                            EmptyState(
                                title = stringResource(R.string.catalog_empty_title),
                                message = stringResource(R.string.catalog_empty_message),
                                actionLabel = stringResource(R.string.back),
                                onAction = onBack,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        CatalogBrowserEmptyState.NO_RESULTS -> {
                            EmptyState(
                                title = stringResource(R.string.no_results_title),
                                message = stringResource(R.string.catalog_no_results_message),
                                actionLabel = stringResource(R.string.clear_search),
                                onAction = { viewModel.setQuery("") },
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
                                        onClick = {
                                            val beerId = row.triedBeerId
                                            if (beerId != null) {
                                                onOpenBeer(beerId)
                                            } else {
                                                onAddProduct(row.product.articleNumber)
                                            }
                                        },
                                        modifier = Modifier.padding(
                                            vertical = BeerTrackerSpacing.xSmall,
                                        ),
                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
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
private fun CatalogListItem(
    row: CatalogRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val product = row.product
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = product.displayImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = beerListSubtitle(product.brewery, product.type)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = catalogItemMeta(product)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (row.triedBeerId != null) {
            GradeMark(grade = row.grade, tried = row.tried, size = 40.dp)
        }
    }
}

internal fun catalogItemMeta(product: CatalogProduct): String = listOfNotNull(
    product.price?.let { "$it kr" },
    product.volumeMl?.let { "$it ml" },
    product.alcoholPercent?.let { "$it %" },
).joinToString(", ")
