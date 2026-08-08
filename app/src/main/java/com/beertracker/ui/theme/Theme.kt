package com.beertracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BottleGreen,
    onPrimary = Paper,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = Charcoal,
    secondary = MutedMoss,
    onSecondary = Paper,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = Charcoal,
    tertiary = MaltAmber,
    onTertiary = Charcoal,
    tertiaryContainer = MaltAmber.copy(alpha = 0.18f),
    onTertiaryContainer = Charcoal,
    background = CellarCream,
    onBackground = Charcoal,
    surface = Paper,
    onSurface = Charcoal,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ErrorRed,
    onError = Paper,
)

private val DarkColors = darkColorScheme(
    primary = SoftGreen,
    onPrimary = DeepCellar,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = PaleText,
    secondary = DarkMutedMoss,
    onSecondary = DeepCellar,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = PaleText,
    tertiary = WarmMalt,
    onTertiary = DeepCellar,
    tertiaryContainer = WarmMalt.copy(alpha = 0.2f),
    onTertiaryContainer = PaleText,
    background = DeepCellar,
    onBackground = PaleText,
    surface = DarkPaper,
    onSurface = PaleText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMutedMoss,
    outline = DarkOutline,
    error = DarkErrorRed,
    onError = DeepCellar,
)

@Composable
fun BeerTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BeerTrackerTypography,
        shapes = BeerTrackerShapes,
        content = content,
    )
}
