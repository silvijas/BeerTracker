package com.beertracker.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beertracker.R
import com.beertracker.domain.Pairing

/**
 * One flat silhouette per [Pairing], in the spirit of the food pairing
 * symbols on systembolaget.se but drawn here rather than copied. Each is a
 * single black-filled 24 by 24 vector, tinted by the caller so it follows
 * the theme like every other icon in the app.
 */

internal val PairingPorkIcon: ImageVector = ImageVector.Builder(
    name = "PairingPork",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(4.6f, 13f)
        curveTo(4.6f, 9.5f, 8f, 7.1f, 12f, 7.1f)
        curveTo(16.5f, 7.1f, 20f, 9.6f, 20f, 13f)
        curveTo(20f, 14.6f, 19.3f, 16f, 18.1f, 17f)
        lineTo(18.1f, 19.6f)
        lineTo(16.1f, 19.6f)
        lineTo(16.1f, 17.9f)
        curveTo(15.3f, 18.1f, 14.4f, 18.2f, 13.5f, 18.2f)
        lineTo(10.6f, 18.2f)
        curveTo(9.8f, 18.2f, 9.1f, 18.1f, 8.4f, 18f)
        lineTo(8.4f, 19.6f)
        lineTo(6.4f, 19.6f)
        lineTo(6.4f, 17.2f)
        curveTo(5.3f, 16.2f, 4.6f, 14.7f, 4.6f, 13f)
        close()
        moveTo(8.6f, 6.4f)
        lineTo(11.4f, 5.3f)
        lineTo(11f, 8.5f)
        close()
        moveTo(2.2f, 12.6f)
        arcTo(2f, 2f, 0f, true, true, 6.2f, 12.6f)
        arcTo(2f, 2f, 0f, true, true, 2.2f, 12.6f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.3f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(20f, 10.8f)
        curveTo(21.6f, 10.4f, 22.2f, 9f, 21.2f, 8.2f)
        curveTo(20.5f, 7.6f, 19.6f, 8f, 19.8f, 8.8f)
    }
}.build()

