package com.android.shaftschematic.pdf

import android.content.Context
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.model.WornSection
import com.android.shaftschematic.settings.DrawingProfile
import com.android.shaftschematic.settings.decodeDrawingProfiles
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.WearAreaStyle
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "Wear areas on the shaft drawing" (`PdfPrefs.wearAreaStyle`, default Grey — on-device ruling):
 * on the consolidated sheet each wear area is ONE light grey fill, vertical strokes, both (the
 * strokes over the grey — the grey alone read on-device as a shaded liner), or nothing. Wherever
 * the grey is drawn, an in-profile Ø value's knockout is re-shaded with it — no white box shows,
 * yet a line under the digits is still knocked out; without the grey the halos stay white.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConsolidatedWearShadeTest {

    private val inch = 25.4f
    private val spec = ShaftSpec(
        overallLengthMm = 120f * inch,
        autoBodyDiaMm = 10f * inch,
        liners = listOf(Liner(id = "L1", startFromAftMm = 20f * inch, lengthMm = 40f * inch, odMm = 11f * inch)),
    )
    private val wear = WearRecord(
        spots = listOf(
            WearSpot(linerId = "L1", startMm = 4f * inch, lengthMm = 8f * inch),
            WearSpot(linerId = "L1", startMm = 24f * inch, lengthMm = 10f * inch),
        ),
        diaReadings = listOf(WearDiaReading(componentId = "L1", axialMm = 8f * inch, diaMm = 10.95f * inch)),
        wornSections = listOf(
            WornSection(startFromAftMm = 46f * inch, lengthMm = 8f * inch, diaMm = listOf(10.97f * inch, 10.96f * inch)),
        ),
    )
    /** One reading + two worn-section values, each over a wear area. */
    private val inProfileValueCount = 3

    private fun compose(style: WearAreaStyle): SheetTextRecorder {
        val w = PDF_PAGE_WIDTH_PT.toFloat(); val h = PDF_PAGE_HEIGHT_PT.toFloat()
        val c = SheetTextRecorder(w, h)
        composeRunoutPdfOnCanvas(
            c, w, h, spec, config = RunoutConfig(), project = ProjectInfo(), unit = UnitSystem.INCHES,
            pdfPrefs = PdfPrefs(wearAreaStyle = style), resolvedComponents = resolveComponents(spec),
            wearRecord = wear, consolidated = true, includeBubbles = false, includeWearInfo = true,
        )
        return c
    }

    private fun SheetTextRecorder.shadeFills() = rects.filter {
        it.style == Paint.Style.FILL && (it.color and 0x00FFFFFF) == 0 && it.alpha == CONSOLIDATED_WEAR_SHADE_ALPHA
    }

    private fun SheetTextRecorder.whiteFills() = rects.filter {
        it.style == Paint.Style.FILL && (it.color and 0x00FFFFFF) == 0x00FFFFFF && it.alpha == 255
    }

    /** The stroked band's paint: half the outline width at alpha 120. */
    private fun SheetTextRecorder.bandStrokes() = lines.count { it.alpha == 120 && abs(it.x0 - it.x1) < 1e-3f }

    private fun abs(v: Float) = kotlin.math.abs(v)

    @Test fun `the pref is grey by default and the profile captures and restores it`() {
        assertEquals(WearAreaStyle.SHADE, PdfPrefs().wearAreaStyle)
        assertEquals("Restore Drawing defaults puts it back", WearAreaStyle.SHADE, DrawingProfile().toPdfPrefs().wearAreaStyle)
        WearAreaStyle.entries.forEach { style ->
            assertEquals(style, DrawingProfile.of(PdfPrefs(wearAreaStyle = style), lineThicknessScale = 1f).toPdfPrefs().wearAreaStyle)
        }
    }

    @Test fun `a profile saved by the old on-off switch keeps meaning what it printed`() {
        val profiles = decodeDrawingProfiles("""{"On":{"wearAreasShaded":true},"Off":{"wearAreasShaded":false}}""")
        assertEquals(WearAreaStyle.SHADE, profiles.getValue("On").toPdfPrefs().wearAreaStyle)
        assertEquals(WearAreaStyle.LINES, profiles.getValue("Off").toPdfPrefs().wearAreaStyle)
    }

    @Test fun `the chip order is None, Lines, Grey, Both`() {
        assertEquals(listOf("None", "Lines", "Grey", "Both"), WearAreaStyle.entries.map { it.uiLabel() })
        assertFalse(WearAreaStyle.NONE.fills || WearAreaStyle.NONE.strokes)
        assertTrue(WearAreaStyle.BOTH.fills && WearAreaStyle.BOTH.strokes)
    }

    @Test fun `the setting round-trips through the settings store`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        WearAreaStyle.entries.forEach { style ->
            SettingsStore.setPdfWearAreaStyle(ctx, style)
            assertEquals(style, SettingsStore.pdfWearAreaStyleFlow(ctx).first())
        }
    }

    @Test fun `grey - one 10 percent fill per wear area, no strokes, every knockout re-shaded`() {
        val on = compose(WearAreaStyle.SHADE)
        val off = compose(WearAreaStyle.LINES)
        val shades = on.shadeFills()
        // The band fills are the full drawn liner height; the re-shades are clipped to a value's box.
        val bandHeight = shades.maxOf { it.rect.height() }
        val bands = shades.filter { kotlin.math.abs(it.rect.height() - bandHeight) < 1e-3f }
        assertEquals("one fill per wear area", wear.spots.size, bands.size)
        assertEquals("≈10% grey", 0.10f, CONSOLIDATED_WEAR_SHADE_ALPHA / 255f, 0.01f)
        assertEquals("no vertical band strokes", 0, on.bandStrokes())
        assertEquals("the same knockouts in both modes", off.whiteFills().size, on.whiteFills().size)
        // Every white knockout that overlaps a band is followed by a shade over exactly the overlap.
        var reshaded = 0
        on.whiteFills().forEach { halo ->
            bands.forEach { band ->
                val overlap = android.graphics.RectF()
                if (!overlap.setIntersect(halo.rect, band.rect)) return@forEach
                val hit = shades.any {
                    kotlin.math.abs(it.rect.left - overlap.left) < 1e-3f && kotlin.math.abs(it.rect.right - overlap.right) < 1e-3f &&
                        kotlin.math.abs(it.rect.top - overlap.top) < 1e-3f && kotlin.math.abs(it.rect.bottom - overlap.bottom) < 1e-3f
                }
                assertTrue("knockout ${halo.rect} over band ${band.rect} is not re-shaded", hit)
                reshaded++
            }
        }
        assertTrue("each in-profile value sits on a band and is re-shaded", reshaded >= inProfileValueCount)
        // The values still print.
        val expected = "Ø" + formatDiaWithUnit(10.95 * inch, UnitSystem.INCHES)
        assertTrue(expected in on.strings)
        // Both bands share the liner's full drawn height.
        assertEquals(bands[0].rect.top, bands[1].rect.top, 1e-3f)
        assertEquals(bands[0].rect.bottom, bands[1].rect.bottom, 1e-3f)
    }

    @Test fun `lines - vertical strokes and halos, no shade fill`() {
        val off = compose(WearAreaStyle.LINES)
        assertEquals(0, off.shadeFills().size)
        assertTrue("vertical band strokes", off.bandStrokes() > 0)
        assertTrue("white value knockouts", off.whiteFills().isNotEmpty())
    }

    @Test fun `both - the strokes over the grey, knockouts re-shaded`() {
        val both = compose(WearAreaStyle.BOTH)
        val grey = compose(WearAreaStyle.SHADE)
        val lines = compose(WearAreaStyle.LINES)
        assertEquals("the grey's fills and re-shades", grey.shadeFills().size, both.shadeFills().size)
        assertEquals("the lines' strokes", lines.bandStrokes(), both.bandStrokes())
        assertTrue(both.bandStrokes() > 0)
    }

    @Test fun `none - no wear marks, values still print on white halos`() {
        val none = compose(WearAreaStyle.NONE)
        assertEquals(0, none.shadeFills().size)
        assertEquals(0, none.bandStrokes())
        assertTrue("white value knockouts", none.whiteFills().isNotEmpty())
        assertTrue(("Ø" + formatDiaWithUnit(10.95 * inch, UnitSystem.INCHES)) in none.strings)
    }

    @Test fun `with no wear areas the shade draws nothing`() {
        val w = PDF_PAGE_WIDTH_PT.toFloat(); val h = PDF_PAGE_HEIGHT_PT.toFloat()
        val c = SheetTextRecorder(w, h)
        composeRunoutPdfOnCanvas(
            c, w, h, spec, config = RunoutConfig(), project = ProjectInfo(), unit = UnitSystem.INCHES,
            pdfPrefs = PdfPrefs(wearAreaStyle = WearAreaStyle.BOTH), resolvedComponents = resolveComponents(spec),
            wearRecord = wear.copy(spots = emptyList()), consolidated = true, includeBubbles = false, includeWearInfo = true,
        )
        assertEquals(0, c.shadeFills().size)
    }
}
