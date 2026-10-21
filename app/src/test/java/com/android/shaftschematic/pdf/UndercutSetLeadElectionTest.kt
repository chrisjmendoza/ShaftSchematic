package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.UNDERCUT_SET_REACH_MM
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.UndercutWindow
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutPageOrientation
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.model.UndercutSetDatum
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.settings.PDF_ARROW_SIZE_DEFAULT_PT
import com.android.shaftschematic.util.DualLabel
import com.android.shaftschematic.util.measureDualLabel
import com.android.shaftschematic.util.PdfPageOrientation
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The undercut sheet's S.E.T. lead and its "Sections to print" election, on the on-device job
 * (fixture `undercut_polar_ranger_stbd.shaft`: three liners, the recorded cuts on the FWD liner,
 * the AFT-most overhanging its AFT edge and the FWD-most running past its FWD edge).
 *
 * - [undercutStripSetSide] is the ONE side behind the strip title and the lead: NEAREST is the
 *   title rule the sheet printed before the choice, AFT/FWD force it.
 * - The lead continues the level-0 chain from its outer witness to that S.E.T. — a half dimension
 *   (open outboard end) when the S.E.T. is off the drawn strip, a normal span when it is on it,
 *   none when the chain already reaches it or on a started strip — and is planned WITH the chain.
 * - The election filters the strips inside [undercutSheetStrips], so the count, the page and the
 *   composer agree; every section left off prints a note, never the profile fallback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutSetLeadElectionTest {

    private val inch = 25.4f
    private val unit = UnitSystem.INCHES

    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft")).readText(),
    )
    private val spec = doc.spec
    private val resolved = resolveComponents(spec)
    private val docSpec = spec.withResolvedBodies(resolved)
    private val liners = spec.liners.sortedBy { it.startFromAftMm }

    private val twoSections: UndercutRecord = doc.undercutRecord.copy(
        undercuts = doc.undercutRecord.undercuts + Undercut(
            id = "mid-cut",
            startFromAftMm = liners[1].startFromAftMm + 9f * inch,
            lengthMm = 2.5f * inch,
            diaMm = 9.875f * inch,
            authoredReference = UndercutReference.LINER_AFT,
            referenceLinerId = liners[1].id,
        ),
    )

    private fun sheet(record: UndercutRecord = doc.undercutRecord, blank: Boolean = false) =
        undercutSheetStrips(spec, resolved, record, blank)

    private fun fwdStrip(record: UndercutRecord = doc.undercutRecord) =
        sheet(record).strips.filterIsInstance<UndercutStrip.LinerStrip>().single { it.linerId == liners[2].id }

    private fun spansOf(s: UndercutStrip, sh: UndercutSheetStrips = sheet()): List<UndercutSpanMm> =
        s.undercutIds.mapNotNull { id ->
            sh.clampedById[id]?.takeIf { !it.isEmpty }?.let { UndercutSpanMm(id, it.startMm, it.endMm) }
        }.sortedBy { it.startMm }

    private val xAt: (Float) -> Float = { mm -> mm * 0.1f }
    private val labelWidth: (DualLabel) -> Float = { it.inline().length * 4.64f }

    private fun planWithLead(
        s: UndercutStrip,
        lead: UndercutSetLead?,
        spans: List<UndercutSpanMm> = spansOf(s),
        started: Boolean = false,
    ) = planUndercutStripRails(
        s.chainStartMm, s.chainEndMm, spans, unit, dual = false, splitAtMm = emptyList(), startedStrip = started,
        xAt = xAt, labelWidth = labelWidth, setLead = lead,
    )

    // ── The side resolver ─────────────────────────────────────────────────────────────

    @Test
    fun `nearest is the title side the sheet printed for every liner strip`() {
        val blank = sheet(blank = true) // a started strip per liner: all three
        assertEquals(3, blank.strips.size)
        blank.strips.filterIsInstance<UndercutStrip.LinerStrip>().forEach { s ->
            val liner = docSpec.liners.single { it.id == s.linerId }
            val titleSide = linerAnchorForPdf(docSpec, liner)
            val side = undercutStripSetSide(s, docSpec, blank.setPositions, UndercutSetDatum.NEAREST)
            assertEquals(titleSide, side.toLinerAnchor())
        }
        val fwd = fwdStrip()
        assertEquals(
            UndercutAnchorSide.FWD_SET,
            undercutStripSetSide(fwd, docSpec, sheet().setPositions, UndercutSetDatum.NEAREST),
        )
    }

    @Test
    fun `aft and fwd force the side`() {
        val fwd = fwdStrip()
        val sets = sheet().setPositions
        assertEquals(UndercutAnchorSide.AFT_SET, undercutStripSetSide(fwd, docSpec, sets, UndercutSetDatum.AFT))
        assertEquals(UndercutAnchorSide.FWD_SET, undercutStripSetSide(fwd, docSpec, sets, UndercutSetDatum.FWD))
    }

    @Test
    fun `a bare-shaft strip's nearest side is its title's proximity rule`() {
        val sh = sheet()
        val free = UndercutStrip.FreeStrip(UndercutWindow(1000f, 1200f, listOf("x")))
        val spans = listOf(UndercutSpanMm("x", 1050f, 1100f))
        val expected = undercutAnchorFor(1050f, 1100f, sh.aftSetMm, sh.fwdSetMm).side
        assertEquals(expected, undercutStripSetSide(free, docSpec, sh.setPositions, UndercutSetDatum.NEAREST, spans))
    }

    // ── Titles ────────────────────────────────────────────────────────────────────────

    @Test
    fun `nearest titles are byte-identical to the labels printed before the choice`() {
        val blank = sheet(blank = true)
        blank.strips.filterIsInstance<UndercutStrip.LinerStrip>().forEach { s ->
            val liner = docSpec.liners.single { it.id == s.linerId }
            val side = undercutStripSetSide(s, docSpec, blank.setPositions, UndercutSetDatum.NEAREST)
            assertEquals(
                buildLinerAnchorLabel(docSpec, liner, blank.setPositions, unit),
                buildLinerAnchorLabel(docSpec, liner, blank.setPositions, unit, forced = side.toLinerAnchor()),
            )
        }
        assertEquals(
            "18 1/4\" FROM FWD S.E.T.",
            buildLinerAnchorLabel(docSpec, liners[2], sheet().setPositions, unit, forced = LinerAnchor.FWD_SET),
        )
    }

    @Test
    fun `forcing aft on the fwd liner measures the aft s e t to the liner's aft edge`() {
        val sets = sheet().setPositions
        val label = buildLinerAnchorLabel(docSpec, liners[2], sets, unit, forced = LinerAnchor.AFT_SET)
        val expectedMm = liners[2].startFromAftMm - sets.aftSETxMm.toFloat()
        assertEquals("${formatLenDimDual(expectedMm.toDouble(), unit, false)} FROM AFT S.E.T.", label)
        assertEquals("394 1/4\" FROM AFT S.E.T.", label)
    }

    // ── The lead span ─────────────────────────────────────────────────────────────────

    @Test
    fun `the fwd lead states fwd s e t minus the chain end as a half dimension`() {
        val s = fwdStrip()
        val sh = sheet()
        val rails = planWithLead(s, UndercutSetLead(UndercutAnchorSide.FWD_SET, sh.fwdSetMm, s.drawStartMm, s.drawEndMm))
        assertNotNull(rails.leadSpan)
        val lead = rails.leadSpan!!
        assertEquals(sh.fwdSetMm - s.chainEndMm, lead.valueMm, 1e-3f)
        assertEquals(formatLenDimDualLabel((sh.fwdSetMm - s.chainEndMm).toDouble(), unit, false), lead.span.label)
        assertEquals("12 3/4\"", lead.span.label.inline())
        assertTrue("the S.E.T. is off the strip", lead.openOutboard)
        assertEquals(s.chainEndMm, lead.span.startMm, 0f)
        assertEquals("stops at the drawn edge", s.drawEndMm, lead.span.endMm, 0f)
        val lay = rails.lead!!
        assertTrue(lay.openEnd)
        assertFalse(lay.openStart)
        assertEquals("rides at the FWD end of the level-0 line", lay, rails.chainWithLead.last())
        assertEquals(rails.chain.size + 1, rails.chainWithLead.size)
    }

    @Test
    fun `no lead when draw to the shaft end already chained to the s e t`() {
        val reached = fwdStrip(doc.undercutRecord.copy(drawToSet = true))
        val sh = sheet(doc.undercutRecord.copy(drawToSet = true))
        assertEquals("the chain ends on the S.E.T.", sh.fwdSetMm, reached.chainEndMm, 0.01f)
        assertNull(
            buildUndercutSetLeadSpan(
                reached.chainStartMm, reached.chainEndMm,
                UndercutSetLead(UndercutAnchorSide.FWD_SET, sh.fwdSetMm, reached.drawStartMm, reached.drawEndMm),
                unit,
            ),
        )
        // The reach constant is what carried it there.
        assertTrue(UNDERCUT_SET_REACH_MM > 0f)
    }

    @Test
    fun `no lead on a started strip`() {
        val started = sheet(blank = true).strips.first()
        val sh = sheet()
        val rails = planWithLead(
            started,
            UndercutSetLead(UndercutAnchorSide.FWD_SET, sh.fwdSetMm, started.drawStartMm, started.drawEndMm),
            spans = emptyList(), started = true,
        )
        assertNull(rails.leadSpan)
        assertNull(rails.lead)
    }

    @Test
    fun `an s e t on the drawn strip gets a normal two-headed span`() {
        val lead = buildUndercutSetLeadSpan(
            100f, 400f, UndercutSetLead(UndercutAnchorSide.FWD_SET, setMm = 450f, drawStartMm = 80f, drawEndMm = 470f),
            unit,
        )!!
        assertFalse(lead.openOutboard)
        assertEquals(450f, lead.span.endMm, 0f)
        val off = buildUndercutSetLeadSpan(
            100f, 400f, UndercutSetLead(UndercutAnchorSide.FWD_SET, 900f, 80f, 470f), unit,
        )!!
        assertTrue(off.openOutboard)
        assertEquals(470f, off.span.endMm, 0f)
        assertEquals(500f, off.valueMm, 1e-3f)
    }

    @Test
    fun `the aft lead runs from the chain start and opens at the drawn start`() {
        val lead = buildUndercutSetLeadSpan(
            100f, 400f, UndercutSetLead(UndercutAnchorSide.AFT_SET, -500f, 60f, 470f), unit,
        )!!
        assertEquals(60f, lead.span.startMm, 0f)
        assertEquals(100f, lead.span.endMm, 0f)
        assertEquals(600f, lead.valueMm, 1e-3f)
        assertTrue(lead.openOutboard)
    }

    @Test
    fun `no lead when the s e t lies on the wrong side of the datum`() {
        assertNull(
            buildUndercutSetLeadSpan(100f, 400f, UndercutSetLead(UndercutAnchorSide.FWD_SET, 350f, 80f, 470f), unit),
        )
        assertNull(
            buildUndercutSetLeadSpan(100f, 400f, UndercutSetLead(UndercutAnchorSide.AFT_SET, 150f, 80f, 470f), unit),
        )
    }

    @Test
    fun `the lead is planned with the chain and a fallback lead value reserves a row`() {
        // One 40 mm cut in a 100 mm chain at 1 pt/mm: every chain value (6 pt wide) seats in its
        // break; the 10 mm lead cannot, so its value falls back — above the line, the chain being
        // the only label level.
        val spans = listOf(UndercutSpanMm("c", 30f, 70f))
        fun plan(lead: UndercutSetLead?) = planUndercutStripRails(
            0f, 100f, spans, unit, dual = false, splitAtMm = emptyList(), startedStrip = false,
            xAt = { it }, labelWidth = { 6f }, setLead = lead,
        )
        val without = plan(null)
        assertTrue(without.chain.all { it.seatsInBreak })
        assertEquals(0, without.rows.aboveRows)

        val with = plan(UndercutSetLead(UndercutAnchorSide.FWD_SET, setMm = 2000f, drawStartMm = -10f, drawEndMm = 110f))
        val lay = with.lead!!
        assertFalse("10 pt cannot hold the value", lay.seatsInBreak)
        assertEquals("the lead's fallback row is reserved", 1, with.rows.aboveRows)
        assertEquals("the chain itself is unchanged", without.chain, with.chain)
    }

    // ── The lead's room: its value seats in its own line ─────────────────────────────

    @Test
    fun `the job's fwd lead seats inline on the printed strip, full width and in a grid cell`() {
        val sh = sheet()
        val strip = fwdStrip()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val diaText = Paint(text).apply { textSize = 8f }
        val undercuts = doc.undercutRecord.undercuts
        // A portrait strip (540 pt of content), a landscape full-width strip, and a landscape grid cell.
        listOf(36f to 576f, 36f to 756f, 36f to 390f).forEach { (left, right) ->
            val plan = requireNotNull(
                planUndercutDetailStrip(
                    sh.surfaceSegs, strip, undercuts, sh.clampedById, left, right, unit, text, diaText,
                    sh.shaftExtentMm, startedStrip = false, dual = false, dualStacked = false,
                    railSplitAtMm = emptyList(), arrowSizePt = PDF_ARROW_SIZE_DEFAULT_PT,
                    leadSide = UndercutAnchorSide.FWD_SET, aftSetMm = sh.aftSetMm, fwdSetMm = sh.fwdSetMm,
                ),
            )
            val lead = requireNotNull(plan.rails.lead) { "a lead on [$left, $right]" }
            assertTrue("the lead value seats in its line on [$left, $right]", lead.seatsInBreak)
            assertTrue(lead.openEnd)
            // The open tail past the value is at least the named tail.
            val tail = lead.x1Pt - (lead.labelCxPt + dimWidth(text, lead.label) * 0.5f + DIM_BREAK_TEXT_PAD_PT)
            assertTrue("tail $tail on [$left, $right]", tail >= UNDERCUT_SET_LEAD_TAIL_PT - 0.01f)
            // The drawn range only grew on the FWD (lead) side.
            assertTrue(plan.drawEndMm > strip.chainEndMm)
        }
    }

    @Test
    fun `a strip without a lead draws exactly the pad rule's range`() {
        val base = computeUndercutStripDrawRange(100f, 400f, 120f, 380f, 36f, 390f, 5000f)
        assertEquals(base, computeUndercutStripDrawRange(100f, 400f, 120f, 380f, 36f, 390f, 5000f, leadSide = null, leadRoomPt = 80f))
        assertEquals(base, computeUndercutStripDrawRange(100f, 400f, 120f, 380f, 36f, 390f, 5000f, leadSide = UndercutAnchorSide.FWD_SET, leadRoomPt = 0f))
        val widened = computeUndercutStripDrawRange(
            100f, 400f, 120f, 380f, 36f, 390f, 5000f, leadSide = UndercutAnchorSide.FWD_SET, leadRoomPt = 80f,
        )
        assertTrue("the lead side grew", widened.endMm > base.endMm)
        assertTrue("the other side never narrows", widened.startMm <= base.startMm)
        // At the final scale the lead side holds its room.
        val avail = 390f - 36f - 2f * UNDERCUT_STRIP_EDGE_INSET_PT
        val ptPerMm = (avail / (widened.endMm - widened.startMm))
            .coerceIn(WEAR_STRIP_MIN_PT_PER_MM, WEAR_STRIP_MAX_PT_PER_MM)
        assertTrue((widened.endMm - 380f) * ptPerMm >= 80f - 0.01f)
        assertTrue("the other side keeps its minimum pad", (120f - widened.startMm) * ptPerMm >= UNDERCUT_STRIP_MIN_PAD_PT - 0.01f)
    }

    private fun dimWidth(text: Paint, label: DualLabel): Float {
        val dimText = Paint(text).apply { textSize = (text.textSize - 2f).coerceAtLeast(7f) }
        return dimText.measureDualLabel(label, false)
    }

    // ── The election ──────────────────────────────────────────────────────────────────

    @Test
    fun `a liner section left off drops out and auto counts the printed sections`() {
        val rec = twoSections.copy(excludedLinerIds = listOf(liners[1].id))
        val sh = sheet(rec)
        assertEquals(2, sh.allStrips.size)
        assertEquals(1, sh.strips.size)
        assertEquals(liners[2].id, (sh.strips.single() as UndercutStrip.LinerStrip).linerId)
        assertEquals(1, undercutSheetStripCount(spec, resolved, rec, blankValues = false))
        assertEquals(PdfPageOrientation.LANDSCAPE, undercutSheetOrientation(spec, resolved, rec, blankValues = false))
        assertEquals(PdfPageOrientation.PORTRAIT, undercutSheetOrientation(spec, resolved, twoSections, blankValues = false))
    }

    @Test
    fun `the election governs a blank draft's started strips too`() {
        val rec = doc.undercutRecord.copy(excludedLinerIds = listOf(liners[0].id))
        assertEquals(2, undercutSheetStripCount(spec, resolved, rec, blankValues = true))
    }

    @Test
    fun `a bare-shaft section is left off only when every member cut is listed`() {
        val free = UndercutStrip.FreeStrip(UndercutWindow(0f, 10f, listOf("a", "b")))
        assertTrue(isUndercutStripPrinted(free, UndercutRecord()))
        assertTrue(isUndercutStripPrinted(free, UndercutRecord(excludedFreeCutIds = listOf("a"))))
        assertFalse(isUndercutStripPrinted(free, UndercutRecord(excludedFreeCutIds = listOf("a", "b", "orphan"))))
        // A cut added to the excluded window brings the whole window back.
        val grown = UndercutStrip.FreeStrip(UndercutWindow(0f, 12f, listOf("a", "b", "new")))
        assertTrue(isUndercutStripPrinted(grown, UndercutRecord(excludedFreeCutIds = listOf("a", "b"))))
        // Liner ids never reach a bare-shaft strip, nor cut ids a liner strip.
        assertTrue(isUndercutStripPrinted(free, UndercutRecord(excludedLinerIds = listOf("a", "b"))))
    }

    @Test
    fun `a new cut in an excluded bare-shaft window brings the section back on the sheet`() {
        val gap = (liners[1].startFromAftMm + liners[1].lengthMm + liners[2].startFromAftMm) * 0.5f
        val bare = Undercut(id = "bare-1", startFromAftMm = gap, lengthMm = inch, diaMm = 9f * inch)
        val rec = twoSections.copy(undercuts = twoSections.undercuts + bare, excludedFreeCutIds = listOf("bare-1"))
        assertEquals(3, sheet(rec).allStrips.size)
        assertEquals(2, sheet(rec).strips.size)
        val bare2 = Undercut(id = "bare-2", startFromAftMm = gap + 1.5f * inch, lengthMm = inch, diaMm = 9f * inch)
        val grown = rec.copy(undercuts = rec.undercuts + bare2)
        val free = sheet(grown).allStrips.filterIsInstance<UndercutStrip.FreeStrip>().single()
        assertEquals(listOf("bare-1", "bare-2"), free.undercutIds)
        assertEquals(3, sheet(grown).strips.size)
    }

    @Test
    fun `every section left off prints the note on a landscape page and never the profile`() {
        val rec = twoSections.copy(
            excludedLinerIds = liners.map { it.id },
            pageOrientation = UndercutPageOrientation.PORTRAIT,
        )
        val sh = sheet(rec)
        assertTrue(sh.noneSelected)
        assertEquals(0, undercutSheetStripCount(spec, resolved, rec, blankValues = false))
        assertEquals(PdfPageOrientation.LANDSCAPE, undercutSheetOrientation(spec, resolved, rec, blankValues = false))

        val texts = composeTexts(rec)
        assertTrue(texts.toString(), UNDERCUT_NONE_SELECTED_NOTE in texts)
        assertTrue("the header still prints", texts.any { it.contains(UNDERCUT_DOC_TITLE) })
        assertFalse("no direction row — no strips and no profile", texts.any { it.contains("AFT") && it.contains("←") })
        assertFalse("no OAL line — the profile fallback never draws", texts.any { it.startsWith("OAL") })

        // With one section back the sheet draws strips again.
        val one = composeTexts(rec.copy(excludedLinerIds = listOf(liners[1].id)))
        assertFalse(UNDERCUT_NONE_SELECTED_NOTE in one)
    }

    /** Every string [composeUndercutPdf] draws for [record], through a recording canvas. */
    private fun composeTexts(record: UndercutRecord): List<String> {
        val texts = mutableListOf<String>()
        val o = undercutSheetOrientation(spec, resolved, record, blankValues = false)
        val bmp = Bitmap.createBitmap(o.widthPt, o.heightPt, Bitmap.Config.ARGB_8888)
        val canvas = object : Canvas(bmp) {
            override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
                texts += text
                super.drawText(text, x, y, paint)
            }
        }
        val info = PdfDocument.PageInfo.Builder(o.widthPt, o.heightPt, 1).create()
        val ctor = PdfDocument.Page::class.java.getDeclaredConstructor(Canvas::class.java, PdfDocument.PageInfo::class.java)
        ctor.isAccessible = true
        composeUndercutPdf(
            ctor.newInstance(canvas, info), spec, ProjectInfo(jobNumber = "1"), unit,
            resolvedComponents = resolved, undercutRecord = record,
        )
        return texts
    }
}
