package com.beertracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.beertracker.R
import com.beertracker.domain.TriedBeer
import com.beertracker.ui.components.ErrorState
import com.beertracker.ui.components.FlagToggleRow
import com.beertracker.ui.components.GradeMark
import com.beertracker.ui.components.LoadingState
import com.beertracker.ui.components.PairingRow
import com.beertracker.ui.components.SectionHeader
import com.beertracker.ui.theme.BeerTrackerSpacing
import com.beertracker.ui.theme.BeerTrackerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val beer = (state as? DetailUiState.Content)?.beer

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (beer != null) {
                        IconButton(onClick = { onEdit(beer.id) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.edit),
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when (val current = state) {
            DetailUiState.Loading -> {
                LoadingState(
                    label = stringResource(R.string.load_beer),
                    modifier = Modifier.padding(padding),
                )
            }
            DetailUiState.NotFound -> {
                ErrorState(
                    title = stringResource(R.string.beer_not_found_title),
                    message = stringResource(R.string.beer_not_found_message),
                    actionLabel = stringResource(R.string.back),
                    onAction = onBack,
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
            }
            is DetailUiState.Error -> {
                ErrorState(
                    title = stringResource(R.string.detail_error_title),
                    message = stringResource(R.string.detail_error_message),
                    actionLabel = stringResource(R.string.back),
                    onAction = onBack,
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
            }
            is DetailUiState.Content -> {
                DetailContent(
                    beer = current.beer,
                    onToggleFavourite = viewModel::toggleFavourite,
                    onToggleBuyAgain = viewModel::toggleBuyAgain,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (showDeleteDialog && beer != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_title, beer.name)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete(onDeleted = onBack)
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun DetailContent(
    beer: TriedBeer,
    onToggleFavourite: () -> Unit,
    onToggleBuyAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = BeerTrackerSpacing.large,
                end = BeerTrackerSpacing.large,
                bottom = BeerTrackerSpacing.section,
            ),
        verticalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
    ) {
        if (beer.imageUrl != null) {
            AsyncImage(
                model = beer.imageUrl,
                contentDescription = stringResource(R.string.beer_image_description, beer.name),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(beer.name, style = MaterialTheme.typography.displaySmall)
                if (beer.brewery.isNotBlank()) {
                    Text(
                        beer.brewery,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (beer.type.isNotBlank()) {
                    Text(
                        beer.type,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            GradeMark(grade = beer.grade, tried = beer.tried, size = 64.dp)
        }

        Column(
            modifier = Modifier.padding(top = BeerTrackerSpacing.small),
        ) {
            FlagToggleRow(
                label = stringResource(R.string.favourite),
                checked = beer.favourite,
                onCheckedChange = { onToggleFavourite() },
            )
            FlagToggleRow(
                label = stringResource(R.string.buy_again),
                checked = beer.buyAgain,
                onCheckedChange = { onToggleBuyAgain() },
            )
        }

        SectionHeader(
            stringResource(R.string.specifications_section),
            modifier = Modifier.padding(top = BeerTrackerSpacing.large),
        )
        beer.alcoholPercent?.let {
            InfoRow(
                stringResource(R.string.alcohol),
                stringResource(R.string.alcohol_value, it.toString()),
            )
        }
        beer.volumeMl?.let {
            InfoRow(
                stringResource(R.string.volume),
                stringResource(R.string.volume_value, it),
            )
        }
        beer.price?.let {
            InfoRow(
                stringResource(R.string.price),
                stringResource(R.string.price_value, it.toString()),
            )
        }
        val date = Instant.ofEpochMilli(beer.dateAdded)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        InfoRow(stringResource(R.string.added), date)

        SectionHeader(
            stringResource(R.string.tasting_notes_section),
            modifier = Modifier.padding(top = BeerTrackerSpacing.large),
        )
        DetailText(
            value = beer.note,
            emptyText = stringResource(R.string.no_tasting_notes),
        )

        SectionHeader(
            stringResource(R.string.aftertaste_section),
            modifier = Modifier.padding(top = BeerTrackerSpacing.large),
        )
        DetailText(
            value = beer.aftertaste,
            emptyText = stringResource(R.string.no_aftertaste),
        )

        SectionHeader(
            stringResource(R.string.pairings_section),
            modifier = Modifier.padding(top = BeerTrackerSpacing.large),
        )
        PairingRow(
            pairings = beer.goesWellWith,
            emptyText = stringResource(R.string.no_pairings),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun DetailText(value: String, emptyText: String) {
    Text(
        text = value.ifBlank { emptyText },
        style = MaterialTheme.typography.bodyLarge,
        color = if (value.isBlank()) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DetailContentPreview() {
    BeerTrackerTheme {
        DetailContent(
            beer = TriedBeer(
                id = "preview",
                name = "Cellar Reserve",
                brewery = "Nordic Field Brewery",
                type = "Dark lager",
                alcoholPercent = 5.8,
                volumeMl = 330,
                price = 29.9,
                grade = 4,
                tried = true,
                note = "Toasted malt, dried fruit, and a clean finish.",
                aftertaste = "Long and gently bitter.",
                goesWellWith = listOf("Mushroom stew", "Hard cheese"),
                buyAgain = true,
                favourite = true,
                dateAdded = 0,
                catalogArticleNumber = null,
                addedBy = null,
                imageUrl = null,
            ),
            onToggleFavourite = {},
            onToggleBuyAgain = {},
        )
    }
}
