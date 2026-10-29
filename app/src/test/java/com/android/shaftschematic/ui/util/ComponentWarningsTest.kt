package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Non-blocking warning rules from `docs/VALIDATION_RULES.md` §3–4.
 * Pure-function coverage: each rule's trigger, below-threshold, boundary, and sentinel-skip.
 */
class ComponentWarningsTest {

    private val STEP = "Large Ø step vs adjacent body"
    private val LINER_UNDER = "Liner OD smaller than shaft Ø beneath it"
    private val SHORT = "Very short segment (< 1 mm)"
    private val LENGTH_SANITY = "Length exceeds 15 m — check for a typo"
    private val DIA_SANITY = "Diameter exceeds 1 m — check for a typo"
    private val PAST_OAL_1000 = "Extends past shaft length (OAL 1000 mm)"

    /** The banner's lines for [spec] on a millimetre document. */
    private fun banner(spec: ShaftSpec) = specIssues(spec, UnitSystem.MILLIMETERS).map { it.message }

    // Mirrors the private thresholds in ComponentWarnings.kt.
    private val MAX_LEN = 15_000f
    private val MAX_DIA = 1_000f

    /* ── §3.2 Body Ø discontinuity vs adjacent body ─────────────────────────── */

    @Test
    fun `body step warns when adjacent body dia ratio exceeds threshold`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val b = Body(startFromAftMm = 100f, lengthMm = 100f, diaMm = 100f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, b))
        assertTrue(bodyWarningMessages(spec, a, UnitSystem.MILLIMETERS).contains(STEP))
        assertTrue(bodyWarningMessages(spec, b, UnitSystem.MILLIMETERS).contains(STEP))
    }

    @Test
    fun `body step silent below ratio threshold`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 100f)
        val b = Body(startFromAftMm = 100f, lengthMm = 100f, diaMm = 140f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, b))
        assertFalse(bodyWarningMessages(spec, a, UnitSystem.MILLIMETERS).contains(STEP))
    }

    @Test
    fun `body step silent exactly at ratio 1_5`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 100f)
        val b = Body(startFromAftMm = 100f, lengthMm = 100f, diaMm = 150f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, b))
        assertFalse(bodyWarningMessages(spec, a, UnitSystem.MILLIMETERS).contains(STEP))
    }

    @Test
    fun `body step honors adjacency eps at boundary`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        // gap of exactly 0.5 mm — still adjacent
        val near = Body(startFromAftMm = 100.5f, lengthMm = 100f, diaMm = 100f)
        val nearSpec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, near))
        assertTrue(bodyWarningMessages(nearSpec, a, UnitSystem.MILLIMETERS).contains(STEP))
        // gap of 0.6 mm — no longer adjacent
        val far = Body(startFromAftMm = 100.6f, lengthMm = 100f, diaMm = 100f)
        val farSpec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, far))
        assertFalse(bodyWarningMessages(farSpec, a, UnitSystem.MILLIMETERS).contains(STEP))
    }

    @Test
    fun `body step skips zero-dia sentinel neighbor`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 100f)
        val b = Body(startFromAftMm = 100f, lengthMm = 100f, diaMm = 0f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, b))
        assertFalse(bodyWarningMessages(spec, a, UnitSystem.MILLIMETERS).contains(STEP))
        assertFalse(bodyWarningMessages(spec, b, UnitSystem.MILLIMETERS).contains(STEP))
    }

    @Test
    fun `body short segment still warns`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 0.5f, diaMm = 50f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a))
        assertTrue(bodyWarningMessages(spec, a, UnitSystem.MILLIMETERS).contains(SHORT))
    }

    /* ── §3.3 Taper warnings ────────────────────────────────────────────────── */

    @Test
    fun `taper short segment warns`() {
        val taper = Taper(startFromAftMm = 0f, lengthMm = 0.5f, startDiaMm = 80f, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 1000f, tapers = listOf(taper))
        assertTrue(taperWarningMessages(spec, taper, UnitSystem.MILLIMETERS).contains(SHORT))
    }

    // Pins the removal of the taper-vs-body Ø mismatch advisory: a large
    // face-vs-body Ø difference is visible in the drawing and must NOT produce a warning.
    @Test
    fun `taper adjacent body dia mismatch produces no warning`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 100f)
        val taper = Taper(startFromAftMm = 100f, lengthMm = 100f, startDiaMm = 80f, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), tapers = listOf(taper))
        assertTrue(taperWarningMessages(spec, taper, UnitSystem.MILLIMETERS).isEmpty())
    }

    /* ── §3.5 Liner OD below underlying body ────────────────────────────────── */

    @Test
    fun `liner warns when od below overlapping body dia`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 100f)
        val liner = Liner(startFromAftMm = 100f, lengthMm = 200f, odMm = 80f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), liners = listOf(liner))
        assertTrue(linerWarningMessages(spec, liner, UnitSystem.MILLIMETERS).contains(LINER_UNDER))
    }

    @Test
    fun `liner silent when not overlapping any body`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 100f)
        val liner = Liner(startFromAftMm = 600f, lengthMm = 100f, odMm = 80f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), liners = listOf(liner))
        assertFalse(linerWarningMessages(spec, liner, UnitSystem.MILLIMETERS).contains(LINER_UNDER))
    }

    @Test
    fun `liner silent when od equals body dia`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 100f)
        val liner = Liner(startFromAftMm = 100f, lengthMm = 200f, odMm = 100f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), liners = listOf(liner))
        assertFalse(linerWarningMessages(spec, liner, UnitSystem.MILLIMETERS).contains(LINER_UNDER))
    }

    @Test
    fun `liner silent over zero-dia body sentinel`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 0f)
        val liner = Liner(startFromAftMm = 100f, lengthMm = 200f, odMm = 80f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), liners = listOf(liner))
        assertFalse(linerWarningMessages(spec, liner, UnitSystem.MILLIMETERS).contains(LINER_UNDER))
    }

    /* ── Threads unchanged ──────────────────────────────────────────────────── */

    @Test
    fun `thread zero pitch warns`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = 40f, pitchMm = 0f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        assertTrue(threadWarningMessages(spec, th, UnitSystem.MILLIMETERS).contains("Zero pitch — thread renders flat"))
    }

    @Test
    fun `thread short segment warns`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 0.5f, majorDiaMm = 40f, pitchMm = 2f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        assertTrue(threadWarningMessages(spec, th, UnitSystem.MILLIMETERS).contains(SHORT))
    }

    /* ── §2.1 Implausible length/diameter sanity checks ────────────────────── */

    @Test
    fun `body warns on implausible length`() {
        val a = Body(startFromAftMm = 0f, lengthMm = MAX_LEN + 1f, diaMm = 50f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN + 1000f, bodies = listOf(a))
        assertTrue(bodyWarningMessages(spec, a, UnitSystem.MILLIMETERS).contains(LENGTH_SANITY))
    }

    @Test
    fun `body silent on length exactly at threshold`() {
        val a = Body(startFromAftMm = 0f, lengthMm = MAX_LEN, diaMm = 50f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN, bodies = listOf(a))
        assertFalse(bodyWarningMessages(spec, a, UnitSystem.MILLIMETERS).contains(LENGTH_SANITY))
    }

    @Test
    fun `body warns on implausible diameter`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = MAX_DIA + 1f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a))
        assertTrue(bodyWarningMessages(spec, a, UnitSystem.MILLIMETERS).contains(DIA_SANITY))
    }

    @Test
    fun `body silent on diameter exactly at threshold`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = MAX_DIA)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a))
        assertFalse(bodyWarningMessages(spec, a, UnitSystem.MILLIMETERS).contains(DIA_SANITY))
    }

    @Test
    fun `body implausible dia produces sanity warning plus legitimate step warning only`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = MAX_DIA + 1f)
        val b = Body(startFromAftMm = 100f, lengthMm = 100f, diaMm = 50f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, b))
        val warnings = bodyWarningMessages(spec, a, UnitSystem.MILLIMETERS)
        assertEquals(setOf(DIA_SANITY, STEP), warnings.toSet())
    }

    @Test
    fun `taper warns on implausible length`() {
        val t = Taper(startFromAftMm = 0f, lengthMm = MAX_LEN + 1f, startDiaMm = 80f, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN + 1000f, tapers = listOf(t))
        assertTrue(taperWarningMessages(spec, t, UnitSystem.MILLIMETERS).contains(LENGTH_SANITY))
    }

    @Test
    fun `taper silent on length exactly at threshold`() {
        val t = Taper(startFromAftMm = 0f, lengthMm = MAX_LEN, startDiaMm = 80f, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN, tapers = listOf(t))
        assertFalse(taperWarningMessages(spec, t, UnitSystem.MILLIMETERS).contains(LENGTH_SANITY))
    }

    @Test
    fun `taper warns on implausible diameter via either end`() {
        val setEnd = Taper(startFromAftMm = 0f, lengthMm = 100f, startDiaMm = MAX_DIA + 1f, endDiaMm = 60f)
        val letEnd = Taper(startFromAftMm = 0f, lengthMm = 100f, startDiaMm = 60f, endDiaMm = MAX_DIA + 1f)
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertTrue(taperWarningMessages(spec, setEnd, UnitSystem.MILLIMETERS).contains(DIA_SANITY))
        assertTrue(taperWarningMessages(spec, letEnd, UnitSystem.MILLIMETERS).contains(DIA_SANITY))
    }

    @Test
    fun `taper silent on diameter exactly at threshold`() {
        val t = Taper(startFromAftMm = 0f, lengthMm = 100f, startDiaMm = MAX_DIA, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 1000f, tapers = listOf(t))
        assertFalse(taperWarningMessages(spec, t, UnitSystem.MILLIMETERS).contains(DIA_SANITY))
    }

    @Test
    fun `liner warns on implausible length`() {
        val ln = Liner(startFromAftMm = 0f, lengthMm = MAX_LEN + 1f, odMm = 80f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN + 1000f, liners = listOf(ln))
        assertTrue(linerWarningMessages(spec, ln, UnitSystem.MILLIMETERS).contains(LENGTH_SANITY))
    }

    @Test
    fun `liner silent on length exactly at threshold`() {
        val ln = Liner(startFromAftMm = 0f, lengthMm = MAX_LEN, odMm = 80f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN, liners = listOf(ln))
        assertFalse(linerWarningMessages(spec, ln, UnitSystem.MILLIMETERS).contains(LENGTH_SANITY))
    }

    @Test
    fun `liner warns on implausible od`() {
        val ln = Liner(startFromAftMm = 0f, lengthMm = 100f, odMm = MAX_DIA + 1f)
        val spec = ShaftSpec(overallLengthMm = 1000f, liners = listOf(ln))
        assertTrue(linerWarningMessages(spec, ln, UnitSystem.MILLIMETERS).contains(DIA_SANITY))
    }

    @Test
    fun `liner silent on od exactly at threshold`() {
        val ln = Liner(startFromAftMm = 0f, lengthMm = 100f, odMm = MAX_DIA)
        val spec = ShaftSpec(overallLengthMm = 1000f, liners = listOf(ln))
        assertFalse(linerWarningMessages(spec, ln, UnitSystem.MILLIMETERS).contains(DIA_SANITY))
    }

    @Test
    fun `liner implausible od produces sanity warning plus legitimate underlying-body warning only`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = MAX_DIA + 500f)
        val ln = Liner(startFromAftMm = 100f, lengthMm = 200f, odMm = MAX_DIA + 1f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), liners = listOf(ln))
        val warnings = linerWarningMessages(spec, ln, UnitSystem.MILLIMETERS)
        assertEquals(setOf(DIA_SANITY, LINER_UNDER), warnings.toSet())
    }

    @Test
    fun `thread warns on implausible length`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = MAX_LEN + 1f, majorDiaMm = 40f, pitchMm = 2f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN + 1000f, threads = listOf(th))
        assertTrue(threadWarningMessages(spec, th, UnitSystem.MILLIMETERS).contains(LENGTH_SANITY))
    }

    @Test
    fun `thread silent on length exactly at threshold`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = MAX_LEN, majorDiaMm = 40f, pitchMm = 2f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN, threads = listOf(th))
        assertFalse(threadWarningMessages(spec, th, UnitSystem.MILLIMETERS).contains(LENGTH_SANITY))
    }

    @Test
    fun `thread warns on implausible major diameter`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = MAX_DIA + 1f, pitchMm = 2f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        assertTrue(threadWarningMessages(spec, th, UnitSystem.MILLIMETERS).contains(DIA_SANITY))
    }

    @Test
    fun `thread silent on major diameter exactly at threshold`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = MAX_DIA, pitchMm = 2f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        assertFalse(threadWarningMessages(spec, th, UnitSystem.MILLIMETERS).contains(DIA_SANITY))
    }

    @Test
    fun `thread implausible dia produces sanity warning plus legitimate zero-pitch warning only`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = MAX_DIA + 1f, pitchMm = 0f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        val warnings = threadWarningMessages(spec, th, UnitSystem.MILLIMETERS)
        assertEquals(setOf(DIA_SANITY, "Zero pitch — thread renders flat"), warnings.toSet())
    }

    /* ── §4.3 Spec-level tiny segments ──────────────────────────────────────── */

    @Test
    fun `spec counts tiny segments across component kinds`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 0.5f, diaMm = 50f)),
            tapers = listOf(Taper(startFromAftMm = 100f, lengthMm = 0.8f, startDiaMm = 40f, endDiaMm = 40f)),
        )
        assertEquals(
            listOf("Body #1 is shorter than 1 mm", "AFT Taper is shorter than 1 mm"),
            banner(spec).filter { it.contains("shorter than 1 mm") },
        )
    }

    @Test
    fun `spec does not count zero-length segment as tiny`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 0f, diaMm = 50f)),
            tapers = listOf(Taper(startFromAftMm = 100f, lengthMm = 200f, startDiaMm = 40f, endDiaMm = 40f)),
        )
        assertTrue(banner(spec).none { it.contains("shorter than 1 mm") })
    }

    @Test
    fun `spec skips excluded thread in tiny count`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 50f)),
            threads = listOf(
                Threads(startFromAftMm = 0f, lengthMm = 0.5f, majorDiaMm = 40f, pitchMm = 2f, excludeFromOAL = true),
            ),
        )
        assertTrue(banner(spec).none { it.contains("shorter than 1 mm") })
    }

    /* ── §4.3 The banner carries PROBLEMS only ──────────────────────────────── */

    /**
     * A shaft with no explicit bodies is perfectly ordinary — auto-fill IS the design — and used
     * to raise "No explicit bodies — shaft body is all auto-fill" here. Routed through an
     * advisory-styled banner it read as something being wrong (on-device report), so it is gone.
     * Nothing describing normal behaviour may take its place: the banner's whole value is that
     * seeing it means something needs attention.
     */
    @Test
    fun `a spec with no explicit bodies raises nothing`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(Taper(startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 60f)),
            liners = listOf(Liner(startFromAftMm = 300f, lengthMm = 200f, odMm = 60f)),
        )
        assertTrue(banner(spec).isEmpty())
    }

    @Test
    fun `a fully empty spec raises nothing`() {
        assertTrue(banner(ShaftSpec(overallLengthMm = 1000f)).isEmpty())
    }

    /** The one surviving message still fires — removing the note must not silence the banner. */
    @Test
    fun `a genuine anomaly still raises`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(Taper(startFromAftMm = 0f, lengthMm = 0.5f, startDiaMm = 80f, endDiaMm = 60f)),
        )
        assertEquals(listOf("FWD Taper is shorter than 1 mm"), banner(spec))
    }

    /* ── Past-OAL bounds advisory (shared predicate) ────────────────────────── */

    @Test
    fun `a span inside the shaft is not outside`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertFalse(outsideShaftSpan(spec, 100f, 200f))
        assertNull(pastShaftEndMessage(spec, 100f, 200f, UnitSystem.MILLIMETERS))
    }

    @Test
    fun `a span running past the end is outside`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertTrue(outsideShaftSpan(spec, 900f, 200f))
        assertEquals(
            "Extends past shaft length (OAL 1000 mm)",
            pastShaftEndMessage(spec, 900f, 200f, UnitSystem.MILLIMETERS),
        )
    }

    @Test
    fun `a negative start is outside`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertTrue(outsideShaftSpan(spec, -10f, 100f))
    }

    /** OAL 0 means "not typed yet" — there is no span to fall outside of. */
    @Test
    fun `a not-yet-set overall length has no bounds`() {
        val spec = ShaftSpec(overallLengthMm = 0f)
        assertFalse(outsideShaftSpan(spec, 900f, 500f))
        assertNull(pastShaftEndMessage(spec, 900f, 500f, UnitSystem.MILLIMETERS))
    }

    @Test
    fun `a span ending exactly at the shaft end is inside`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertFalse(outsideShaftSpan(spec, 800f, 200f))
    }

    /**
     * The eps absorbs float round-trip noise; a real overrun still fires. It is 0.01 mm — a
     * `Float` start on a 9 m shaft resolves to ~0.001 mm, and a slop of exactly that lit the
     * warning on a taper authored flush with the FWD face (on-device report).
     */
    @Test
    fun `bounds honor the eps at the boundary`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertFalse(outsideShaftSpan(spec, 800f, 200.005f))
        assertTrue(outsideShaftSpan(spec, 800f, 200.05f))
        assertFalse(outsideShaftSpan(spec, -0.005f, 100f))
        assertTrue(outsideShaftSpan(spec, -0.05f, 100f))
    }

    /** One float unit of rounding on a 30 ft shaft is inside, not past the end. */
    @Test
    fun `a flush taper on a long shaft is not past the end by float noise`() {
        val inch = 25.4f
        val oal = 367.75f * inch
        val len = 25.25f * inch
        val spec = ShaftSpec(overallLengthMm = oal)
        val start = (oal.toDouble() - len).toFloat()
        assertFalse(outsideShaftSpan(spec, start, len))
        assertFalse(outsideShaftSpan(spec, Math.nextUp(start), len))
    }

    /** Both surfaces read the ONE comparison; only the wording differs. */
    @Test
    fun `dialog and card messages agree on when but not on wording`() {
        val spec = ShaftSpec(overallLengthMm = 500f)
        assertEquals("Falls outside shaft span (OAL 500 mm)", outsideShaftSpanMessage(spec, 400f, 200f, UnitSystem.MILLIMETERS))
        assertEquals("Extends past shaft length (OAL 500 mm)", pastShaftEndMessage(spec, 400f, 200f, UnitSystem.MILLIMETERS))
        assertNull(outsideShaftSpanMessage(spec, 100f, 200f, UnitSystem.MILLIMETERS))
        assertNull(pastShaftEndMessage(spec, 100f, 200f, UnitSystem.MILLIMETERS))
    }

    @Test
    fun `body past the shaft end gets the card chip`() {
        val b = Body(startFromAftMm = 900f, lengthMm = 200f, diaMm = 50f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(b))
        assertTrue(bodyWarningMessages(spec, b, UnitSystem.MILLIMETERS).contains(PAST_OAL_1000))
    }

    /**
     * The OAL in the message prints in the document's display unit through the footer's length
     * formatter — an inch drawing quoted its OAL in millimeters (on-device report). Canonical
     * geometry stays mm; only the quoted number converts.
     */
    @Test
    fun `an inch document quotes its OAL in inches`() {
        val spec = ShaftSpec(overallLengthMm = 120f * 25.4f)
        assertEquals("Falls outside shaft span (OAL 120\")", outsideShaftSpanMessage(spec, 3000f, 100f, UnitSystem.INCHES))
        assertEquals("Extends past shaft length (OAL 120\")", pastShaftEndMessage(spec, 3000f, 100f, UnitSystem.INCHES))

        // A fractional inch OAL takes the footer's smart inch spelling, never a raw mm number.
        val fractional = ShaftSpec(overallLengthMm = 100.5f * 25.4f)
        assertEquals("Extends past shaft length (OAL 100 1/2\")", pastShaftEndMessage(fractional, 3000f, 100f, UnitSystem.INCHES))

        // Every card chip carries the same quoted unit.
        val b = Body(startFromAftMm = 3000f, lengthMm = 200f, diaMm = 50f)
        val t = Taper(startFromAftMm = 3000f, lengthMm = 200f, startDiaMm = 50f, endDiaMm = 40f)
        val l = Liner(startFromAftMm = 3000f, lengthMm = 200f, odMm = 60f)
        val th = Threads(startFromAftMm = 3000f, lengthMm = 200f, majorDiaMm = 40f, pitchMm = 2f)
        val built = spec.copy(bodies = listOf(b), tapers = listOf(t), liners = listOf(l), threads = listOf(th))
        val expected = "Extends past shaft length (OAL 120\")"
        assertTrue(bodyWarningMessages(built, b, UnitSystem.INCHES).contains(expected))
        assertTrue(taperWarningMessages(built, t, UnitSystem.INCHES).contains(expected))
        assertTrue(linerWarningMessages(built, l, UnitSystem.INCHES).contains(expected))
        assertTrue(threadWarningMessages(built, th, UnitSystem.INCHES).contains(expected))
        assertTrue(collectAddWarnings(built, 3000f, 100f, UnitSystem.INCHES).contains("Falls outside shaft span (OAL 120\")"))
    }

    @Test
    fun `taper past the shaft end gets the card chip`() {
        val t = Taper(startFromAftMm = 900f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 1000f, tapers = listOf(t))
        assertTrue(taperWarningMessages(spec, t, UnitSystem.MILLIMETERS).contains(PAST_OAL_1000))
    }

    @Test
    fun `liner past the shaft end gets the card chip`() {
        val ln = Liner(startFromAftMm = 900f, lengthMm = 200f, odMm = 80f)
        val spec = ShaftSpec(overallLengthMm = 1000f, liners = listOf(ln))
        assertTrue(linerWarningMessages(spec, ln, UnitSystem.MILLIMETERS).contains(PAST_OAL_1000))
    }

    @Test
    fun `included thread past the shaft end gets the card chip`() {
        val th = Threads(startFromAftMm = 900f, lengthMm = 200f, majorDiaMm = 40f, pitchMm = 2f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        assertTrue(threadWarningMessages(spec, th, UnitSystem.MILLIMETERS).contains(PAST_OAL_1000))
    }

    /** An excluded thread sits outside the envelope by design — flagging it would fire always. */
    @Test
    fun `excluded thread past the shaft end is never flagged`() {
        val th = Threads(
            startFromAftMm = 1000f, lengthMm = 200f, majorDiaMm = 40f, pitchMm = 2f,
            excludeFromOAL = true, isAftEnd = false,
        )
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        assertFalse(threadWarningMessages(spec, th, UnitSystem.MILLIMETERS).any { it.contains("past shaft length") })
    }

    @Test
    fun `banner names a past-OAL component and how far it runs`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 900f, lengthMm = 200f, diaMm = 50f)),
        )
        assertTrue(banner(spec).contains("Body #1 runs 100 mm past the FWD end"))
    }

    @Test
    fun `banner names every past-OAL component`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 900f, lengthMm = 200f, diaMm = 50f)),
            liners = listOf(Liner(startFromAftMm = 950f, lengthMm = 200f, odMm = 80f)),
            threads = listOf(
                // The excluded one must not be counted.
                Threads(startFromAftMm = 1000f, lengthMm = 50f, majorDiaMm = 40f, pitchMm = 2f,
                        excludeFromOAL = true),
            ),
        )
        assertEquals(
            listOf("Body #1 runs 100 mm past the FWD end", "FWD Liner runs 150 mm past the FWD end"),
            banner(spec).filter { it.contains("past the FWD end") },
        )
    }

    /**
     * The banner message depends on `overallLengthMm`, not only on the component lists —
     * the pure half of the `remember`-key fix in `SpecWarningBanner`. Shortening the shaft
     * under an unchanged component must flip the message on.
     */
    @Test
    fun `shortening the OAL alone raises the banner line`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 900f, diaMm = 50f)
        val roomy = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body))
        assertTrue(banner(roomy).none { it.contains("past the FWD end") })

        val tight = roomy.copy(overallLengthMm = 500f)
        assertTrue(banner(tight).contains("Body #1 runs 400 mm past the FWD end"))
    }

    /**
     * §3.1a — a coupler bolt slot is a reference feature, not a component: it never enters
     * coverage, collision, or these bounds. A row of cutouts whose footprint runs past the FWD
     * end is ordinary authoring on a coupling at the very end of the shaft, and flagging it
     * would put a permanent advisory on a correct drawing.
     */
    @Test
    fun `a coupler bolt slot past the shaft end raises nothing`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            couplerBoltSlots = listOf(
                CouplerBoltSlot(startFromAftMm = 980f, holeDiaMm = 20f, count = 3, spacingMm = 30f),
            ),
        )
        assertTrue(banner(spec).isEmpty())
    }

    /**
     * §3.3 — bounds are judged on the STORED span. The authored reference is display metadata:
     * two tapers occupying the same millimetres are equally past the end whichever face their
     * Start field was measured from.
     */
    @Test
    fun `the authored reference does not change the bounds verdict`() {
        val aftRef = Taper(
            id = "t-aft", startFromAftMm = 900f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 60f,
            authoredReference = LinerAuthoredReference.AFT,
        )
        val fwdRef = aftRef.copy(id = "t-fwd", authoredReference = LinerAuthoredReference.FWD)
        val spec = ShaftSpec(overallLengthMm = 1000f, tapers = listOf(aftRef, fwdRef))

        assertTrue(taperWarningMessages(spec, aftRef, UnitSystem.MILLIMETERS).contains(PAST_OAL_1000))
        assertTrue(taperWarningMessages(spec, fwdRef, UnitSystem.MILLIMETERS).contains(PAST_OAL_1000))
        assertEquals(2, specIssues(spec, UnitSystem.MILLIMETERS).count { it.kind == SpecIssueKind.PAST_FWD_END })
    }

    /* ── Thread relief advisories ──────────────────────────────────────────── */

    private fun relievedThread(len: Float, dia: Float) = Threads(
        startFromAftMm = 0f, lengthMm = 100f, majorDiaMm = 60f, pitchMm = 2f,
        reliefLenMm = len, reliefDiaMm = dia,
    )

    private fun threadWarnings(th: Threads) =
        threadWarningMessages(ShaftSpec(overallLengthMm = 1000f, threads = listOf(th)), th, UnitSystem.MILLIMETERS)

    @Test
    fun `a sound relief raises no advisory`() {
        assertEquals(emptyList<String>(), threadWarnings(relievedThread(4f, 57f)))
        assertEquals(emptyList<String>(), threadWarnings(relievedThread(0f, 0f)))
    }

    @Test
    fun `relief diameter at or above the thread diameter warns`() {
        assertTrue(threadWarnings(relievedThread(4f, 60f)).contains(THREAD_RELIEF_DIA_MSG))
        assertTrue(threadWarnings(relievedThread(4f, 75f)).contains(THREAD_RELIEF_DIA_MSG))
        assertFalse(threadWarnings(relievedThread(4f, 59.9f)).contains(THREAD_RELIEF_DIA_MSG))
    }

    @Test
    fun `relief as long as the thread warns`() {
        assertTrue(threadWarnings(relievedThread(100f, 57f)).contains(THREAD_RELIEF_LEN_MSG))
        assertTrue(threadWarnings(relievedThread(150f, 57f)).contains(THREAD_RELIEF_LEN_MSG))
        assertFalse(threadWarnings(relievedThread(99f, 57f)).contains(THREAD_RELIEF_LEN_MSG))
    }

    @Test
    fun `relief advisories never rewrite the typed values`() {
        val th = relievedThread(150f, 75f)
        threadWarnings(th)
        assertEquals(150f, th.reliefLenMm, 0f)
        assertEquals(75f, th.reliefDiaMm, 0f)
    }

    /* ── Which END a component runs past ───────────────────────────────────── */

    /**
     * Including an excluded AFT thread in the OAL leaves it at a negative start — hanging off the
     * AFT end. "Past shaft length" read as wrong for that: nothing is longer than the shaft.
     */
    @Test
    fun `a component starting before 0 is reported past the AFT end, not past shaft length`() {
        val th = Threads(startFromAftMm = -147.6f, lengthMm = 147.6f, majorDiaMm = 100f, pitchMm = 6.35f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        val banner = banner(spec)
        assertTrue(banner.contains("AFT Thread starts 147.6 mm before the AFT end"))
        assertTrue(banner.none { it.contains("past the FWD end") })
        assertEquals(
            "Extends past the AFT end of the shaft",
            pastShaftEndMessage(spec, th.startFromAftMm, th.lengthMm, UnitSystem.MILLIMETERS),
        )
    }

    @Test
    fun `the AFT and FWD ends are counted separately`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(
                Body(startFromAftMm = -10f, lengthMm = 200f, diaMm = 50f),
                Body(startFromAftMm = -20f, lengthMm = 200f, diaMm = 50f),
                Body(startFromAftMm = 900f, lengthMm = 200f, diaMm = 50f),
            ),
        )
        val kinds = specIssues(spec, UnitSystem.MILLIMETERS).map { it.kind }
        assertEquals(2, kinds.count { it == SpecIssueKind.PAST_AFT_END })
        assertEquals(1, kinds.count { it == SpecIssueKind.PAST_FWD_END })
    }

    @Test
    fun `a span past both ends names both on its card`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertEquals(
            "Extends past the AFT end of the shaft; $PAST_OAL_1000",
            pastShaftEndMessage(spec, -10f, 1100f, UnitSystem.MILLIMETERS),
        )
        assertEquals(PAST_OAL_1000, pastShaftEndMessage(spec, 900f, 200f, UnitSystem.MILLIMETERS))
    }
}
