package com.android.shaftschematic.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round-trips [SettingsStore.dialogUnitConverterEnabledFlow] / [SettingsStore.setDialogUnitConverterEnabled]
 * — the capability gate for the Add dialogs' title-row unit-converter icon (default OFF; the
 * sidebar Tools entry is unaffected by this pref).
 *
 * `Context.settingsDataStore` is a process-wide singleton keyed by file, not by the specific
 * [Context] instance handed in — asserting an absolute "starts false" default would depend on
 * test execution order against every other test in the same JVM worker that touches settings.
 * A single sequential test that only checks its own writes avoids that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStoreDialogUnitConverterTest {

    @Test
    fun `set then read round-trips both true and false`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        SettingsStore.setDialogUnitConverterEnabled(ctx, true)
        assertTrue(SettingsStore.dialogUnitConverterEnabledFlow(ctx).first())

        SettingsStore.setDialogUnitConverterEnabled(ctx, false)
        assertFalse(SettingsStore.dialogUnitConverterEnabledFlow(ctx).first())
    }
}
