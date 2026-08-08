package com.beertracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beertracker.R
import com.beertracker.ui.components.ErrorState
import com.beertracker.ui.components.FlagToggleRow
import com.beertracker.ui.components.GradeMark
import com.beertracker.ui.components.LoadingState
import com.beertracker.ui.components.SectionHeader
import com.beertracker.ui.theme.BeerTrackerSpacing

internal enum class BackNavigationAction {
    BLOCK,
    CONFIRM_DISCARD,
    NAVIGATE,
}

internal fun backNavigationAction(
    isSaving: Boolean,
    hasUnsavedChanges: Boolean,
): BackNavigationAction = when {
    isSaving -> BackNavigationAction.BLOCK
    hasUnsavedChanges -> BackNavigationAction.CONFIRM_DISCARD
    else -> BackNavigationAction.NAVIGATE
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(
    viewModel: AddEditBeerViewModel,
    beerId: String?,
    prefillArticle: String? = null,
    onDone: () -> Unit,
) {
    LaunchedEffect(beerId) {
        if (beerId != null) viewModel.load(beerId)
    }
    LaunchedEffect(prefillArticle) {
        if (beerId == null && prefillArticle != null) {
            viewModel.prefillFromCatalog(prefillArticle)
        }
    }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val typeOptions by viewModel.typeOptions.collectAsStateWithLifecycle()
    val pairingOptions by viewModel.pairingOptions.collectAsStateWithLifecycle()
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.save_error_message)

    LaunchedEffect(form.saved) {
        if (form.saved) onDone()
    }
    LaunchedEffect(form.saveState) {
        if (form.saveState is SaveState.Error) {
            snackbarHostState.showSnackbar(saveErrorMessage)
        }
    }

    val isSaving = form.saveState == SaveState.Saving
    fun requestBack() {
        when (backNavigationAction(isSaving, form.hasUnsavedChanges)) {
            BackNavigationAction.BLOCK -> Unit
            BackNavigationAction.CONFIRM_DISCARD -> showDiscardDialog = true
            BackNavigationAction.NAVIGATE -> onDone()
        }
    }
    BackHandler(enabled = isSaving || form.hasUnsavedChanges, onBack = ::requestBack)

    val loadState = if (
        beerId != null &&
        form.id != beerId &&
        form.loadState == EditLoadState.Content
    ) {
        EditLoadState.Loading
    } else {
        form.loadState
    }
    val busy = loadState == EditLoadState.Loading || isSaving
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (beerId == null) R.string.add_beer else R.string.edit_beer))
                },
                navigationIcon = {
                    IconButton(
                        onClick = ::requestBack,
                        enabled = !isSaving,
                    ) {
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
        bottomBar = {
            if (loadState == EditLoadState.Content) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    Button(
                        onClick = viewModel::save,
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = BeerTrackerSpacing.large,
                                end = BeerTrackerSpacing.large,
                                top = BeerTrackerSpacing.small,
                                bottom = BeerTrackerSpacing.large,
                            ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Text(
                            stringResource(
                                if (isSaving) R.string.saving else R.string.save,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (loadState) {
            EditLoadState.Loading -> {
                LoadingState(
                    label = stringResource(R.string.load_beer),
                    modifier = Modifier.padding(padding),
                )
            }
            EditLoadState.NotFound -> {
                ErrorState(
                    title = stringResource(R.string.beer_not_found_title),
                    message = stringResource(R.string.beer_not_found_message),
                    actionLabel = stringResource(R.string.back),
                    onAction = onDone,
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
            }
            is EditLoadState.Error -> {
                ErrorState(
                    title = stringResource(R.string.load_error_title),
                    message = stringResource(R.string.load_error_message),
                    actionLabel = stringResource(R.string.back),
                    onAction = onDone,
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
            }
            EditLoadState.Content -> {
                BeerForm(
                    form = form,
                    typeOptions = typeOptions,
                    pairingOptions = pairingOptions,
                    enabled = !busy,
                    onUpdate = viewModel::update,
                    onSetTried = viewModel::setTried,
                    onSetGrade = viewModel::setGrade,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (showDiscardDialog && !isSaving) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onDone()
                }) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BeerForm(
    form: BeerFormState,
    typeOptions: List<String>,
    pairingOptions: List<String>,
    enabled: Boolean,
    onUpdate: ((BeerFormState) -> BeerFormState) -> Unit,
    onSetTried: (Boolean) -> Unit,
    onSetGrade: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BeerTrackerSpacing.large, vertical = BeerTrackerSpacing.small),
        verticalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
    ) {
        SectionHeader(stringResource(R.string.basics_section))
        OutlinedTextField(
            value = form.name,
            onValueChange = { value -> onUpdate { it.copy(name = value, nameError = false) } },
            label = { Text(stringResource(R.string.name_label)) },
            isError = form.nameError,
            supportingText = {
                if (form.nameError) Text(stringResource(R.string.name_required))
            },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ResponsiveFieldPair(
            first = {
                OutlinedTextField(
                    value = form.brewery,
                    onValueChange = { value -> onUpdate { it.copy(brewery = value) } },
                    label = { Text(stringResource(R.string.brewery_label)) },
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            second = {
                OutlinedTextField(
                    value = form.type,
                    onValueChange = { value -> onUpdate { it.copy(type = value) } },
                    label = { Text(stringResource(R.string.type_label)) },
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small)) {
            typeOptions.forEach { option ->
                SuggestionChip(
                    onClick = { onUpdate { it.copy(type = option) } },
                    label = { Text(option) },
                    enabled = enabled,
                )
            }
        }
        ResponsiveFieldPair(
            first = {
                NumericField(
                    value = form.alcoholPercent,
                    onValueChange = { value -> onUpdate { it.copy(alcoholPercent = value) } },
                    label = stringResource(R.string.alcohol_label),
                    error = form.alcoholError,
                    errorText = stringResource(R.string.alcohol_error),
                    keyboardType = KeyboardType.Decimal,
                    enabled = enabled,
                )
            },
            second = {
                NumericField(
                    value = form.volumeMl,
                    onValueChange = { value -> onUpdate { it.copy(volumeMl = value) } },
                    label = stringResource(R.string.volume_label),
                    error = form.volumeError,
                    errorText = stringResource(R.string.volume_error),
                    keyboardType = KeyboardType.Number,
                    enabled = enabled,
                )
            },
        )
        NumericField(
            value = form.price,
            onValueChange = { value -> onUpdate { it.copy(price = value) } },
            label = stringResource(R.string.price_label),
            error = form.priceError,
            errorText = stringResource(R.string.price_error),
            keyboardType = KeyboardType.Decimal,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader(
            title = stringResource(R.string.tasting_section),
            supportingText = stringResource(R.string.grade_scale_help),
            modifier = Modifier.padding(top = BeerTrackerSpacing.large),
        )
        FlagToggleRow(
            label = stringResource(R.string.tried),
            checked = form.tried,
            onCheckedChange = onSetTried,
            supportingText = stringResource(R.string.tried_help),
            enabled = enabled,
        )
        Text(
            stringResource(R.string.grade_scale_title),
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small),
            verticalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small),
        ) {
            (5..10).forEach { grade ->
                val selected = form.grade == grade
                GradeMark(
                    grade = grade,
                    tried = true,
                    modifier = Modifier
                        .clip(CircleShape)
                        .then(
                            if (selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSetGrade(if (selected) null else grade) },
                        ),
                    size = 48.dp,
                )
            }
        }
        if (form.gradeError) {
            Text(
                stringResource(R.string.grade_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedTextField(
            value = form.note,
            onValueChange = { value -> onUpdate { it.copy(note = value) } },
            label = { Text(stringResource(R.string.note_label)) },
            enabled = enabled,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.aftertaste,
            onValueChange = { value -> onUpdate { it.copy(aftertaste = value) } },
            label = { Text(stringResource(R.string.aftertaste_label)) },
            enabled = enabled,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader(
            stringResource(R.string.pairings_section),
            modifier = Modifier.padding(top = BeerTrackerSpacing.large),
        )
        Text(stringResource(R.string.goes_well_with), style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small),
            verticalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small),
        ) {
            pairingOptions.forEach { option ->
                val selected = option in form.pairings
                androidx.compose.material3.FilterChip(
                    selected = selected,
                    onClick = {
                        onUpdate {
                            it.copy(
                                pairings = if (selected) {
                                    it.pairings - option
                                } else {
                                    it.pairings + option
                                },
                            )
                        }
                    },
                    label = { Text(option) },
                    enabled = enabled,
                )
            }
        }
        OutlinedTextField(
            value = form.customPairing,
            onValueChange = { value -> onUpdate { it.copy(customPairing = value) } },
            label = { Text(stringResource(R.string.other_pairing)) },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader(
            stringResource(R.string.keep_section),
            modifier = Modifier.padding(top = BeerTrackerSpacing.large),
        )
        FlagToggleRow(
            label = stringResource(R.string.buy_again),
            checked = form.buyAgain,
            onCheckedChange = { value -> onUpdate { it.copy(buyAgain = value) } },
            enabled = enabled,
        )
        FlagToggleRow(
            label = stringResource(R.string.favourite),
            checked = form.favourite,
            onCheckedChange = { value -> onUpdate { it.copy(favourite = value) } },
            enabled = enabled,
        )
    }
}

@Composable
private fun ResponsiveFieldPair(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 480.dp) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) { first() }
                Column(Modifier.weight(1f)) { second() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.medium)) {
                first()
                second()
            }
        }
    }
}

@Composable
private fun NumericField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: Boolean,
    errorText: String,
    keyboardType: KeyboardType,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error,
        supportingText = { if (error) Text(errorText) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        enabled = enabled,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}
