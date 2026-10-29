package com.android.shaftschematic.ui.util

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.collidingIds
import com.android.shaftschematic.model.withNewOal
import com.android.shaftschematic.ui.nav.blockingExportError
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [specIssues] — the ONE problem list behind the warning banner, the preview's red marks and the
 * export gate's overlap message. Each line names its parts by their card titles (on-device report:
 * "1 component extends past shaft length" left the user unable to tell which part was wrong).
 */
class SpecIssuesTest {

    private val mm = UnitSystem.MILLIMETERS
    private val inch = UnitSystem.INCHES

    /** The on-device job at its original 239" OAL, its mid taper labelled as on the device. */
    private val royalAtlantic: ShaftSpec = run {
        val spec = ShaftDocCodec.decode(
            requireNotNull(javaClass.classLoader?.getResource("fixtures/mid_taper_pac_fish.shaft")).readText(),
        ).spec
        spec.copy(
            tapers = spec.tapers.map {
                if (it.startFromAftMm > 0f) it.copy(label = "Body size transition taper") else it
            },
        )
    }

    private val midTaperId = royalAtlantic.tapers.single { it.startFromAftMm > 0f }.id
    private val fwdLinerId = royalAtlantic.liners.maxBy { it.startFromAftMm }.id
    private val fwdBodyId = royalAtlantic.bodies.single().id

    // ── The on-device case ────────────────────────────────────────────────────

    @Test
    fun `the job is clean at its original length`() {
        assertEquals(emptyList<SpecIssue>(), specIssues(royalAtlantic, inch))
    }

    /**
     * The OAL corrected from 239" to 234 3/16" carries the FWD-measured liner 4 13/16" aft into the
     * AFT-measured mid taper, and leaves the AFT-placed FWD body hanging that far past the end.
     */
    @Test
    fun `an OAL correction names the overlap and the overhang`() {
        val corrected = royalAtlantic.withNewOal(234.1875f * 25.4f)

        val issues = specIssues(corrected, inch)

        assertEquals(
            listOf(
                "FWD Liner overlaps Body size transition taper",
                "Body #1 runs 4 13/16\" past the FWD end",
            ),
            issues.map { it.message },
        )
        assertEquals(listOf(SpecIssueKind.OVERLAP, SpecIssueKind.PAST_FWD_END), issues.map { it.kind })
        assertEquals("the overlap line selects the liner first", listOf(fwdLinerId, midTaperId), issues[0].componentIds)
        assertEquals(listOf(fwdBodyId), issues[1].componentIds)
    }

    @Test
    fun `the overlap mark is the shared stretch and the overhang mark runs from the OAL`() {
        val corrected = royalAtlantic.withNewOal(234.1875f * 25.4f)
        val (overlap, past) = specIssues(corrected, inch)
        val liner = corrected.liners.single { it.id == fwdLinerId }
        val taper = corrected.tapers.single { it.id == midTaperId }
        val body = corrected.bodies.single()

        assertEquals(liner.startFromAftMm, overlap.markStartMm, 1e-3f)
        assertEquals(taper.startFromAftMm + taper.lengthMm, overlap.markEndMm, 1e-3f)
        assertNull(overlap.boundMm)
        assertEquals(corrected.overallLengthMm, past.markStartMm, 1e-3f)
        assertEquals(body.startFromAftMm + body.lengthMm, past.markEndMm, 1e-3f)
        assertEquals(corrected.overallLengthMm, past.boundMm!!, 1e-3f)
    }

    @Test
    fun `the export gate names the same overlap`() {
        val corrected = royalAtlantic.withNewOal(234.1875f * 25.4f)
        val expected = "Can't export: FWD Liner overlaps Body size transition taper"

        assertEquals(expected, overlapExportMessage(corrected))
        assertEquals(expected, blockingExportError(corrected))
        assertEquals(expected, exportPdfGate(corrected, corrected.collidingIds()).disabledMessage)
    }

    // ── Wording and order ─────────────────────────────────────────────────────

