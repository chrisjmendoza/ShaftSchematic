package com.android.shaftschematic.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.util.PreviewDimTextSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings → Editor Screen → "Dimension text size" of the preview's rails
 * ([SettingsStore.previewDimTextSizeFlow]) — default Medium, and it persists.
 *
 * The settings DataStore is a process-wide singleton, so the round-trip ends on the default
 * (Medium): whichever test runs first, the default test reads what an untouched store would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStorePreviewDimTextSizeTest {

    @Test
    fun `defaults to medium`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(PreviewDimTextSize.MEDIUM, SettingsStore.previewDimTextSizeFlow(ctx).first())
    }

    @Test
    fun `set then read round-trips every size`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        for (size in listOf(PreviewDimTextSize.SMALL, PreviewDimTextSize.LARGE, PreviewDimTextSize.MEDIUM)) {
            SettingsStore.setPreviewDimTextSize(ctx, size)
            assertEquals(size, SettingsStore.previewDimTextSizeFlow(ctx).first())
        }
    }
}
