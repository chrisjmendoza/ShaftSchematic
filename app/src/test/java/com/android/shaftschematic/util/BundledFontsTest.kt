package com.android.shaftschematic.util

import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The bundled output font: Atkinson Hyperlegible is packaged under `res/font`, loads at process
 * start, and sets type that differs from the platform sans — so the "Hyperlegible" choice is not
 * silently printing in Standard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BundledFontsTest {

    @Test
    fun `the bundled face loads and is the one Hyperlegible draws with`() {
        BundledFonts.init(ApplicationProvider.getApplicationContext())
        val face = BundledFonts.hyperlegible
        assertNotNull("Atkinson Hyperlegible did not load from res/font", face)
        assertSame(face, OutputFont.HYPERLEGIBLE.typeface())
    }

    @Test
    fun `hyperlegible and medium set type unlike the standard face`() {
        BundledFonts.init(ApplicationProvider.getApplicationContext())
        fun width(font: OutputFont) = Paint().apply {
            typeface = font.typeface(); textSize = 100f
        }.measureText("L.E.T.: 6.526\" 1:10.732 Ø")
        val standard = width(OutputFont.STANDARD)
        assertNotEquals(standard, width(OutputFont.HYPERLEGIBLE), 0.5f)
    }
}