internal val PairingPoultryIcon: ImageVector = ImageVector.Builder(
    name = "PairingPoultry",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(4f, 13.6f)
        curveTo(4f, 11f, 7f, 9.2f, 10.6f, 9.2f)
        lineTo(15.6f, 9.2f)
        curveTo(17.8f, 9.2f, 19.4f, 10.6f, 19.4f, 12.6f)
        curveTo(19.4f, 15f, 17.4f, 16.9f, 14.6f, 16.9f)
        lineTo(8.6f, 16.9f)
        curveTo(5.9f, 16.9f, 4f, 15.5f, 4f, 13.6f)
        close()
        moveTo(4.6f, 11.4f)
        lineTo(1.4f, 9.6f)
        lineTo(4.4f, 14.6f)
        close()
        moveTo(14.2f, 7.6f)
        curveTo(13.6f, 9f, 13.4f, 10f, 13.4f, 11.2f)
        lineTo(16.8f, 11.2f)
        curveTo(16.8f, 9.8f, 17f, 8.6f, 17.6f, 7.6f)
        close()
        moveTo(17.6f, 5f)
        lineTo(21.4f, 5.9f)
        lineTo(17.6f, 6.9f)
        close()
        moveTo(12.6f, 5.8f)
        arcTo(2.6f, 2.6f, 0f, true, true, 17.8f, 5.8f)
        arcTo(2.6f, 2.6f, 0f, true, true, 12.6f, 5.8f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.3f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(9.4f, 17f)
        lineTo(9.4f, 20.4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.3f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(13f, 17f)
        lineTo(13f, 20.4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.3f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(8f, 20.6f)
        lineTo(11f, 20.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.3f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(11.6f, 20.6f)
        lineTo(14.6f, 20.6f)
    }
}.build()

internal val PairingLambIcon: ImageVector = ImageVector.Builder(
    name = "PairingLamb",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(6.6f, 12.6f)
        arcTo(2.5f, 2.5f, 0f, false, true, 8.9f, 8.4f)
        arcTo(2.5f, 2.5f, 0f, false, true, 13f, 7.2f)
        arcTo(2.5f, 2.5f, 0f, false, true, 17.2f, 8.6f)
        arcTo(2.4f, 2.4f, 0f, false, true, 18.4f, 13.2f)
        arcTo(2.4f, 2.4f, 0f, false, true, 16.6f, 15.6f)
        lineTo(8.2f, 15.6f)
        arcTo(2.4f, 2.4f, 0f, false, true, 6.6f, 12.6f)
        close()
        moveTo(8.6f, 15.4f)
        lineTo(10.4f, 15.4f)
        lineTo(10.4f, 19.6f)
        lineTo(8.6f, 19.6f)
        close()
        moveTo(14.4f, 15.4f)
        lineTo(16.2f, 15.4f)
        lineTo(16.2f, 19.6f)
        lineTo(14.4f, 19.6f)
        close()
        moveTo(18f, 11.6f)
        curveTo(18f, 9.8f, 19.2f, 8.6f, 20.6f, 8.6f)
        curveTo(22f, 8.6f, 23f, 9.8f, 23f, 11.4f)
        curveTo(23f, 13.2f, 21.8f, 14.4f, 20.4f, 14.4f)
        curveTo(19f, 14.4f, 18f, 13.2f, 18f, 11.6f)
        close()
        moveTo(17.4f, 8.6f)
        arcTo(1.5f, 1.9f, -25f, true, true, 19.6f, 7.4f)
        arcTo(1.5f, 1.9f, -25f, true, true, 17.4f, 8.6f)
        close()
    }
}.build()

internal val PairingBeefIcon: ImageVector = ImageVector.Builder(
    name = "PairingBeef",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(6f, 9.4f)
        lineTo(16.4f, 9.4f)
        curveTo(18.2f, 9.4f, 19.6f, 10.8f, 19.6f, 12.6f)
        lineTo(19.6f, 14.4f)
        curveTo(19.6f, 16.2f, 18.2f, 17.6f, 16.4f, 17.6f)
        lineTo(16.2f, 17.6f)
        lineTo(16.2f, 20.6f)
        lineTo(14.4f, 20.6f)
        lineTo(14.4f, 17.6f)
        lineTo(8.6f, 17.6f)
        lineTo(8.6f, 20.6f)
        lineTo(6.8f, 20.6f)
        lineTo(6.8f, 17.6f)
        lineTo(6f, 17.6f)
        curveTo(4.2f, 17.6f, 2.8f, 16.2f, 2.8f, 14.4f)
        lineTo(2.8f, 12.6f)
        curveTo(2.8f, 10.8f, 4.2f, 9.4f, 6f, 9.4f)
        close()
        moveTo(19.4f, 10.8f)
        curveTo(21.2f, 10.8f, 22.6f, 12.2f, 22.6f, 14f)
        curveTo(22.6f, 15.8f, 21.2f, 17.2f, 19.4f, 17.2f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.3f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(19.6f, 11f)
        curveTo(19.6f, 9.4f, 20.4f, 8.2f, 21.6f, 7.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.3f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(22.4f, 11.4f)
        curveTo(23f, 10f, 22.8f, 8.6f, 22f, 7.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.3f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(2.8f, 11f)
        curveTo(1.6f, 10.4f, 1.2f, 9f, 1.6f, 7.6f)
    }
}.build()

internal val PairingGameIcon: ImageVector = ImageVector.Builder(
    name = "PairingGame",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(12f, 11.4f)
        curveTo(10f, 11.4f, 8.6f, 12.9f, 8.6f, 14.9f)
        curveTo(8.6f, 17.6f, 10.2f, 20.4f, 12f, 21.4f)
        curveTo(13.8f, 20.4f, 15.4f, 17.6f, 15.4f, 14.9f)
        curveTo(15.4f, 12.9f, 14f, 11.4f, 12f, 11.4f)
        close()
        moveTo(8.8f, 11.6f)
        arcTo(1.7f, 2.2f, 30f, true, true, 6.6f, 13.4f)
        arcTo(1.7f, 2.2f, 30f, true, true, 8.8f, 11.6f)
        close()
        moveTo(15.2f, 11.6f)
        arcTo(1.7f, 2.2f, -30f, true, false, 17.4f, 13.4f)
        arcTo(1.7f, 2.2f, -30f, true, false, 15.2f, 11.6f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.4f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(9.6f, 11.2f)
        lineTo(8f, 7.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.4f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(8f, 7.2f)
        lineTo(5.6f, 6.4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.4f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(8f, 7.2f)
        lineTo(8.8f, 4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.4f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(14.4f, 11.2f)
        lineTo(16f, 7.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.4f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(16f, 7.2f)
        lineTo(18.4f, 6.4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.4f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(16f, 7.2f)
        lineTo(15.2f, 4f)
    }
}.build()

internal val PairingFishIcon: ImageVector = ImageVector.Builder(
    name = "PairingFish",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.EvenOdd,
    ) {
        moveTo(1.8f, 12f)
        curveTo(4.8f, 7f, 10.8f, 5.8f, 15f, 8.8f)
        lineTo(19.2f, 5.8f)
        lineTo(18.2f, 12f)
        lineTo(19.2f, 18.2f)
        lineTo(15f, 15.2f)
        curveTo(10.8f, 18.2f, 4.8f, 17f, 1.8f, 12f)
        close()
        moveTo(5.2f, 10.8f)
        arcTo(1f, 1f, 0f, true, true, 7.2f, 10.8f)
        arcTo(1f, 1f, 0f, true, true, 5.2f, 10.8f)
        close()
    }
}.build()

internal val PairingShellfishIcon: ImageVector = ImageVector.Builder(
    name = "PairingShellfish",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(15.07f, 5.95f)
        arcTo(7.5f, 7.5f, 0f, true, true, 5f, 13f)
        lineTo(8.5f, 13f)
        arcTo(4f, 4f, 0f, true, false, 13.87f, 9.24f)
        close()
        moveTo(15.6f, 4.4f)
        lineTo(20.6f, 1.8f)
        lineTo(21.4f, 8.4f)
        lineTo(16.6f, 7.6f)
        close()
        moveTo(6f, 11.4f)
        arcTo(1f, 1f, 0f, true, true, 8f, 11.4f)
        arcTo(1f, 1f, 0f, true, true, 6f, 11.4f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.2f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(6.6f, 13.6f)
        curveTo(5f, 15f, 3.4f, 15.4f, 1.8f, 15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.2f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(7f, 14.8f)
        curveTo(6f, 16.4f, 4.6f, 17.4f, 2.8f, 17.6f)
    }
}.build()

internal val PairingVegetablesIcon: ImageVector = ImageVector.Builder(
    name = "PairingVegetables",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(11.9f, 22f)
        lineTo(7.6f, 11.6f)
        curveTo(10.2f, 10.2f, 13.6f, 10.2f, 16.2f, 11.6f)
        close()
        moveTo(12f, 9.8f)
        curveTo(12f, 7.4f, 13.6f, 5.6f, 16f, 5.2f)
        curveTo(16.4f, 7.8f, 15f, 9.6f, 12.6f, 10.2f)
        close()
        moveTo(11.4f, 9.6f)
        curveTo(10.6f, 7.6f, 11.2f, 5.4f, 13f, 4f)
        curveTo(14.4f, 6f, 14.2f, 8.2f, 12.6f, 9.8f)
        close()
        moveTo(10.8f, 10.2f)
        curveTo(9f, 9.4f, 7.8f, 7.6f, 7.8f, 5.4f)
        curveTo(10.2f, 5.8f, 11.6f, 7.4f, 11.8f, 9.6f)
        close()
    }
}.build()

internal val PairingCheeseIcon: ImageVector = ImageVector.Builder(
    name = "PairingCheese",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.EvenOdd,
    ) {
        moveTo(2.6f, 18.4f)
        lineTo(2.6f, 13.8f)
        curveTo(2.6f, 13.3f, 2.9f, 12.9f, 3.4f, 12.8f)
        lineTo(20f, 8.2f)
        curveTo(20.7f, 8f, 21.4f, 8.5f, 21.4f, 9.2f)
        lineTo(21.4f, 18.4f)
        curveTo(21.4f, 19f, 21f, 19.4f, 20.4f, 19.4f)
        lineTo(3.6f, 19.4f)
        curveTo(3f, 19.4f, 2.6f, 19f, 2.6f, 18.4f)
        close()
        moveTo(5.7f, 16f)
        arcTo(1.3f, 1.3f, 0f, true, true, 8.3f, 16f)
        arcTo(1.3f, 1.3f, 0f, true, true, 5.7f, 16f)
        close()
        moveTo(11.4f, 14.4f)
        arcTo(1.6f, 1.6f, 0f, true, true, 14.6f, 14.4f)
        arcTo(1.6f, 1.6f, 0f, true, true, 11.4f, 14.4f)
        close()
        moveTo(16.7f, 17f)
        arcTo(1.1f, 1.1f, 0f, true, true, 18.9f, 17f)
        arcTo(1.1f, 1.1f, 0f, true, true, 16.7f, 17f)
        close()
    }
}.build()

internal val PairingDessertIcon: ImageVector = ImageVector.Builder(
    name = "PairingDessert",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(6.2f, 12.6f)
        lineTo(17.8f, 12.6f)
        lineTo(16.4f, 20.2f)
        curveTo(16.3f, 20.8f, 15.8f, 21.2f, 15.2f, 21.2f)
        lineTo(8.8f, 21.2f)
        curveTo(8.2f, 21.2f, 7.7f, 20.8f, 7.6f, 20.2f)
        close()
        moveTo(6.4f, 12.2f)
        curveTo(5.1f, 12.2f, 4.2f, 11.1f, 4.4f, 9.9f)
        curveTo(4.6f, 8.9f, 5.4f, 8.2f, 6.4f, 8.2f)
        curveTo(6.4f, 6.4f, 7.9f, 5f, 9.7f, 5.2f)
        curveTo(10.3f, 4f, 11.6f, 3.2f, 13f, 3.4f)
        curveTo(14.8f, 3.6f, 16.1f, 5.1f, 16.1f, 6.8f)
        curveTo(17.6f, 6.8f, 18.8f, 8f, 18.8f, 9.5f)
        curveTo(18.8f, 11f, 17.6f, 12.2f, 16.1f, 12.2f)
        close()
    }
}.build()

internal val PairingSpicyFoodIcon: ImageVector = ImageVector.Builder(
    name = "PairingSpicyFood",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(7.4f, 8.2f)
        curveTo(9.8f, 8f, 11.9f, 9.2f, 13.3f, 11.4f)
        curveTo(15.3f, 14.4f, 17.6f, 16.9f, 20.8f, 18.4f)
        curveTo(17.8f, 20.8f, 13.6f, 20.2f, 10.8f, 17.2f)
        curveTo(8.4f, 14.6f, 7.4f, 11.6f, 7.4f, 8.2f)
        close()
        moveTo(6.2f, 4f)
        curveTo(8.2f, 4f, 9.2f, 5.4f, 9f, 7.6f)
        lineTo(6.6f, 8.8f)
        curveTo(5.6f, 7.2f, 5.4f, 5.4f, 6.2f, 4f)
        close()
    }
}.build()

internal val PairingAsianFoodIcon: ImageVector = ImageVector.Builder(
    name = "PairingAsianFood",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(2.8f, 12.2f)
        lineTo(21.2f, 12.2f)
        curveTo(21.2f, 17f, 17.1f, 20.8f, 12f, 20.8f)
        curveTo(6.9f, 20.8f, 2.8f, 17f, 2.8f, 12.2f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(2f, 20.6f)
        lineTo(22f, 20.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(21.6f, 3.4f)
        lineTo(12.6f, 10.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(22.4f, 6.2f)
        lineTo(16f, 10.8f)
    }
}.build()

internal val PairingBuffetIcon: ImageVector = ImageVector.Builder(
    name = "PairingBuffet",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(3f, 16.4f)
        curveTo(3f, 11.4f, 7f, 7.4f, 12f, 7.4f)
        curveTo(17f, 7.4f, 21f, 11.4f, 21f, 16.4f)
        close()
        moveTo(2f, 17.6f)
        lineTo(22f, 17.6f)
        curveTo(22.6f, 17.6f, 23f, 18f, 23f, 18.6f)
        curveTo(23f, 19.2f, 22.6f, 19.6f, 22f, 19.6f)
        lineTo(2f, 19.6f)
        curveTo(1.4f, 19.6f, 1f, 19.2f, 1f, 18.6f)
        curveTo(1f, 18f, 1.4f, 17.6f, 2f, 17.6f)
        close()
        moveTo(10.5f, 5.4f)
        arcTo(1.5f, 1.5f, 0f, true, true, 13.5f, 5.4f)
        arcTo(1.5f, 1.5f, 0f, true, true, 10.5f, 5.4f)
        close()
    }
}.build()

internal val PairingAperitifIcon: ImageVector = ImageVector.Builder(
    name = "PairingAperitif",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(3.4f, 4.4f)
        lineTo(20.6f, 4.4f)
        lineTo(13f, 13.2f)
        lineTo(11f, 13.2f)
        close()
        moveTo(11.1f, 12.8f)
        lineTo(12.9f, 12.8f)
        lineTo(12.9f, 19.2f)
        lineTo(11.1f, 19.2f)
        close()
        moveTo(7.4f, 19.2f)
        lineTo(16.6f, 19.2f)
        curveTo(17.2f, 19.2f, 17.6f, 19.6f, 17.6f, 20.2f)
        curveTo(17.6f, 20.8f, 17.2f, 21.2f, 16.6f, 21.2f)
        lineTo(7.4f, 21.2f)
        curveTo(6.8f, 21.2f, 6.4f, 20.8f, 6.4f, 20.2f)
        curveTo(6.4f, 19.6f, 6.8f, 19.2f, 7.4f, 19.2f)
        close()
    }
}.build()

internal val PairingSocialDrinkIcon: ImageVector = ImageVector.Builder(
    name = "PairingSocialDrink",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(0.742f, 8.1672f)
        lineTo(7.2694f, 5.5299f)
        curveTo(8.8277f, 9.387f, 8.4516f, 12.3f, 6.6429f, 13.3759f)
        curveTo(4.5945f, 13.8584f, 2.3003f, 12.0243f, 0.742f, 8.1672f)
        close()
    }
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(5.8925f, 13.334f)
        lineTo(7.1535f, 12.8245f)
        lineTo(9.551f, 18.7585f)
        lineTo(8.29f, 19.2679f)
        close()
    }
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(6.0419f, 20.0037f)
        lineTo(11.6792f, 17.7261f)
        curveTo(12.0501f, 17.5762f, 12.4667f, 17.753f, 12.6165f, 18.1239f)
        curveTo(12.7663f, 18.4948f, 12.5895f, 18.9114f, 12.2186f, 19.0612f)
        lineTo(6.5814f, 21.3388f)
        curveTo(6.2105f, 21.4886f, 5.7939f, 21.3118f, 5.6441f, 20.9409f)
        curveTo(5.4942f, 20.5701f, 5.6711f, 20.1535f, 6.0419f, 20.0037f)
        close()
    }
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(16.5306f, 5.5299f)
        lineTo(23.058f, 8.1672f)
        curveTo(21.4997f, 12.0243f, 19.2055f, 13.8584f, 17.1571f, 13.3759f)
        curveTo(15.3484f, 12.3f, 14.9723f, 9.387f, 16.5306f, 5.5299f)
        close()
    }
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(16.6465f, 12.8245f)
        lineTo(17.9075f, 13.334f)
        lineTo(15.51f, 19.2679f)
        lineTo(14.249f, 18.7585f)
        close()
    }
    path(
        fill = SolidColor(Color.Black),
    ) {
        moveTo(12.1208f, 17.7261f)
        lineTo(17.7581f, 20.0037f)
        curveTo(18.1289f, 20.1535f, 18.3058f, 20.5701f, 18.1559f, 20.9409f)
        curveTo(18.0061f, 21.3118f, 17.5895f, 21.4886f, 17.2186f, 21.3388f)
        lineTo(11.5814f, 19.0612f)
        curveTo(11.2105f, 18.9114f, 11.0337f, 18.4948f, 11.1835f, 18.1239f)
        curveTo(11.3333f, 17.753f, 11.7499f, 17.5762f, 12.1208f, 17.7261f)
        close()
    }
}.build()

