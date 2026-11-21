package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.WearTraceBody
import com.android.shaftschematic.geom.WearTraceLiner
import com.android.shaftschematic.geom.wearBodyZoneTraces
import com.android.shaftschematic.geom.wearLinerTraces
import com.android.shaftschematic.geom.wearTraceBaselineMm
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearAreaName
import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A NAMED wear area is a wear zone (on-device request: packing or grounding-strap wear where there
 * is no liner): the wear sheet prints its name, and a body's readings inside it trace a worn
 * profile like a liner's. An unnamed area is exactly what it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WearZoneTest {

    private val inch = 25.4f

    /* ── The trace ───────────────────────────────────────────────────────────── */

    private val body = WearTraceBody("B1", diaMm = 10f * inch, lengthMm = 100f * inch)
    private val zone = Triple("B1", 20f * inch, 10f * inch)
    private val inZone = Triple("B1", 25f * inch, 9.95f * inch)
    private val outside = Triple("B1", 60f * inch, 9.9f * inch)

    @Test fun `a body traces through a reading inside its named zone`() {
        val traces = wearBodyZoneTraces(listOf(body), listOf(zone), listOf(inZone), 0.025f * inch, 0.25f)
        val verts = traces.getValue("B1")
        // Opens and closes on the surface at the zone's ends, and dips in between.
        assertEquals(20f * inch, verts.first().localMm, 1e-3f)
        assertEquals(30f * inch, verts.last().localMm, 1e-3f)
        assertEquals(0f, verts.first().depthFrac, 0f)
        assertTrue(verts.any { it.depthFrac > 0f })
    }

    @Test fun `overlapping zones on one body trace as one`() {
        val a = Triple("B1", 20f * inch, 10f * inch)
        val b = Triple("B1", 25f * inch, 10f * inch)
        val inSecond = Triple("B1", 33f * inch, 9.95f * inch)
        val verts = wearBodyZoneTraces(listOf(body), listOf(a, b), listOf(inZone, inSecond), 0.025f * inch, 0.25f)
            .getValue("B1")
        assertEquals(20f * inch, verts.first().localMm, 1e-3f)
        assertEquals(35f * inch, verts.last().localMm, 1e-3f)
        assertTrue("both readings trace", verts.count { it.depthFrac > 0.2f } >= 2)
    }

    @Test fun `a liner's zone name stays off the profile names row`() {
        val spots = listOf(WearSpot(id = "s", linerId = "L1", label = "Seal"))
        val host = com.android.shaftschematic.geom.WearAreaHost("L1", 0f, 100f, 50f, isLiner = true)
        val spans = listOf(com.android.shaftschematic.geom.WearAreaSpan("s", host, 10f, 20f))
        assertTrue(wearZoneNameRequests(spans, spots, { it }, { 10f }).isEmpty())
    }

    @Test fun `a reading outside every zone, or with no zone, never traces a body`() {
        assertTrue(wearBodyZoneTraces(listOf(body), listOf(zone), listOf(outside), 0.05f * inch, 0.25f).isEmpty())
        assertTrue(wearBodyZoneTraces(listOf(body), emptyList(), listOf(inZone), 0.05f * inch, 0.25f).isEmpty())
    }

    @Test fun `the baseline pools liner readings and zone readings, and is the liner one without zones`() {
        val liner = WearTraceLiner("L1", odMm = 11f * inch, lengthMm = 30f * inch)
        val linerReading = Triple("L1", 5f * inch, 10.99f * inch)
        val linerOnly = wearTraceBaselineMm(listOf(liner), listOf(body), emptyList(), listOf(linerReading, outside))
        assertEquals((0.01f * inch) / 2f, linerOnly, 1e-3f)
        val pooled = wearTraceBaselineMm(listOf(liner), listOf(body), listOf(zone), listOf(linerReading, inZone))
        assertEquals((0.05f * inch) / 2f, pooled, 1e-3f)
        // Without a baseline handed in, the liner traces exactly as before.
        val spots = emptyList<Triple<String, Float, Float>>()
        assertEquals(
            wearLinerTraces(listOf(liner), spots, listOf(linerReading), 0.25f),
            wearLinerTraces(listOf(liner), spots, listOf(linerReading), 0.25f, deepestDepthMm = linerOnly),
        )
    }

    /* ── The names ───────────────────────────────────────────────────────────── */

    @Test fun `a host's zone names follow its strip title, each once`() {
        val spots = listOf(
            WearSpot(id = "a", linerId = "B1", label = "Packing"),
            WearSpot(id = "b", linerId = "B1", label = "Packing"),
            WearSpot(id = "c", linerId = "B1", label = "Grounding strap"),
            WearSpot(id = "d", linerId = "L1"),
        )
        val titles = withWearZoneNames(mapOf("B1" to "Bare shaft", "L1" to "AFT Liner"), spots)
        assertEquals("Bare shaft (Packing, Grounding strap)", titles["B1"])
        assertEquals("AFT Liner", titles["L1"])
    }

    @Test fun `a zone name slides along its band clear of a liner name`() {
        // Band 100..300, a 60-wide name centred at 170..230; a liner name sits over 150..210.
        val placed = placeWearZoneNames(
            listOf(WearZoneNameRequest("Packing", 60f, 100f, 300f)),
            occupiedX = listOf(150f..210f), loX = 0f, hiX = 1000f,
        ).single()
        assertTrue(placed.fitted)
        assertTrue("clear of the liner name", placed.left >= 210f || placed.left + 60f <= 150f)
        assertTrue("still under its band", placed.left >= 100f && placed.left + 60f <= 300f)
    }

    @Test fun `the presets read back from the stored name`() {
        assertEquals(WearAreaName.PACKING, WearAreaName.presetFor("Packing"))
        assertNull(WearAreaName.presetFor("Stern tube"))
        assertNull(WearAreaName.presetFor(""))
    }

    /* ── The wear sheet ──────────────────────────────────────────────────────── */

    private val spec = ShaftSpec(
        overallLengthMm = 200f * inch,
        liners = listOf(Liner(id = "L1", startFromAftMm = 40f * inch, lengthMm = 30f * inch, odMm = 11f * inch)),
        bodies = listOf(Body(id = "B1", startFromAftMm = 70f * inch, lengthMm = 130f * inch, diaMm = 10f * inch)),
    )

    private fun wearSheet(record: WearRecord): List<String> {
        val c = SheetTextRecorder(PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat())
        composeWearPdf(
            page = pageOnCanvas(c, PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT),
            spec = spec, project = ProjectInfo(), unit = UnitSystem.INCHES,
            displayUnits = DisplayUnits(UnitSystem.INCHES), pdfPrefs = PdfPrefs(),
            resolvedComponents = resolveComponents(spec), wearRecord = record,
        )
        return c.strings
    }

    private fun record(label: String) = WearRecord(
        spots = listOf(WearSpot(id = "p", linerId = "B1", startMm = 20f * inch, lengthMm = 10f * inch, label = label)),
        diaReadings = listOf(WearDiaReading(componentId = "B1", axialMm = 25f * inch, diaMm = 9.95f * inch)),
        stripComponentIds = listOf("L1", "B1"),
    )

    @Test fun `the wear sheet prints a zone's name under its band and in its strip title`() {
        val drawn = wearSheet(record("Packing"))
        assertTrue("name under the band: $drawn", "Packing" in drawn)
        assertTrue("strip title: $drawn", drawn.any { it.contains("(Packing)") })
    }

    @Test fun `an unnamed area prints no name`() {
        val drawn = wearSheet(record(""))
        assertFalse(drawn.any { it.contains("Packing") || it.contains("()") })
    }
}
