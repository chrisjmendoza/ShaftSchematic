package com.android.shaftschematic.pdf

import android.graphics.Color
import android.graphics.Paint
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.util.OutputFont
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [sheetTextPaint] carries the Output font into every text paint a composer builds — the rail
 * values and the Ø callouts included, which once printed sans under a Serif choice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetTextPaintTest {

    @After
    fun restoreShippedFont() {
        SettingsStore.updatePdfPrefs { it.copy(outputFont = PdfPrefs().outputFont) }
    }

    @Test
    fun `a sheet text paint takes the chosen output face`() {
        SettingsStore.updatePdfPrefs { it.copy(outputFont = OutputFont.SERIF) }
        assertEquals(OutputFont.SERIF.typeface(), sheetTextPaint(10f).typeface)

        SettingsStore.updatePdfPrefs { it.copy(outputFont = OutputFont.MONOSPACE) }
        assertEquals(OutputFont.MONOSPACE.typeface(), sheetTextPaint(10f).typeface)
    }

    @Test
    fun `a copy of a sheet text paint keeps the face`() {
        SettingsStore.updatePdfPrefs { it.copy(outputFont = OutputFont.SERIF) }
        val derived = Paint(sheetTextPaint(12f)).apply { textSize = 8f }
        assertEquals(OutputFont.SERIF.typeface(), derived.typeface)
    }

    @Test
    fun `size, alignment and ink are the caller's`() {
        val p = sheetTextPaint(9f, Paint.Align.CENTER)
        assertEquals(9f, p.textSize, 0f)
        assertEquals(Paint.Align.CENTER, p.textAlign)
        assertEquals(Paint.Style.FILL, p.style)
        assertEquals(Color.BLACK, p.color)
        assertEquals(true, p.isAntiAlias)
    }
}
