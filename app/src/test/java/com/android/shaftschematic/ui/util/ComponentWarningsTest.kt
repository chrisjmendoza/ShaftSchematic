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
    private val MISMATCH = "Ø differs from adjacent body by >10%"
    private val LINER_UNDER = "Liner OD smaller than shaft Ø beneath it"
    private val SHORT = "Very short segment (< 1 mm)"
    private val NO_BODIES = "No explicit bodies — shaft body is all auto-fill"

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

    /* ── §3.3 Taper large mismatch vs adjacent body ─────────────────────────── */

    @Test
    fun `taper mismatch warns when face dia differs from abutting body`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 100f)
        val taper = Taper(startFromAftMm = 100f, lengthMm = 100f, startDiaMm = 80f, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), tapers = listOf(taper))
        assertTrue(taperWarningMessages(spec, taper).contains(MISMATCH))
    }

    @Test
    fun `taper mismatch silent below fraction threshold`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 100f)
        val taper = Taper(startFromAftMm = 100f, lengthMm = 100f, startDiaMm = 92f, endDiaMm = 92f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), tapers = listOf(taper))
        assertFalse(taperWarningMessages(spec, taper).contains(MISMATCH))
    }

    @Test
    fun `taper mismatch silent exactly at 10 percent`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 100f)
        val taper = Taper(startFromAftMm = 100f, lengthMm = 100f, startDiaMm = 90f, endDiaMm = 90f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), tapers = listOf(taper))
        assertFalse(taperWarningMessages(spec, taper).contains(MISMATCH))
    }

    @Test
    fun `taper mismatch silent when not adjacent to body`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 100f)
        // taper starts well past the body's FWD face
        val taper = Taper(startFromAftMm = 300f, lengthMm = 100f, startDiaMm = 40f, endDiaMm = 40f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), tapers = listOf(taper))
        assertFalse(taperWarningMessages(spec, taper).contains(MISMATCH))
    }

    @Test
    fun `taper mismatch skips zero-dia body sentinel`() {
        val body = Body(startFromAftMm = 0f, lengthMm = 100f, diaMm = 0f)
        val taper = Taper(startFromAftMm = 100f, lengthMm = 100f, startDiaMm = 80f, endDiaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), tapers = listOf(taper))
        assertFalse(taperWarningMessages(spec, taper).contains(MISMATCH))
    }

    // ── Regression: FWD-end taper stored SET-in-startDiaMm (Add-taper path). ──────
    // On-device case: FWD taper, SET at the shaft tip, LET (equal to the body Ø)
    // abutting the body at the taper's AFT face. The naive "startDiaMm at the AFT face"
    // mapping read the SET (152.4) there and raised a false >10% warning; the physical
    // AFT face is the LET (177.8), which matches the body.

    @Test
    fun `fwd taper stored SET-first does not warn when LET matches abutting body`() {
        // 126 in ≈ 3200.4 mm modelled directly in mm.
        val body = Body(startFromAftMm = 2400f, lengthMm = 500f, diaMm = 177.8f) // 7"
        // FWD taper occupying the FWD half: SET (6") stored in startDiaMm, LET (7") in endDiaMm.
        val taper = Taper(startFromAftMm = 2900f, lengthMm = 300f, startDiaMm = 152.4f, endDiaMm = 177.8f)
        val spec = ShaftSpec(overallLengthMm = 3200f, bodies = listOf(body), tapers = listOf(taper))
        assertFalse(taperWarningMessages(spec, taper).contains(MISMATCH))
    }

    @Test
    fun `aft taper mirror does not warn when LET matches abutting body`() {
        // AFT-half taper: SET (6") at the AFT tip, LET (7") at the FWD face abutting the body.
        val taper = Taper(startFromAftMm = 0f, lengthMm = 300f, startDiaMm = 152.4f, endDiaMm = 177.8f)
        val body = Body(startFromAftMm = 300f, lengthMm = 500f, diaMm = 177.8f)
        val spec = ShaftSpec(overallLengthMm = 3200f, bodies = listOf(body), tapers = listOf(taper))
        assertFalse(taperWarningMessages(spec, taper).contains(MISMATCH))
    }

    @Test
    fun `fwd taper still warns when abutting-face LET genuinely mismatches body`() {
        val body = Body(startFromAftMm = 2400f, lengthMm = 500f, diaMm = 177.8f)
        // LET = 203.2 mm (8") physically at the AFT face abutting the body → |203.2−177.8|/177.8 ≈ 14%.
        val taper = Taper(startFromAftMm = 2900f, lengthMm = 300f, startDiaMm = 152.4f, endDiaMm = 203.2f)
        val spec = ShaftSpec(overallLengthMm = 3200f, bodies = listOf(body), tapers = listOf(taper))
        assertTrue(taperWarningMessages(spec, taper).contains(MISMATCH))
    }

    @Test
    fun `aft taper still warns when abutting-face LET genuinely mismatches body`() {
        val taper = Taper(startFromAftMm = 0f, lengthMm = 300f, startDiaMm = 152.4f, endDiaMm = 203.2f)
        val body = Body(startFromAftMm = 300f, lengthMm = 500f, diaMm = 177.8f)
        val spec = ShaftSpec(overallLengthMm = 3200f, bodies = listOf(body), tapers = listOf(taper))
        assertTrue(taperWarningMessages(spec, taper).contains(MISMATCH))
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
        assertEquals("Zero pitch — thread renders flat", threadWarningMessage(th))
    }

    @Test
    fun `thread short segment warns`() {
        val th = Threads(startFromAftMm = 0f, lengthMm = 0.5f, majorDiaMm = 40f, pitchMm = 2f)
        assertEquals(SHORT, threadWarningMessage(th))
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

    /* ── §4.3 Zero-body coverage ────────────────────────────────────────────── */

    @Test
    fun `spec warns on zero-body coverage with a taper present`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(Taper(startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 60f)),
        )
        assertTrue(specWarningMessages(spec).contains(NO_BODIES))
    }

    @Test
    fun `spec silent on zero-body coverage when only excluded thread exists`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(
                Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = 40f, pitchMm = 2f, excludeFromOAL = true),
            ),
        )
        assertFalse(specWarningMessages(spec).contains(NO_BODIES))
    }

    @Test
    fun `spec silent on zero-body coverage for fully empty spec`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertTrue(specWarningMessages(spec).isEmpty())
    }

    @Test
    fun `spec silent on zero-body coverage when bodies present`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 50f)),
            tapers = listOf(Taper(startFromAftMm = 500f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 60f)),
        )
        assertFalse(specWarningMessages(spec).contains(NO_BODIES))
    }
}
