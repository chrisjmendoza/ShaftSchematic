package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
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

    // Mirrors the private thresholds in ComponentWarnings.kt.
    private val MAX_LEN = 15_000f
    private val MAX_DIA = 1_000f

    /* ── §3.2 Body Ø discontinuity vs adjacent body ─────────────────────────── */

    @Test
    fun `body step warns when adjacent body dia ratio exceeds threshold`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val b = Body(startFromAftMm = 100f, lengthMm = 100f, diaMm = 100f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, b))
        assertTrue(bodyWarningMessages(spec, a).contains(STEP))
        assertTrue(bodyWarningMessages(spec, b).contains(STEP))
    }

    @Test
    fun `body step silent below ratio threshold`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 100f)
        val b = Body(startFromAftMm = 100f, lengthMm = 100f, diaMm = 140f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, b))
        assertFalse(bodyWarningMessages(spec, a).contains(STEP))
    }

    @Test
    fun `body step silent exactly at ratio 1_5`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 100f)
        val b = Body(startFromAftMm = 100f, lengthMm = 100f, diaMm = 150f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, b))
        assertFalse(bodyWarningMessages(spec, a).contains(STEP))
    }

    @Test
    fun `body step honors adjacency eps at boundary`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        // gap of exactly 0.5 mm — still adjacent
        val near = Body(startFromAftMm = 100.5f, lengthMm = 100f, diaMm = 100f)
        val nearSpec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, near))
        assertTrue(bodyWarningMessages(nearSpec, a).contains(STEP))
        // gap of 0.6 mm — no longer adjacent
        val far = Body(startFromAftMm = 100.6f, lengthMm = 100f, diaMm = 100f)
        val farSpec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, far))
        assertFalse(bodyWarningMessages(farSpec, a).contains(STEP))
    }

    @Test
    fun `body step skips zero-dia sentinel neighbor`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 100f)
        val b = Body(startFromAftMm = 100f, lengthMm = 100f, diaMm = 0f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, b))
        assertFalse(bodyWarningMessages(spec, a).contains(STEP))
        assertFalse(bodyWarningMessages(spec, b).contains(STEP))
    }

    @Test
    fun `body short segment still warns`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 0.5f, diaMm = 50f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a))
        assertTrue(bodyWarningMessages(spec, a).contains(SHORT))
    }

    /* ── §3.3 Taper warnings ────────────────────────────────────────────────── */

    @Test
    fun `taper short segment warns`() {
        val taper = Taper(startFromAftMm = 0f, lengthMm = 0.5f, startDiaMm = 80f, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 1000f, tapers = listOf(taper))
        assertTrue(taperWarningMessages(spec, taper).contains(SHORT))
    }

    // Pins the removal of the taper-vs-body Ø mismatch advisory: a large
    // face-vs-body Ø difference is visible in the drawing and must NOT produce a warning.
    @Test
    fun `taper adjacent body dia mismatch produces no warning`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 100f)
        val taper = Taper(startFromAftMm = 100f, lengthMm = 100f, startDiaMm = 80f, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), tapers = listOf(taper))
        assertTrue(taperWarningMessages(spec, taper).isEmpty())
    }

    /* ── §3.5 Liner OD below underlying body ────────────────────────────────── */

    @Test
    fun `liner warns when od below overlapping body dia`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 100f)
        val liner = Liner(startFromAftMm = 100f, lengthMm = 200f, odMm = 80f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), liners = listOf(liner))
        assertTrue(linerWarningMessages(spec, liner).contains(LINER_UNDER))
    }

    @Test
    fun `liner silent when not overlapping any body`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 100f)
        val liner = Liner(startFromAftMm = 600f, lengthMm = 100f, odMm = 80f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), liners = listOf(liner))
        assertFalse(linerWarningMessages(spec, liner).contains(LINER_UNDER))
    }

    @Test
    fun `liner silent when od equals body dia`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 100f)
        val liner = Liner(startFromAftMm = 100f, lengthMm = 200f, odMm = 100f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), liners = listOf(liner))
        assertFalse(linerWarningMessages(spec, liner).contains(LINER_UNDER))
    }

    @Test
    fun `liner silent over zero-dia body sentinel`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 0f)
        val liner = Liner(startFromAftMm = 100f, lengthMm = 200f, odMm = 80f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), liners = listOf(liner))
        assertFalse(linerWarningMessages(spec, liner).contains(LINER_UNDER))
    }

    /* ── Threads unchanged ──────────────────────────────────────────────────── */

    @Test
    fun `thread zero pitch warns`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = 40f, pitchMm = 0f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        assertTrue(threadWarningMessages(spec, th).contains("Zero pitch — thread renders flat"))
    }

    @Test
    fun `thread short segment warns`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 0.5f, majorDiaMm = 40f, pitchMm = 2f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        assertTrue(threadWarningMessages(spec, th).contains(SHORT))
    }

    /* ── §2.1 Implausible length/diameter sanity checks ────────────────────── */

    @Test
    fun `body warns on implausible length`() {
        val a = Body(startFromAftMm = 0f, lengthMm = MAX_LEN + 1f, diaMm = 50f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN + 1000f, bodies = listOf(a))
        assertTrue(bodyWarningMessages(spec, a).contains(LENGTH_SANITY))
    }

    @Test
    fun `body silent on length exactly at threshold`() {
        val a = Body(startFromAftMm = 0f, lengthMm = MAX_LEN, diaMm = 50f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN, bodies = listOf(a))
        assertFalse(bodyWarningMessages(spec, a).contains(LENGTH_SANITY))
    }

    @Test
    fun `body warns on implausible diameter`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = MAX_DIA + 1f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a))
        assertTrue(bodyWarningMessages(spec, a).contains(DIA_SANITY))
    }

    @Test
    fun `body silent on diameter exactly at threshold`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = MAX_DIA)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a))
        assertFalse(bodyWarningMessages(spec, a).contains(DIA_SANITY))
    }

    @Test
    fun `body implausible dia produces sanity warning plus legitimate step warning only`() {
        val a = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = MAX_DIA + 1f)
        val b = Body(startFromAftMm = 100f, lengthMm = 100f, diaMm = 50f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(a, b))
        val warnings = bodyWarningMessages(spec, a)
        assertEquals(setOf(DIA_SANITY, STEP), warnings.toSet())
    }

    @Test
    fun `taper warns on implausible length`() {
        val t = Taper(startFromAftMm = 0f, lengthMm = MAX_LEN + 1f, startDiaMm = 80f, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN + 1000f, tapers = listOf(t))
        assertTrue(taperWarningMessages(spec, t).contains(LENGTH_SANITY))
    }

    @Test
    fun `taper silent on length exactly at threshold`() {
        val t = Taper(startFromAftMm = 0f, lengthMm = MAX_LEN, startDiaMm = 80f, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN, tapers = listOf(t))
        assertFalse(taperWarningMessages(spec, t).contains(LENGTH_SANITY))
    }

    @Test
    fun `taper warns on implausible diameter via either end`() {
        val setEnd = Taper(startFromAftMm = 0f, lengthMm = 100f, startDiaMm = MAX_DIA + 1f, endDiaMm = 60f)
        val letEnd = Taper(startFromAftMm = 0f, lengthMm = 100f, startDiaMm = 60f, endDiaMm = MAX_DIA + 1f)
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertTrue(taperWarningMessages(spec, setEnd).contains(DIA_SANITY))
        assertTrue(taperWarningMessages(spec, letEnd).contains(DIA_SANITY))
    }

    @Test
    fun `taper silent on diameter exactly at threshold`() {
        val t = Taper(startFromAftMm = 0f, lengthMm = 100f, startDiaMm = MAX_DIA, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 1000f, tapers = listOf(t))
        assertFalse(taperWarningMessages(spec, t).contains(DIA_SANITY))
    }

    @Test
    fun `liner warns on implausible length`() {
        val ln = Liner(startFromAftMm = 0f, lengthMm = MAX_LEN + 1f, odMm = 80f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN + 1000f, liners = listOf(ln))
        assertTrue(linerWarningMessages(spec, ln).contains(LENGTH_SANITY))
    }

    @Test
    fun `liner silent on length exactly at threshold`() {
        val ln = Liner(startFromAftMm = 0f, lengthMm = MAX_LEN, odMm = 80f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN, liners = listOf(ln))
        assertFalse(linerWarningMessages(spec, ln).contains(LENGTH_SANITY))
    }

    @Test
    fun `liner warns on implausible od`() {
        val ln = Liner(startFromAftMm = 0f, lengthMm = 100f, odMm = MAX_DIA + 1f)
        val spec = ShaftSpec(overallLengthMm = 1000f, liners = listOf(ln))
        assertTrue(linerWarningMessages(spec, ln).contains(DIA_SANITY))
    }

    @Test
    fun `liner silent on od exactly at threshold`() {
        val ln = Liner(startFromAftMm = 0f, lengthMm = 100f, odMm = MAX_DIA)
        val spec = ShaftSpec(overallLengthMm = 1000f, liners = listOf(ln))
        assertFalse(linerWarningMessages(spec, ln).contains(DIA_SANITY))
    }

    @Test
    fun `liner implausible od produces sanity warning plus legitimate underlying-body warning only`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = MAX_DIA + 500f)
        val ln = Liner(startFromAftMm = 100f, lengthMm = 200f, odMm = MAX_DIA + 1f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), liners = listOf(ln))
        val warnings = linerWarningMessages(spec, ln)
        assertEquals(setOf(DIA_SANITY, LINER_UNDER), warnings.toSet())
    }

    @Test
    fun `thread warns on implausible length`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = MAX_LEN + 1f, majorDiaMm = 40f, pitchMm = 2f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN + 1000f, threads = listOf(th))
        assertTrue(threadWarningMessages(spec, th).contains(LENGTH_SANITY))
    }

    @Test
    fun `thread silent on length exactly at threshold`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = MAX_LEN, majorDiaMm = 40f, pitchMm = 2f)
        val spec = ShaftSpec(overallLengthMm = MAX_LEN, threads = listOf(th))
        assertFalse(threadWarningMessages(spec, th).contains(LENGTH_SANITY))
    }

    @Test
    fun `thread warns on implausible major diameter`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = MAX_DIA + 1f, pitchMm = 2f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        assertTrue(threadWarningMessages(spec, th).contains(DIA_SANITY))
    }

    @Test
    fun `thread silent on major diameter exactly at threshold`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = MAX_DIA, pitchMm = 2f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        assertFalse(threadWarningMessages(spec, th).contains(DIA_SANITY))
    }

    @Test
    fun `thread implausible dia produces sanity warning plus legitimate zero-pitch warning only`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = MAX_DIA + 1f, pitchMm = 0f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        val warnings = threadWarningMessages(spec, th)
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
        assertTrue(specWarningMessages(spec).contains("2 segments shorter than 1 mm"))
    }

    @Test
    fun `spec does not count zero-length segment as tiny`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 0f, diaMm = 50f)),
            tapers = listOf(Taper(startFromAftMm = 100f, lengthMm = 200f, startDiaMm = 40f, endDiaMm = 40f)),
        )
        assertTrue(specWarningMessages(spec).none { it.contains("shorter than 1 mm") })
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
        assertTrue(specWarningMessages(spec).none { it.contains("shorter than 1 mm") })
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
        assertTrue(specWarningMessages(spec).isEmpty())
    }

    @Test
    fun `a fully empty spec raises nothing`() {
        assertTrue(specWarningMessages(ShaftSpec(overallLengthMm = 1000f)).isEmpty())
    }

    /** The one surviving message still fires — removing the note must not silence the banner. */
    @Test
    fun `a genuine anomaly still raises`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(Taper(startFromAftMm = 0f, lengthMm = 0.5f, startDiaMm = 80f, endDiaMm = 60f)),
        )
        assertEquals(listOf("1 segment shorter than 1 mm"), specWarningMessages(spec))
    }

    /* ── Past-OAL bounds advisory (shared predicate) ────────────────────────── */

    @Test
    fun `a span inside the shaft is not outside`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertFalse(outsideShaftSpan(spec, 100f, 200f))
        assertNull(pastShaftEndMessage(spec, 100f, 200f))
    }

    @Test
    fun `a span running past the end is outside`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertTrue(outsideShaftSpan(spec, 900f, 200f))
        assertEquals(
            "Extends past shaft length (OAL 1000 mm)",
            pastShaftEndMessage(spec, 900f, 200f),
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
        assertNull(pastShaftEndMessage(spec, 900f, 500f))
    }

    @Test
    fun `a span ending exactly at the shaft end is inside`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertFalse(outsideShaftSpan(spec, 800f, 200f))
    }

    /** The eps absorbs float round-trip noise; a real overrun still fires. */
    @Test
    fun `bounds honor the eps at the boundary`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertFalse(outsideShaftSpan(spec, 800f, 200.0005f))
        assertTrue(outsideShaftSpan(spec, 800f, 200.01f))
        assertFalse(outsideShaftSpan(spec, -0.0005f, 100f))
        assertTrue(outsideShaftSpan(spec, -0.01f, 100f))
    }

    /** Both surfaces read the ONE comparison; only the wording differs. */
    @Test
    fun `dialog and card messages agree on when but not on wording`() {
        val spec = ShaftSpec(overallLengthMm = 500f)
        assertEquals("Falls outside shaft span (OAL 500 mm)", outsideShaftSpanMessage(spec, 400f, 200f))
        assertEquals("Extends past shaft length (OAL 500 mm)", pastShaftEndMessage(spec, 400f, 200f))
        assertNull(outsideShaftSpanMessage(spec, 100f, 200f))
        assertNull(pastShaftEndMessage(spec, 100f, 200f))
    }

    @Test
    fun `body past the shaft end gets the card chip`() {
        val b = Body(startFromAftMm = 900f, lengthMm = 200f, diaMm = 50f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(b))
        assertTrue(bodyWarningMessages(spec, b).contains(PAST_OAL_1000))
    }

    @Test
    fun `taper past the shaft end gets the card chip`() {
        val t = Taper(startFromAftMm = 900f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 1000f, tapers = listOf(t))
        assertTrue(taperWarningMessages(spec, t).contains(PAST_OAL_1000))
    }

    @Test
    fun `liner past the shaft end gets the card chip`() {
        val ln = Liner(startFromAftMm = 900f, lengthMm = 200f, odMm = 80f)
        val spec = ShaftSpec(overallLengthMm = 1000f, liners = listOf(ln))
        assertTrue(linerWarningMessages(spec, ln).contains(PAST_OAL_1000))
    }

    @Test
    fun `included thread past the shaft end gets the card chip`() {
        val th = Threads(startFromAftMm = 900f, lengthMm = 200f, majorDiaMm = 40f, pitchMm = 2f)
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        assertTrue(threadWarningMessages(spec, th).contains(PAST_OAL_1000))
    }

    /** An excluded thread sits outside the envelope by design — flagging it would fire always. */
    @Test
    fun `excluded thread past the shaft end is never flagged`() {
        val th = Threads(
            startFromAftMm = 1000f, lengthMm = 200f, majorDiaMm = 40f, pitchMm = 2f,
            excludeFromOAL = true, isAftEnd = false,
        )
        val spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th))
        assertFalse(threadWarningMessages(spec, th).any { it.contains("past shaft length") })
    }

    @Test
    fun `banner counts one past-OAL component in the singular`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 900f, lengthMm = 200f, diaMm = 50f)),
        )
        assertTrue(specWarningMessages(spec).contains("1 component extends past shaft length"))
    }

    @Test
    fun `banner counts several past-OAL components in the plural`() {
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
        assertTrue(specWarningMessages(spec).contains("2 components extend past shaft length"))
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
        assertTrue(specWarningMessages(roomy).none { it.contains("past shaft length") })

        val tight = roomy.copy(overallLengthMm = 500f)
        assertTrue(specWarningMessages(tight).contains("1 component extends past shaft length"))
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
        assertTrue(specWarningMessages(spec).none { it.contains("past shaft length") })
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

        assertTrue(taperWarningMessages(spec, aftRef).contains(PAST_OAL_1000))
        assertTrue(taperWarningMessages(spec, fwdRef).contains(PAST_OAL_1000))
        assertTrue(specWarningMessages(spec).contains("2 components extend past shaft length"))
    }
}
