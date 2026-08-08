package com.beertracker.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beertracker.R
import com.beertracker.ui.components.ErrorState
import com.beertracker.ui.components.SectionHeader
import com.beertracker.ui.theme.BeerTrackerSpacing
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

internal enum class CameraPermission { UNKNOWN, GRANTED, DENIED }

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onFound: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permission by rememberSaveable {
        mutableStateOf(
            if (hasCameraPermission(context)) CameraPermission.GRANTED else CameraPermission.UNKNOWN,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permission = if (granted) CameraPermission.GRANTED else CameraPermission.DENIED
    }
    LaunchedEffect(Unit) {
        if (permission == CameraPermission.UNKNOWN) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
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
            CameraPreviewSection(onTextDetected = viewModel::onTextDetected)
        },
    )
}

internal fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

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

@Composable
private fun CameraPreviewSection(onTextDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(MaterialTheme.shapes.large),
    )

    DisposableEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val analyzer = LabelAnalyzer(onTextDetected)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            provider = cameraProvider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(mainExecutor, analyzer) }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }, mainExecutor)
        onDispose {
            analyzer.close()
            provider?.unbindAll()
        }
    }
}

/**
 * Runs ML Kit text recognition on camera frames. KEEP_ONLY_LATEST plus
 * closing the frame only when recognition completes gives natural
 * backpressure: a new frame is analyzed only when the previous one is done.
 * Deduplication of repeated numbers happens in ScanViewModel.
 */
private class LabelAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { result -> onText(result.text) }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        recognizer.close()
    }
}
