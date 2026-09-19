package com.beertracker.ui.sync

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beertracker.R
import com.beertracker.domain.InviteCodes
import com.beertracker.domain.SyncStatus
import com.beertracker.ui.components.ErrorState
import com.beertracker.ui.components.SectionHeader
import com.beertracker.ui.theme.BeerTrackerSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SyncScreen(
    viewModel: SyncViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.sync_share_code)
    SyncContent(
        state = state,
        onCodeChange = viewModel::setCodeInput,
        onCreate = viewModel::createCellar,
        onJoin = viewModel::join,
        onStopSyncing = viewModel::stopSyncing,
        onShare = { text ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, shareTitle))
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SyncContent(
    state: SyncUiState,
    onCodeChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onStopSyncing: () -> Unit,
    onShare: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showStopDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
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
            when (val status = state.status) {
                SyncStatus.Unavailable -> ErrorState(
                    title = stringResource(R.string.sync_unavailable_title),
                    message = stringResource(R.string.sync_unavailable_message),
                )
                SyncStatus.NotPaired -> NotPairedContent(
                    state = state,
                    onCodeChange = onCodeChange,
                    onCreate = onCreate,
                    onJoin = onJoin,
                )
                is SyncStatus.Paired -> PairedContent(
                    status = status,
                    onShare = onShare,
                    onStopClick = { showStopDialog = true },
                )
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text(stringResource(R.string.sync_stop_title)) },
            text = { Text(stringResource(R.string.sync_stop_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopDialog = false
                        onStopSyncing()
                    },
                ) {
                    Text(stringResource(R.string.sync_stop_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun NotPairedContent(
    state: SyncUiState,
    onCodeChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    Text(
        stringResource(R.string.sync_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = onCreate,
        enabled = !state.working,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.sync_create))
    }
    SectionHeader(
        title = stringResource(R.string.sync_join_section),
        supportingText = stringResource(R.string.sync_join_help),
        modifier = Modifier.padding(top = BeerTrackerSpacing.large),
    )
    OutlinedTextField(
        value = state.codeInput,
        onValueChange = onCodeChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.working,
        label = { Text(stringResource(R.string.sync_code_label)) },
        placeholder = { Text(stringResource(R.string.sync_code_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        shape = MaterialTheme.shapes.medium,
    )
    Button(
        onClick = onJoin,
        enabled = !state.working && state.codeInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.sync_join))
    }
    if (state.working) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    state.error?.let { error ->
        Text(
            text = errorText(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PairedContent(
    status: SyncStatus.Paired,
    onShare: (String) -> Unit,
    onStopClick: () -> Unit,
) {
    val formattedCode = InviteCodes.format(status.inviteCode)
    val shareText = stringResource(R.string.sync_share_text, formattedCode)
    SectionHeader(
        title = stringResource(R.string.sync_code_section),
        supportingText = stringResource(R.string.sync_code_help),
    )
    Text(
        text = formattedCode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BeerTrackerSpacing.medium),
        style = MaterialTheme.typography.displaySmall,
        letterSpacing = 4.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
    )
    OutlinedButton(
        onClick = { onShare(shareText) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.sync_share_code))
    }
    SectionHeader(
        title = stringResource(R.string.sync_status_section),
        modifier = Modifier.padding(top = BeerTrackerSpacing.large),
    )
    Text(
        text = when (val count = status.memberCount) {
            null -> stringResource(R.string.sync_members_connecting)
            0, 1 -> stringResource(R.string.sync_members_alone)
            else -> stringResource(R.string.sync_members_count, count)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = status.lastSyncedUtc?.let { stringResource(R.string.sync_last_synced, formatSyncTime(it)) }
            ?: stringResource(R.string.sync_waiting_first),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (status.hasPendingUploads) {
        Text(
            text = stringResource(R.string.sync_pending_uploads),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TextButton(
        onClick = onStopClick,
        modifier = Modifier.padding(top = BeerTrackerSpacing.large),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(stringResource(R.string.sync_stop))
    }
}

@Composable
private fun errorText(error: SyncError): String = stringResource(
    when (error) {
        SyncError.INVALID_CODE -> R.string.sync_error_invalid_code
        SyncError.UNKNOWN_CODE -> R.string.sync_error_unknown_code
        SyncError.OFFLINE -> R.string.sync_error_offline
        SyncError.UNAVAILABLE -> R.string.sync_error_unavailable
        SyncError.FAILED -> R.string.sync_error_failed
    },
)

private fun formatSyncTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
