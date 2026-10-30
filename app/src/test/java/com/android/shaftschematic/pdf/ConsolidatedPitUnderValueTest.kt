package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.PitSize
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WearPit
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.WearAreaStyle
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * On the consolidated sheet a pit X that would cross an in-profile value's halo is not drawn
 * (on-device ruling: the value stands above the pits). Driven through the real composer: a LARGE
 * pit centred under a valued liner reading is skipped, a LARGE pit clear of every halo still
 * draws, and with no value printed both draw — with the wear areas shaded (no halo, the rule
 * guards the text box) and with them stroked (the halo) alike.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConsolidatedPitUnderValueTest {

    /** Records the centre of every pit-X stroke (round cap, 45°, a LARGE pit's arm span). */
    private class PitRecordingCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        val pitStrokeCentersX = mutableListOf<Float>()

        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            val dx = abs(stopX - startX)
            val dy = abs(stopY - startY)
            val span = 2f * WEAR_PIT_SMALL_HALF_PROFILE_PT * 2f   // LARGE = 2 × small half-arm, both sides
            if (paint.strokeCap == Paint.Cap.ROUND && abs(dx - dy) < 0.01f && abs(dx - span) < 0.01f) {
                pitStrokeCentersX += (startX + stopX) / 2f
            }
            super.drawLine(startX, startY, stopX, stopY, paint)
        }
    }

    private val inch = 25.4f
    private val spec = ShaftSpec(
        overallLengthMm = 100f * inch,
        autoBodyDiaMm = 10f * inch,
        liners = listOf(Liner(id = "L1", startFromAftMm = 20f * inch, lengthMm = 40f * inch, odMm = 11f * inch)),
    )

    private val underPit = WearPit(componentId = "L1", axialMm = 10f * inch, acrossFrac = 0.5f, size = PitSize.LARGE)
    private val clearPit = WearPit(componentId = "L1", axialMm = 35f * inch, acrossFrac = 0.5f, size = PitSize.LARGE)
    private val reading = WearDiaReading(componentId = "L1", axialMm = 10f * inch, diaMm = 10.5f * inch)

    private val modes = listOf(true, false)

    private fun compose(wear: WearRecord, shaded: Boolean): PitRecordingCanvas {
        val c = PitRecordingCanvas(
            Bitmap.createBitmap(PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT, Bitmap.Config.ARGB_8888),
        )
        composeRunoutPdfOnCanvas(
            c, PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat(), spec,
            config = RunoutConfig(),
            project = ProjectInfo(),
            unit = UnitSystem.INCHES,
            pdfPrefs = PdfPrefs(wearAreaStyle = if (shaded) WearAreaStyle.SHADE else WearAreaStyle.LINES),
            resolvedComponents = resolveComponents(spec),
            wearRecord = wear,
            consolidated = true, includeBubbles = false, includeWearInfo = true,
        )
        return c
    }

    @Test
    fun `with no value printed both pits draw`() = modes.forEach { shaded ->
        val c = compose(WearRecord(pits = listOf(underPit, clearPit)), shaded)
        // Two strokes per X.
        assertEquals("shaded=$shaded pit strokes ${c.pitStrokeCentersX}", 4, c.pitStrokeCentersX.size)
    }

    @Test
    fun `a pit under a valued reading is hidden and a pit clear of every halo still draws`() = modes.forEach { shaded ->
        val withValue = compose(WearRecord(pits = listOf(underPit, clearPit), diaReadings = listOf(reading)), shaded)
        val bare = compose(WearRecord(pits = listOf(underPit, clearPit)), shaded)
        assertEquals("shaded=$shaded pit strokes ${withValue.pitStrokeCentersX}", 2, withValue.pitStrokeCentersX.size)
        // The X that survives is the clear pit's — the right-hand pair on the bare sheet.
        val clearX = bare.pitStrokeCentersX.max()
        assertTrue(withValue.pitStrokeCentersX.all { abs(it - clearX) < 0.01f })
    }

    @Test
    fun `a zero reading prints no value and hides no pit`() = modes.forEach { shaded ->
        val c = compose(
            WearRecord(pits = listOf(underPit, clearPit), diaReadings = listOf(reading.copy(diaMm = 0f))),
            shaded,
        )
        assertEquals("shaded=$shaded", 4, c.pitStrokeCentersX.size)
    }
}
