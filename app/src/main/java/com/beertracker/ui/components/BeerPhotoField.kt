package com.beertracker.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.beertracker.R
import com.beertracker.data.BeerPhotoStore
import com.beertracker.ui.theme.BeerTrackerSpacing
import java.io.File

/**
 * The beer's picture on the add/edit form, plus the two ways to replace it
 * with one of your own. The camera writes straight into app private storage
 * through a FileProvider URI; the photo picker's result is copied in, so
 * deleting the original from the gallery can never blank the beer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeerPhotoField(
    imageUrl: String?,
    hasPhoto: Boolean,
    enabled: Boolean,
    photoStore: BeerPhotoStore,
    onPhotoPicked: (String) -> Unit,
    onRemovePhoto: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val file = pendingCameraFile
        pendingCameraFile = null
        when {
            file == null -> Unit
            saved && file.length() > 0L -> onPhotoPicked(photoStore.uriFor(file))
            // A cancelled shot leaves behind the empty file we created.
            else -> file.delete()
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { picked: Uri? ->
        if (picked != null) {
            val stream = runCatching { context.contentResolver.openInputStream(picked) }.getOrNull()
            if (stream == null) {
                onError()
            } else {
                runCatching { photoStore.save(stream) }
                    .onSuccess(onPhotoPicked)
                    .onFailure { onError() }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl == null) {
                BeerCan(filled = false, height = 72.dp, alpha = 0.6f)
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(BeerTrackerSpacing.small)) {
            OutlinedButton(
                enabled = enabled,
                onClick = {
                    val file = photoStore.newPhotoFile()
                    pendingCameraFile = file
                    cameraLauncher.launch(
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.photos",
                            file,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.take_photo))
            }
            OutlinedButton(
                enabled = enabled,
                onClick = {
                    pickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                Text(stringResource(R.string.choose_photo))
            }
            if (hasPhoto) {
                TextButton(enabled = enabled, onClick = onRemovePhoto) {
                    Text(stringResource(R.string.remove_photo))
                }
            }
        }
    }
}