/**
 * Exhaustive on purpose: a new [Pairing] cannot compile until it has an
 * icon and a label.
 */
internal fun pairingIcon(pairing: Pairing): ImageVector = when (pairing) {
    Pairing.PORK -> PairingPorkIcon
    Pairing.POULTRY -> PairingPoultryIcon
    Pairing.LAMB -> PairingLambIcon
    Pairing.BEEF -> PairingBeefIcon
    Pairing.GAME -> PairingGameIcon
    Pairing.FISH -> PairingFishIcon
    Pairing.SHELLFISH -> PairingShellfishIcon
    Pairing.VEGETABLES -> PairingVegetablesIcon
    Pairing.CHEESE -> PairingCheeseIcon
    Pairing.DESSERT -> PairingDessertIcon
    Pairing.SPICY -> PairingSpicyFoodIcon
    Pairing.ASIAN -> PairingAsianFoodIcon
    Pairing.BUFFET -> PairingBuffetIcon
    Pairing.APERITIF -> PairingAperitifIcon
    Pairing.SOCIAL -> PairingSocialDrinkIcon
}

internal fun pairingLabelRes(pairing: Pairing): Int = when (pairing) {
    Pairing.PORK -> R.string.pairing_pork
    Pairing.POULTRY -> R.string.pairing_poultry
    Pairing.LAMB -> R.string.pairing_lamb
    Pairing.BEEF -> R.string.pairing_beef
    Pairing.GAME -> R.string.pairing_game
    Pairing.FISH -> R.string.pairing_fish
    Pairing.SHELLFISH -> R.string.pairing_shellfish
    Pairing.VEGETABLES -> R.string.pairing_vegetables
    Pairing.CHEESE -> R.string.pairing_cheese
    Pairing.DESSERT -> R.string.pairing_dessert
    Pairing.SPICY -> R.string.pairing_spicy
    Pairing.ASIAN -> R.string.pairing_asian
    Pairing.BUFFET -> R.string.pairing_buffet
    Pairing.APERITIF -> R.string.pairing_aperitif
    Pairing.SOCIAL -> R.string.pairing_social
}

/** The translated display label for a pairing. */
@Composable
internal fun pairingLabel(pairing: Pairing): String = stringResource(pairingLabelRes(pairing))

@Composable
internal fun PairingIcon(
    pairing: Pairing,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = pairingIcon(pairing),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
