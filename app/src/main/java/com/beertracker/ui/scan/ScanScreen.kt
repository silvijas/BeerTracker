package com.beertracker.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beertracker.R
import com.beertracker.ui.components.ErrorState
import com.beertracker.ui.components.SectionHeader
import com.beertracker.ui.theme.BeerTrackerSpacing

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onFound: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberCameraPermission()
    LaunchedEffect(state) {
        val found = state as? ScanUiState.Found ?: return@LaunchedEffect
        onFound(found.product.articleNumber)
    }
    var manualInput by rememberSaveable { mutableStateOf("") }

    ScanContent(
        state = state,
        permission = permission,
        manualInput = manualInput,
        onManualInputChange = { manualInput = it },
        onManualLookup = { viewModel.onManualLookup(manualInput) },
        onScanAgain = viewModel::scanAgain,
        onBack = onBack,
        cameraPreview = {
            TextRecognitionCameraPreview(onTextDetected = viewModel::onTextDetected)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanContent(
    state: ScanUiState,
    permission: CameraPermission,
    manualInput: String,
    onManualInputChange: (String) -> Unit,
    onManualLookup: () -> Unit,
    onScanAgain: () -> Unit,
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
            when (permission) {
                CameraPermission.GRANTED -> {
                    cameraPreview()
                    Text(
                        stringResource(R.string.scan_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CameraPermission.DENIED -> {
                    ErrorState(
                        title = stringResource(R.string.camera_denied_title),
                        message = stringResource(R.string.camera_denied_message),
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

            SectionHeader(
                title = stringResource(R.string.manual_lookup_section),
                supportingText = stringResource(R.string.manual_lookup_help),
                modifier = Modifier.padding(top = BeerTrackerSpacing.small),
            )
            OutlinedTextField(
                value = manualInput,
                onValueChange = onManualInputChange,
                label = { Text(stringResource(R.string.article_number_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onManualLookup,
                enabled = manualInput.isNotBlank() && state != ScanUiState.Searching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.look_up))
            }

            when (state) {
                ScanUiState.Searching -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(stringResource(R.string.searching_catalog))
                    }
                }
                is ScanUiState.NotFound -> {
                    ErrorState(
                        title = stringResource(R.string.scan_not_found_title),
                        message = stringResource(R.string.scan_not_found_message, state.number),
                        actionLabel = stringResource(R.string.scan_again),
                        onAction = onScanAgain,
                    )
                }
                is ScanUiState.Found -> {
                    Text(
                        stringResource(R.string.scan_found, state.product.name),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                ScanUiState.Idle -> Unit
            }
        }
    }
}
