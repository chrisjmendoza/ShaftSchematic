package com.android.shaftschematic.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AppThemeMode parsing — the persisted-name decode used by SettingsStore.
 *
 * LIGHT is the contractual fallback: it is the app's historical presentation, so a missing
 * or corrupt preference must never surprise the user with a dark app.
 */
class AppThemeModeTest {

    @Test
    fun `null and blank fall back to light`() {
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromName(null))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromName(""))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromName("   "))
    }

    @Test
    fun `unknown names fall back to light`() {
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromName("SOLARIZED"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromName("dark")) // names are exact-case
    }

    @Test
    fun `valid names parse`() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromName("SYSTEM"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromName("LIGHT"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromName("DARK"))
    }
}
