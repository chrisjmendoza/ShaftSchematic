package com.android.shaftschematic.pdf

import android.graphics.Paint
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.WearAreaStyle
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A wear area on a body (explicit or bare shaft) draws on the consolidated sheet the way a
 * liner's does — the same `PdfPrefs.wearAreaStyle` grey / strokes, at the body's own height,
 * clipped to the drawn run and clear of its S-break gap. An area whose liner or body no longer
 * resolves is skipped at this render layer, and a record with no body areas prints as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BodyWearAreaSheetTest {

    private val inch = 25.4f
    private val spec = ShaftSpec(
        overallLengthMm = 120f * inch,
        autoBodyDiaMm = 10f * inch,
        liners = listOf(Liner(id = "L1", startFromAftMm = 20f * inch, lengthMm = 40f * inch, odMm = 11f * inch)),
    )
    private val resolved = resolveComponents(spec)
    private val fwdRun = resolved.filterIsInstance<ResolvedBody>().single { it.startMmPhysical == 60f * inch }

    private val linerArea = WearSpot(id = "liner", linerId = "L1", startMm = 4f * inch, lengthMm = 8f * inch)
    private val bodyArea = WearSpot(id = "body", linerId = fwdRun.id, startMm = 6f * inch, lengthMm = 5f * inch)
    private val orphanLiner = WearSpot(id = "o1", linerId = "deleted-liner", startMm = 1f * inch, lengthMm = 2f * inch)
    private val orphanRun = WearSpot(id = "o2", linerId = "auto_body_1.000_2.000", startMm = 1f * inch, lengthMm = 2f * inch)

    private fun compose(record: WearRecord, style: WearAreaStyle): SheetTextRecorder {
        val w = PDF_PAGE_WIDTH_PT.toFloat(); val h = PDF_PAGE_HEIGHT_PT.toFloat()
        val c = SheetTextRecorder(w, h)
        composeRunoutPdfOnCanvas(
            c, w, h, spec, config = RunoutConfig(), project = ProjectInfo(), unit = UnitSystem.INCHES,
            pdfPrefs = PdfPrefs(wearAreaStyle = style), resolvedComponents = resolved,
            wearRecord = record, consolidated = true, includeBubbles = false, includeWearInfo = true,
        )
        return c
    }

    private fun SheetTextRecorder.shadeFills() = rects.filter {
        it.style == Paint.Style.FILL && (it.color and 0x00FFFFFF) == 0 && it.alpha == CONSOLIDATED_WEAR_SHADE_ALPHA
    }

    private fun SheetTextRecorder.bandStrokes() = lines.count { it.alpha == 120 && abs(it.x0 - it.x1) < 1e-3f }

    @Test fun `grey - a body area fills at the body's height beside the liner's`() {
        val fills = compose(WearRecord(spots = listOf(linerArea, bodyArea)), WearAreaStyle.SHADE).shadeFills()
        assertEquals(2, fills.size)
        val (tall, short) = fills.sortedByDescending { it.rect.height() }
        // 11 in liner over a 10 in shaft: the body's band is 10/11 the liner band's height.
        assertEquals(10f / 11f, short.rect.height() / tall.rect.height(), 1e-3f)
        assertTrue("the body's band is FWD of the liner's", short.rect.left > tall.rect.right)
        assertEquals("both on the centreline", tall.rect.centerY(), short.rect.centerY(), 1e-3f)
    }

    @Test fun `the style governs a body area like a liner's`() {
        val record = WearRecord(spots = listOf(bodyArea))
        val lines = compose(record, WearAreaStyle.LINES)
        assertEquals(0, lines.shadeFills().size)
        assertTrue(lines.bandStrokes() > 0)
        val both = compose(record, WearAreaStyle.BOTH)
        assertEquals(1, both.shadeFills().size)
        assertEquals(lines.bandStrokes(), both.bandStrokes())
        val none = compose(record, WearAreaStyle.NONE)
        assertEquals(0, none.shadeFills().size)
        assertEquals(0, none.bandStrokes())
    }

    @Test fun `orphan areas draw nothing and change nothing else on the sheet`() {
        val clean = compose(WearRecord(spots = listOf(linerArea)), WearAreaStyle.BOTH)
        val withOrphans = compose(WearRecord(spots = listOf(linerArea, orphanLiner, orphanRun)), WearAreaStyle.BOTH)
        assertEquals(1, withOrphans.shadeFills().size)
        assertEquals(clean.rects.size, withOrphans.rects.size)
        assertEquals(clean.lines.size, withOrphans.lines.size)
        assertEquals(clean.strings, withOrphans.strings)
    }

    // ── The one rect construction ────────────────────────────────────────────────────────

    private val xAt: (Float) -> Float = { it / inch } // 1 pt per inch
    private val rPx: (Float) -> Float = { it / inch / 2f } // a diameter's drawn radius

    @Test fun `band rects - liner areas from the spec, body areas from the resolved runs`() {
        val record = WearRecord(spots = listOf(linerArea, bodyArea, orphanLiner, orphanRun))
        val rects = consolidatedWearBandRects(record, spec.liners, 100f, xAt, rPx, resolved)
        assertEquals(2, rects.size)
        assertEquals(24f, rects[0].left, 1e-3f); assertEquals(32f, rects[0].right, 1e-3f)
        assertEquals(11f, rects[0].height(), 1e-3f)
        assertEquals(66f, rects[1].left, 1e-3f); assertEquals(71f, rects[1].right, 1e-3f)
        assertEquals(10f, rects[1].height(), 1e-3f)
    }

    @Test fun `band rects - with no resolved list only the liner areas draw, as before`() {
        val record = WearRecord(spots = listOf(linerArea, bodyArea))
        assertEquals(1, consolidatedWearBandRects(record, spec.liners, 100f, xAt, rPx).size)
    }

    @Test fun `band rects - a body area stops at each side of the run's break gap, a liner's does not`() {
        val record = WearRecord(spots = listOf(linerArea, bodyArea))
        val rects = consolidatedWearBandRects(
            record, spec.liners, 100f, xAt, rPx, resolved, bodyGapsX = listOf(26f..30f, 68f..69f),
        )
        assertEquals(3, rects.size)
        assertEquals("the liner band ignores a body gap", 8f, rects[0].width(), 1e-3f)
        assertEquals(66f, rects[1].left, 1e-3f); assertEquals(68f, rects[1].right, 1e-3f)
        assertEquals(69f, rects[2].left, 1e-3f); assertEquals(71f, rects[2].right, 1e-3f)
    }

    @Test fun `band rects - a body area is clamped to its drawn run`() {
        // Starts 3 in AFT of the run (under the liner) and runs 10 in: only the run's part draws.
        val over = WearSpot(id = "over", linerId = fwdRun.id, startMm = -3f * inch, lengthMm = 10f * inch)
        val rect = consolidatedWearBandRects(WearRecord(spots = listOf(over)), spec.liners, 100f, xAt, rPx, resolved).single()
        assertEquals(60f, rect.left, 1e-3f)
        assertEquals(67f, rect.right, 1e-3f)
    }
}
