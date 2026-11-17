package com.android.shaftschematic.pdf

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.doc.mateDuplicate
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.SchematicSection
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.screen.buildSectionFilename
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section sheets: the window a stored section resolves to, what of the full sheet's rails it
 * prints, and how a section travels with its document.
 */
class SchematicSectionTest {

    private val inch = 25.4f

    /** 100" shaft: a taper at each end, a liner near each end, bare 6" shaft between. */
    private val spec = ShaftSpec(
        overallLengthMm = 100f * inch,
        tapers = listOf(
            Taper(id = "TA", startFromAftMm = 0f, lengthMm = 10f * inch, startDiaMm = 4f * inch, endDiaMm = 6f * inch),
            Taper(id = "TF", startFromAftMm = 90f * inch, lengthMm = 10f * inch, startDiaMm = 6f * inch, endDiaMm = 4f * inch),
        ),
        liners = listOf(
            Liner(id = "L1", startFromAftMm = 20f * inch, lengthMm = 10f * inch, odMm = 6.5f * inch),
            Liner(id = "L2", startFromAftMm = 70f * inch, lengthMm = 10f * inch, odMm = 6.5f * inch),
        ),
        autoBodyDiaMm = 6f * inch,
    )
    private val resolved = resolveComponents(spec)
    private fun rails() =
        schematicRailSpans(spec, UnitSystem.INCHES, DisplayUnits.single(UnitSystem.INCHES), PdfPrefs())

    @Test
    fun `a section to the shaft end stops there and takes a stub at its cut end`() {
        val s = requireNotNull(resolveSheetSection(spec, resolved, "L2", null, "FWD END"))
        assertEquals(70f * inch, s.coreStartMm, 0.01f)
        assertEquals(100f * inch, s.coreEndMm, 0.01f)
        assertEquals(100f * inch, s.endMm, 0.01f)
        // Three quarters of the 6" stock it cuts through.
        assertEquals((70f - 4.5f) * inch, s.startMm, 0.5f)
        assertTrue(s.cutsAft(spec))
        assertFalse(s.cutsFwd(spec))
        assertEquals("FWD END", s.title)
    }

    @Test
    fun `the two parts may be named in either order`() {
        val a = resolveSheetSection(spec, resolved, "L1", "L2")
        val b = resolveSheetSection(spec, resolved, "L2", "L1")
        assertNotNull(a)
        assertEquals(a, b)
        assertEquals(20f * inch, a!!.coreStartMm, 0.01f)
        assertEquals(80f * inch, a.coreEndMm, 0.01f)
    }

    @Test
    fun `a section naming a part that is gone does not resolve`() {
        assertNull(resolveSheetSection(spec, resolved, "L2", "NOPE"))
        assertNull(resolveSheetSection(spec, resolved, "NOPE", null))
    }

    @Test
    fun `a section starting on the shaft end has no cut there`() {
        val s = requireNotNull(resolveSheetSection(spec, resolved, "TA", "L1"))
        assertEquals(0f, s.startMm, 0.01f)
        assertFalse(s.cutsAft(spec))
        assertTrue(s.cutsFwd(spec))
    }

    @Test
    fun `what a stub only clips is drawn but is not what the sheet is about`() {
        // L2 alone: its FWD stub (4.5 in) stops short of the FWD taper at 90 in.
        val s = requireNotNull(resolveSheetSection(spec, resolved, "L2", "L2"))
        assertEquals(listOf("L2"), spec.inSection(s).liners.map { it.id })
        assertTrue(spec.inSection(s).tapers.isEmpty())
        // A window that does clip the taper draws it, and still prints nothing for it.
        val wide = s.copy(endMm = 92f * inch)
        assertEquals(listOf("TF"), spec.inSection(wide).tapers.map { it.id })
        assertTrue(spec.inSectionCore(wide).tapers.isEmpty())
        val names = labelSpansInSection(componentLabelSpans(spec, titlesDefault = true), wide).map { it.text }
        assertEquals(1, names.size)
    }

    @Test
    fun `a section prints the rails of its own parts and none to a part it does not show`() {
        val full = rails()
        val s = requireNotNull(resolveSheetSection(spec, resolved, "L2", null))
        val cut = full.inSection(s)

        assertFalse("a section short of the AFT end prints no overall length", cut.printsOal)
        assertTrue(cut.topSpans.isEmpty())
        assertTrue(cut.assignments.isNotEmpty())
        assertTrue(cut.assignments.size < full.assignments.size)
        fun shaftMm(x: Double) = (x + full.win.measureStartMm).toFloat()
        cut.assignments.forEach { (_, span) ->
            val a = shaftMm(span.x1Mm)
            val b = shaftMm(span.x2Mm)
            assertTrue("a kept span touches the section's own parts", s.coreHolds(a) || s.coreHolds(b))
            // Nothing runs to L1 (20 in to 30 in): that liner is not on this sheet.
            listOf(a, b).forEach { assertFalse(it > 19f * inch && it < 31f * inch) }
        }
        // Rails close up: consecutive from 0.
        assertEquals((0..cut.maxRail).toList(), cut.assignments.map { it.rail }.distinct().sorted())
        // Values are the full sheet's own.
        val fullLabels = full.assignments.map { it.span.label }.toSet()
        assertTrue(cut.assignments.all { it.span.label in fullLabels })
    }

    @Test
    fun `the whole shaft as a section keeps every rail and the overall length`() {
        val full = rails()
        val s = requireNotNull(resolveSheetSection(spec, resolved, null, null))
        val all = full.inSection(s)
        assertTrue(all.printsOal)
        assertEquals(full.assignments.size, all.assignments.size)
    }

    @Test
    fun `sections ride the envelope and a mate keeps only the original drawing's`() {
        val sections = listOf(
            SchematicSection("s1", "FWD end finish", firstId = "L2", lastId = null),
            SchematicSection("s2", "Final FWD", firstId = "L2", lastId = null, final = true),
        )
        val raw = ShaftDocCodec.encodeV1(ShaftDocCodec.ShaftDocV1(spec = spec, sheetSections = sections))
        assertEquals(sections, ShaftDocCodec.decode(raw).sheetSections)

        val mate = mateDuplicate(
            ShaftDocCodec.decodeEnvelope(raw),
            jobNumber = "1", customer = "c", vessel = "v", position = ShaftPosition.STBD,
        )
        assertEquals(listOf("s1"), mate.sheetSections.map { it.id })
    }

    @Test
    fun `a file without sections decodes to none`() {
        val raw = ShaftDocCodec.encodeV1(ShaftDocCodec.ShaftDocV1(spec = spec))
        assertTrue(ShaftDocCodec.decode(raw).sheetSections.isEmpty())
    }

    @Test
    fun `a section's file is named for it`() {
        val plain = buildSectionFilename("Curtin Marine", "Kelley C", "935062", ShaftPosition.PORT, "FwdEnd", final = false)
        assertTrue(plain, plain.endsWith("_section_FwdEnd.pdf"))
        assertFalse(plain.contains("_Final"))
        val final = buildSectionFilename("c", "v", "1", ShaftPosition.PORT, "", final = true, blankDraft = true)
        assertTrue(final, final.endsWith("_Final_section_section_BlankDraft.pdf"))
        assertFalse(buildSectionFilename("c", "v", "1", ShaftPosition.PORT, "a/b:c", final = false).contains("/"))
    }
}
