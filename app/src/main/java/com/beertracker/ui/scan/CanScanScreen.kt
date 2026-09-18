package com.beertracker.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beertracker.R
import com.beertracker.ui.components.CatalogListItem
import com.beertracker.ui.components.CatalogRow
import com.beertracker.ui.components.ErrorState
import com.beertracker.ui.components.SectionHeader
import com.beertracker.ui.components.beerListSubtitle
import com.beertracker.ui.theme.BeerTrackerSpacing

/**
 * The can scan mode: live camera, on-device text recognition, and a list
 * of catalog beers whose name and brewery match what was read. Picking an
 * unlogged beer goes to the prefilled add form; a logged one opens its
 * detail screen; "Add manually" carries the best name guess into an empty
 * form.
 */
@Composable
fun CanScanScreen(
    viewModel: CanScanViewModel,
    onAddProduct: (String) -> Unit,
    onOpenBeer: (String) -> Unit,
    onAddManually: (String?) -> Unit,
    onSwitchToShelfLabel: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberCameraPermission()

    CanScanContent(
        state = state,
        permission = permission,
        onPickRow = { row ->
            val beerId = row.triedBeerId
            if (beerId != null) onOpenBeer(beerId) else onAddProduct(row.product.articleNumber)
        },
        onStartOver = viewModel::startOver,
        onAddManually = { onAddManually(state.guessedName) },
        onSwitchToShelfLabel = onSwitchToShelfLabel,
        onBack = onBack,
        cameraPreview = {
            TextRecognitionCameraPreview(onTextDetected = viewModel::onTextDetected)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CanScanContent(
    state: CanScanUiState,
    permission: CameraPermission,
    onPickRow: (CatalogRow) -> Unit,
    onStartOver: () -> Unit,
    onAddManually: () -> Unit,
    onSwitchToShelfLabel: () -> Unit,
    onBack: () -> Unit,
    cameraPreview: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BeerTrackerSpacing.large),
            verticalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
        ) {
            ScanModeSwitch(
                selected = ScanMode.CAN,
                onSelect = { if (it == ScanMode.SHELF_LABEL) onSwitchToShelfLabel() },
            )
            when (permission) {
                CameraPermission.GRANTED -> {
                    cameraPreview()
                    Text(
                        stringResource(R.string.can_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MatchesSection(state = state, onPickRow = onPickRow)
                }
                CameraPermission.DENIED -> {
                    ErrorState(
                        title = stringResource(R.string.camera_denied_title),
                        message = stringResource(R.string.can_camera_denied_message),
                    )
                }
                CameraPermission.UNKNOWN -> {
                    Text(
                        stringResource(R.string.camera_waiting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = BeerTrackerSpacing.small),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = BeerTrackerSpacing.section),
                horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
            ) {
                if (permission == CameraPermission.GRANTED) {
                    OutlinedButton(
                        onClick = onStartOver,
                        enabled = state.hasText,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.can_start_over))
                    }
                }
                Button(
                    onClick = onAddManually,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.can_add_manually))
                }
            }
        }
    }
}

/**
 * The candidate list. A plain Column rather than a LazyColumn because the
 * screen already scrolls and there are at most five rows.
 */
@Composable
private fun MatchesSection(
    state: CanScanUiState,
    onPickRow: (CatalogRow) -> Unit,
) {
    val supporting = when {
        state.matches.isNotEmpty() -> null
        state.hasText -> stringResource(R.string.can_no_match)
        else -> stringResource(R.string.can_nothing_read)
    }
    SectionHeader(
        title = stringResource(R.string.can_matches_section),
        supportingText = supporting,
        modifier = Modifier.padding(top = BeerTrackerSpacing.small),
    )
    state.matches.forEach { row ->
        CatalogListItem(
            row = row,
            subtitle = beerListSubtitle(row.product.brewery, row.product.type),
            onClick = { onPickRow(row) },
            modifier = Modifier.padding(vertical = BeerTrackerSpacing.xSmall),
        )
    }
}
