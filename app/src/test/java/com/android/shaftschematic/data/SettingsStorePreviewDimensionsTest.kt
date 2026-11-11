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
 * Settings → Editor Screen → "Show Dimension Lines in Preview" ([SettingsStore.showPreviewDimensionsFlow])
 * — default ON, and it persists.
 *
 * The settings DataStore is a process-wide singleton, so the round-trip ends on the default
 * (true): whichever test runs first, the default test reads what an untouched store would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStorePreviewDimensionsTest {

    @Test
    fun `defaults on`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(SettingsStore.showPreviewDimensionsFlow(ctx).first())
    }

    @Test
    fun `set then read round-trips both false and true`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        SettingsStore.setShowPreviewDimensions(ctx, false)
        assertFalse(SettingsStore.showPreviewDimensionsFlow(ctx).first())

        SettingsStore.setShowPreviewDimensions(ctx, true)
        assertTrue(SettingsStore.showPreviewDimensionsFlow(ctx).first())
    }
}