    @Test
    fun `a part before the AFT end says how far, in the document unit`() {
        val th = Threads(id = "th", startFromAftMm = -50.8f, lengthMm = 100f, majorDiaMm = 60f, pitchMm = 3f)
        val spec = ShaftSpec(overallLengthMm = 2000f, threads = listOf(th))

        val issue = specIssues(spec, inch).single()

        assertEquals("AFT Thread starts 2\" before the AFT end", issue.message)
        assertEquals(SpecIssueKind.PAST_AFT_END, issue.kind)
        assertEquals(-50.8f, issue.markStartMm, 1e-4f)
        assertEquals(0f, issue.markEndMm, 1e-4f)
        assertEquals(0f, issue.boundMm!!, 0f)
    }

    @Test
    fun `a tiny segment is named and marks nothing`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 500f, diaMm = 50f),
                Body(id = "b2", startFromAftMm = 500f, lengthMm = 0.5f, diaMm = 50f),
            ),
        )

        val issue = specIssues(spec, mm).single()

        assertEquals("Body #2 is shorter than 1 mm", issue.message)
        assertEquals(issue.markStartMm, issue.markEndMm, 0f)
    }

    @Test
    fun `overlaps come first, then the FWD end, the AFT end and tiny segments`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b", startFromAftMm = 300f, lengthMm = 0.5f, diaMm = 50f)),
            tapers = listOf(Taper(id = "t", startFromAftMm = 900f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 60f)),
            liners = listOf(
                Liner(id = "l1", startFromAftMm = -20f, lengthMm = 200f, odMm = 90f),
                Liner(id = "l2", startFromAftMm = 150f, lengthMm = 100f, odMm = 90f),
            ),
        )

        assertEquals(
            listOf(
                SpecIssueKind.OVERLAP,
                SpecIssueKind.PAST_FWD_END,
                SpecIssueKind.PAST_AFT_END,
                SpecIssueKind.TINY_SEGMENT,
            ),
            specIssues(spec, mm).map { it.kind },
        )
    }

    @Test
    fun `each colliding pair is one line, ordered by where the overlap starts`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            tapers = listOf(Taper(id = "t", startFromAftMm = 0f, lengthMm = 300f, startDiaMm = 80f, endDiaMm = 60f)),
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 200f, lengthMm = 300f, odMm = 90f),
                Liner(id = "l2", startFromAftMm = 400f, lengthMm = 300f, odMm = 90f),
            ),
        )

        val overlaps = specIssues(spec, mm).filter { it.kind == SpecIssueKind.OVERLAP }

        assertEquals(listOf(listOf("l1", "t"), listOf("l1", "l2")), overlaps.map { it.componentIds })
        val titles = componentCardTitles(spec)
        assertEquals("${titles["l1"]} overlaps ${titles["t"]}", overlaps[0].message)
    }

    @Test
    fun `a body under a liner is never an overlap`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 1000f, diaMm = 50f)),
            liners = listOf(Liner(startFromAftMm = 200f, lengthMm = 300f, odMm = 90f)),
        )
        assertEquals(emptyList<SpecIssue>(), specIssues(spec, mm))
    }

    @Test
    fun `an excluded thread outside the shaft is never a problem`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(
                Threads(startFromAftMm = -100f, lengthMm = 100f, majorDiaMm = 60f, pitchMm = 3f, excludeFromOAL = true),
            ),
        )
        assertEquals(emptyList<SpecIssue>(), specIssues(spec, mm))
    }

    @Test
    fun `a not-yet-typed OAL has no ends to run past`() {
        val spec = ShaftSpec(bodies = listOf(Body(startFromAftMm = -5f, lengthMm = 100f, diaMm = 50f)))
        assertFalse(specIssues(spec, mm).any { it.kind == SpecIssueKind.PAST_AFT_END || it.kind == SpecIssueKind.PAST_FWD_END })
    }

    @Test
    fun `a user label names the part`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 900f, lengthMm = 200f, diaMm = 50f, label = "SKF section")),
        )
        assertEquals(listOf("SKF section runs 100 mm past the FWD end"), specIssues(spec, mm).map { it.message })
    }

    @Test
    fun `several overlaps count the rest on the export message`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 0f, lengthMm = 300f, odMm = 90f),
                Liner(id = "l2", startFromAftMm = 200f, lengthMm = 300f, odMm = 90f),
                Liner(id = "l3", startFromAftMm = 400f, lengthMm = 300f, odMm = 90f),
            ),
        )
        assertTrue(overlapExportMessage(spec)!!.endsWith(" (+1 more)"))
        assertNull(overlapExportMessage(ShaftSpec(overallLengthMm = 1000f)))
    }
}
