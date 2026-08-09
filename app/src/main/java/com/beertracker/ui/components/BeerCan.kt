package com.beertracker.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val BEER_CAN_ASPECT_RATIO = 12f / 22f

internal val BeerCanIcon: ImageVector = ImageVector.Builder(
    name = "BeerCan",
    defaultWidth = 12.dp,
    defaultHeight = 22.dp,
    viewportWidth = 12f,
    viewportHeight = 22f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        // Lid, with the neck gap below reading as the rim groove.
        moveTo(2.4f, 0f)
        lineTo(9.6f, 0f)
        curveTo(10.1f, 0f, 10.5f, 0.4f, 10.5f, 0.9f)
        lineTo(10.5f, 2.2f)
        lineTo(1.5f, 2.2f)
        lineTo(1.5f, 0.9f)
        curveTo(1.5f, 0.4f, 1.9f, 0f, 2.4f, 0f)
        close()
        // Body, tapering slightly toward a rounded base.
        moveTo(1f, 3.4f)
        lineTo(11f, 3.4f)
        lineTo(10.55f, 19.8f)
        curveTo(10.52f, 21f, 9.55f, 22f, 8.35f, 22f)
        lineTo(3.65f, 22f)
        curveTo(2.45f, 22f, 1.48f, 21f, 1.45f, 19.8f)
        close()
    }
}.build()

@Composable
internal fun BeerCan(
    filled: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val tint = if (filled) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Icon(
        imageVector = BeerCanIcon,
        contentDescription = null,
        modifier = modifier
            .height(height)
            .width(height * BEER_CAN_ASPECT_RATIO),
        tint = tint.copy(alpha = tint.alpha * alpha),
    )
}
