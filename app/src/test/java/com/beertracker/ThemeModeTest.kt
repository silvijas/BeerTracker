package com.beertracker

import com.beertracker.domain.ThemeMode
import com.beertracker.domain.isDarkTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `system mode follows the platform setting`() {
        assertTrue(ThemeMode.SYSTEM.isDarkTheme(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDarkTheme(systemDark = false))
    }

    @Test
    fun `light mode ignores the platform setting`() {
        assertFalse(ThemeMode.LIGHT.isDarkTheme(systemDark = true))
        assertFalse(ThemeMode.LIGHT.isDarkTheme(systemDark = false))
    }

    @Test
    fun `dark mode ignores the platform setting`() {
        assertTrue(ThemeMode.DARK.isDarkTheme(systemDark = true))
        assertTrue(ThemeMode.DARK.isDarkTheme(systemDark = false))
    }
}
