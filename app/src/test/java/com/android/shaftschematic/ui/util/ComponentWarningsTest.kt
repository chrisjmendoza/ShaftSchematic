package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(threadWarningMessages(th).contains("Zero pitch — thread renders flat"))
    }

    @Test
    fun `thread short segment warns`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 0.5f, majorDiaMm = 40f, pitchMm = 2f)
        assertTrue(threadWarningMessages(th).contains(SHORT))
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
        assertTrue(threadWarningMessages(th).contains(LENGTH_SANITY))
    }

    @Test
    fun `thread silent on length exactly at threshold`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = MAX_LEN, majorDiaMm = 40f, pitchMm = 2f)
        assertFalse(threadWarningMessages(th).contains(LENGTH_SANITY))
    }

    @Test
    fun `thread warns on implausible major diameter`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = MAX_DIA + 1f, pitchMm = 2f)
        assertTrue(threadWarningMessages(th).contains(DIA_SANITY))
    }

    @Test
    fun `thread silent on major diameter exactly at threshold`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = MAX_DIA, pitchMm = 2f)
        assertFalse(threadWarningMessages(th).contains(DIA_SANITY))
    }

    @Test
    fun `thread implausible dia produces sanity warning plus legitimate zero-pitch warning only`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = MAX_DIA + 1f, pitchMm = 0f)
        val warnings = threadWarningMessages(th)
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
        assertEquals(listOf("1 segments shorter than 1 mm"), specWarningMessages(spec))
    }
}
