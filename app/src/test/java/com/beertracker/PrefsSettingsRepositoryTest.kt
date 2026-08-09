package com.beertracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.beertracker.data.PrefsSettingsRepository
import com.beertracker.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsSettingsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `defaults to the system theme`() {
        assertEquals(ThemeMode.SYSTEM, PrefsSettingsRepository(context).themeMode.value)
    }

    @Test
    fun `keeps the chosen theme across instances`() {
        PrefsSettingsRepository(context).setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, PrefsSettingsRepository(context).themeMode.value)
    }

    @Test
    fun `falls back to the system theme on an unknown stored value`() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", "TOTALLY_BOGUS")
            .commit()

        assertEquals(ThemeMode.SYSTEM, PrefsSettingsRepository(context).themeMode.value)
    }
}
