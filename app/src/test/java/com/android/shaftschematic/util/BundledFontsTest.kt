package com.android.shaftschematic.util

import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every Output font is bundled under `res/font`, loads at process start, and is the face its choice
 * draws with — never a system family, which a phone may swap for its own font (on-device report:
 * four system faces printed identically on a Samsung while the bundled Hyperlegible came through).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BundledFontsTest {

    @Test
    fun `every face loads from res font and is the one its choice draws with`() {
        BundledFonts.init(ApplicationProvider.getApplicationContext())
        OutputFont.entries.forEach { font ->
            val face = BundledFonts.get(font)
            assertNotNull("$font did not load from res/font", face)
            assertSame(face, font.typeface())
        }
    }

    @Test
    fun `every face sets type its own way`() {
        BundledFonts.init(ApplicationProvider.getApplicationContext())
        fun width(font: OutputFont) = Paint().apply {
            typeface = font.typeface(); textSize = 100f
        }.measureText("L.E.T.: 6.526\" 1:10.732 Ø 0O 1lI")
        val widths = OutputFont.entries.associateWith { width(it) }
        // No two faces measure alike — a face silently printing as another would.
        val distinct = widths.values.map { (it * 10).toInt() }.toSet()
        assertEquals("faces measuring alike: $widths", widths.size, distinct.size)
    }
}
