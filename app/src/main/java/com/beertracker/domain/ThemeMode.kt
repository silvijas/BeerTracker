package com.beertracker.domain

import kotlinx.coroutines.flow.StateFlow

/** How the app picks between the light and dark theme. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Resolves the effective dark flag given the current system setting. */
fun ThemeMode.isDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

interface SettingsRepository {
    val themeMode: StateFlow<ThemeMode>
    fun setThemeMode(mode: ThemeMode)
}
