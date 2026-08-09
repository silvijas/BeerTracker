package com.beertracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BottleGreen,
    onPrimary = PaperWhite,
    primaryContainer = BottleTint,
    onPrimaryContainer = BottleGreenDeep,
    inversePrimary = BottleMint,
    secondary = Ink,
    onSecondary = PaperWhite,
    secondaryContainer = BottleTint,
    onSecondaryContainer = BottleGreen,
    tertiary = GoldAccent,
    onTertiary = Ink,
    tertiaryContainer = GoldPale,
    onTertiaryContainer = Ink,
    background = PaperWhite,
    onBackground = Ink,
    surface = PaperWhite,
    onSurface = Ink,
    surfaceVariant = ShelfGray,
    onSurfaceVariant = MutedGray,
    surfaceTint = PaperWhite,
    inverseSurface = Ink,
    inverseOnSurface = ShelfGray,
    outline = BorderGray,
    outlineVariant = HairlineGray,
    error = ErrorRed,
    onError = PaperWhite,
    surfaceContainerLowest = PaperWhite,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = PaperWhite,
    surfaceContainerHigh = PaperWhite,
    surfaceContainerHighest = ShelfGray,
)

private val DarkColors = darkColorScheme(
    primary = BottleMint,
    onPrimary = DeepForest,
    primaryContainer = BottleGreen,
    onPrimaryContainer = BottleTint,
    inversePrimary = BottleGreen,
    secondary = PaperWhite,
    onSecondary = NightSurface,
    secondaryContainer = BottleGreen,
    onSecondaryContainer = BottleTint,
    tertiary = GoldBright,
    onTertiary = NightBlack,
    tertiaryContainer = GoldNightContainer,
    onTertiaryContainer = GoldPale,
    background = NightBlack,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightInput,
    onSurfaceVariant = NightMuted,
    surfaceTint = NightSurface,
    inverseSurface = NightText,
    inverseOnSurface = Ink,
    outline = NightOutline,
    outlineVariant = NightHairline,
    error = ErrorRedSoft,
    onError = NightBlack,
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = NightCard,
    surfaceContainerHigh = NightInput,
    surfaceContainerHighest = Color(0xFF3B3B3B),
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
