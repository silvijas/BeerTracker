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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

internal enum class CameraPermission { UNKNOWN, GRANTED, DENIED }

internal fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/**
 * The camera permission as the scan screens see it: already granted,
 * denied, or not yet answered. Asks once on first composition when the
 * answer is unknown. Survives configuration changes through
 * rememberSaveable, so rotating the phone does not ask again.
 */
@Composable
internal fun rememberCameraPermission(): CameraPermission {
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
    return permission
}

/**
 * A live back-camera preview whose frames run through on-device ML Kit
 * text recognition. Every recognized frame's full text goes to
 * [onTextDetected]; deduplication and interpretation belong to the caller.
 * Shared by the shelf-label and can scan screens.
 */
@Composable
internal fun TextRecognitionCameraPreview(onTextDetected: (String) -> Unit) {
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
        val analyzer = TextRecognitionAnalyzer(onTextDetected)
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
 */
private class TextRecognitionAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {

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
