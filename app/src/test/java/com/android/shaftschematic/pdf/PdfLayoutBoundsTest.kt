package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that the PDF layout keeps the entire shaft drawing (including excluded
 * end threads that extend outside the OAL span) within the page's drawable area.
 *
 * The constants here mirror ShaftPdfComposer:
 *   PAGE_MARGIN_PT = 36f, page width = 612f (US Letter)
 *   geomRect.left = 36f, geomRect.right = 576f → geomWidth = 540f
 *
 * Helper: [layoutFor] replicates the contentMin/contentMax/ptPerMm/left math from
 * ShaftPdfComposer so we can assert on geometry without generating a real PDF.
 */
class PdfLayoutBoundsTest {

    private val geomWidthPt = 540f   // US Letter: 612 - 2 × 36
    private val geomLeftPt  = 36f
    private val geomRightPt = geomLeftPt + geomWidthPt
    private val geomHeightPt = 400f  // generous; not the binding constraint in these tests
    private val eps = 0.5f

    /**
     * Replicates the layout geometry from ShaftPdfComposer (lines 129–151 after fix).
     * Returns (ptPerMm, leftPt) so tests can compute any shaft coordinate.
     */
    private fun layoutFor(spec: ShaftSpec): Pair<Float, Float> {
        val contentMinMm = minOf(0f,
            spec.threads.filter { it.excludeFromOAL && it.isAftEnd }
                .minOfOrNull { it.startFromAftMm } ?: 0f
        )
        val contentMaxMm = maxOf(spec.overallLengthMm,
            spec.threads.filter { it.excludeFromOAL && !it.isAftEnd }
                .maxOfOrNull { it.startFromAftMm + it.lengthMm } ?: spec.overallLengthMm
        )
        val contentSpanMm = (contentMaxMm - contentMinMm).coerceAtLeast(1f)
        val oalMmClamped = spec.overallLengthMm.coerceAtLeast(1f)
        val effectiveGeomWidthPt = geomWidthPt * (oalMmClamped / contentSpanMm)

        val hasNonBodyDetail = spec.tapers.isNotEmpty() || spec.threads.isNotEmpty() || spec.liners.isNotEmpty()
        val bodyOnly = spec.bodies.isNotEmpty() && !hasNonBodyDetail

        val ptPerMm = if (bodyOnly || hasNonBodyDetail) {
            computeDetailPtPerMm(spec, effectiveGeomWidthPt, geomHeightPt)
        } else {
            geomWidthPt / contentSpanMm
        }

        val left = geomLeftPt + (-contentMinMm * ptPerMm).coerceAtLeast(0f)
        return ptPerMm to left
    }

    private fun xAt(mm: Float, ptPerMm: Float, left: Float) = left + mm * ptPerMm

    // ── No excluded threads — shaft fills drawable width ──────────────────────

    @Test
    fun `no excluded threads - shaft fwd end within right margin`() {
        val oalMm = 3756f
        val spec = ShaftSpec(
            overallLengthMm = oalMm,
            tapers = listOf(Taper(startFromAftMm = 0f, lengthMm = 300f, startDiaMm = 130f, endDiaMm = 108f)),
        )
        val (ptPerMm, left) = layoutFor(spec)
        val fwdEdgePt = xAt(oalMm, ptPerMm, left)

        assertTrue("FWD end must be ≤ right margin (was ${fwdEdgePt}pt)", fwdEdgePt <= geomRightPt + eps)
        assertTrue("AFT end must be ≥ left margin", xAt(0f, ptPerMm, left) >= geomLeftPt - eps)
    }

    // ── Excluded AFT thread — historically caused FWD overflow ────────────────

    @Test
    fun `excluded aft thread - fwd end of shaft stays within right margin`() {
        val oalMm = 3756f
        val aftThreadLen = 114f  // 4.5" — representative prop thread
        val aftThread = Threads(
            startFromAftMm = -aftThreadLen,
            lengthMm = aftThreadLen,
            majorDiaMm = 95f, pitchMm = 3f,
            excludeFromOAL = true,
            isAftEnd = true,
        )
        val spec = ShaftSpec(
            overallLengthMm = oalMm,
            threads = listOf(aftThread),
            tapers = listOf(Taper(startFromAftMm = aftThreadLen, lengthMm = 300f, startDiaMm = 130f, endDiaMm = 108f)),
        )
        val (ptPerMm, left) = layoutFor(spec)
        val aftEdgePt = xAt(-aftThreadLen, ptPerMm, left)
        val fwdEdgePt = xAt(oalMm, ptPerMm, left)

        assertTrue("AFT thread must start at or right of left margin (was ${aftEdgePt}pt)", aftEdgePt >= geomLeftPt - eps)
        assertTrue("FWD end must be ≤ right margin (was ${fwdEdgePt}pt)", fwdEdgePt <= geomRightPt + eps)
    }

    @Test
    fun `excluded aft thread - full content spans drawable area`() {
        val oalMm = 3756f
        val aftThreadLen = 114f
        val aftThread = Threads(
            startFromAftMm = -aftThreadLen, lengthMm = aftThreadLen,
            majorDiaMm = 95f, pitchMm = 3f,
            excludeFromOAL = true, isAftEnd = true,
        )
        val spec = ShaftSpec(
            overallLengthMm = oalMm, threads = listOf(aftThread),
            tapers = listOf(Taper(startFromAftMm = aftThreadLen, lengthMm = 300f, startDiaMm = 130f, endDiaMm = 108f)),
        )
        val (ptPerMm, left) = layoutFor(spec)

        // total content: -aftThreadLen..oalMm
        val contentWidthPt = (oalMm + aftThreadLen) * ptPerMm
        assertTrue("Content span must fit within drawable width ($contentWidthPt vs $geomWidthPt)",
            contentWidthPt <= geomWidthPt + eps)
    }

    // ── Excluded FWD thread — extends past OAL ───────────────────────────────

    @Test
    fun `excluded fwd thread end stays within right margin`() {
        val oalMm = 2440f
        val fwdThreadLen = 102f  // 4" thread
        val fwdThread = Threads(
            startFromAftMm = oalMm,   // placed at shaft FWD face
            lengthMm = fwdThreadLen,
            majorDiaMm = 95f, pitchMm = 3f,
            excludeFromOAL = true,
            isAftEnd = false,
        )
        val spec = ShaftSpec(
            overallLengthMm = oalMm, threads = listOf(fwdThread),
            tapers = listOf(Taper(startFromAftMm = oalMm - 300f, lengthMm = 300f, startDiaMm = 130f, endDiaMm = 108f)),
        )
        val (ptPerMm, left) = layoutFor(spec)
        val fwdThreadEndPt = xAt(oalMm + fwdThreadLen, ptPerMm, left)

        assertTrue("FWD excluded thread end must be ≤ right margin (was ${fwdThreadEndPt}pt)",
            fwdThreadEndPt <= geomRightPt + eps)
    }

    // ── Both excluded AFT and FWD threads ────────────────────────────────────

    @Test
    fun `both excluded threads - full content stays within page`() {
        val oalMm = 3756f
        val aftLen = 114f
        val fwdLen = 76f  // 3"
        val aftThread = Threads(
            startFromAftMm = -aftLen, lengthMm = aftLen,
            majorDiaMm = 95f, pitchMm = 3f, excludeFromOAL = true, isAftEnd = true,
        )
        val fwdThread = Threads(
            startFromAftMm = oalMm, lengthMm = fwdLen,
            majorDiaMm = 95f, pitchMm = 3f, excludeFromOAL = true, isAftEnd = false,
        )
        val spec = ShaftSpec(
            overallLengthMm = oalMm,
            threads = listOf(aftThread, fwdThread),
            tapers = listOf(Taper(startFromAftMm = aftLen, lengthMm = 300f, startDiaMm = 130f, endDiaMm = 108f)),
        )
        val (ptPerMm, left) = layoutFor(spec)
        val aftEdgePt  = xAt(-aftLen, ptPerMm, left)
        val fwdEdgePt  = xAt(oalMm + fwdLen, ptPerMm, left)

        assertTrue("AFT edge ≥ left margin (was $aftEdgePt)", aftEdgePt >= geomLeftPt - eps)
        assertTrue("FWD edge ≤ right margin (was $fwdEdgePt)", fwdEdgePt <= geomRightPt + eps)
    }

    // ── Short shaft ───────────────────────────────────────────────────────────

    @Test
    fun `short shaft with no excluded threads stays within bounds`() {
        val oalMm = 300f  // ~12 inch shaft
        val spec = ShaftSpec(
            overallLengthMm = oalMm,
            tapers = listOf(Taper(startFromAftMm = 0f, lengthMm = 100f, startDiaMm = 60f, endDiaMm = 50f)),
        )
        val (ptPerMm, left) = layoutFor(spec)
        val fwdEdgePt = xAt(oalMm, ptPerMm, left)
        val aftEdgePt = xAt(0f, ptPerMm, left)

        assertTrue("AFT edge ≥ left margin", aftEdgePt >= geomLeftPt - eps)
        assertTrue("FWD edge ≤ right margin (was $fwdEdgePt)", fwdEdgePt <= geomRightPt + eps)
    }

    @Test
    fun `very short shaft - scale is bounded by diameter constraint`() {
        // A very short, wide shaft should be bounded by the target-height constraint, not page width.
        val oalMm = 50f
        val spec = ShaftSpec(
            overallLengthMm = oalMm,
            tapers = listOf(Taper(startFromAftMm = 0f, lengthMm = oalMm, startDiaMm = 120f, endDiaMm = 100f)),
        )
        val (ptPerMm, left) = layoutFor(spec)
        val fwdEdgePt = xAt(oalMm, ptPerMm, left)

        // Shaft must always fit on page
        assertTrue("FWD edge ≤ right margin (was $fwdEdgePt)", fwdEdgePt <= geomRightPt + eps)
        // Rendered height must not be taller than target (~90pt = 1.25in)
        val renderedHeight = 120f * ptPerMm
        assertTrue("Height bounded by target (~90pt), got $renderedHeight", renderedHeight <= 90f + eps)
    }

    // ── Idempotency: same OAL, different excluded thread sizes ───────────────

    @Test
    fun `shaft fwd end position invariant across different aft thread lengths`() {
        val oalMm = 2440f
        val taper = Taper(startFromAftMm = 100f, lengthMm = 300f, startDiaMm = 130f, endDiaMm = 108f)

        // All should produce a shaft whose FWD end is within page bounds
        for (threadLen in listOf(50f, 100f, 150f, 200f)) {
            val aftThread = Threads(
                startFromAftMm = -threadLen, lengthMm = threadLen,
                majorDiaMm = 95f, pitchMm = 3f, excludeFromOAL = true, isAftEnd = true,
            )
            val spec = ShaftSpec(overallLengthMm = oalMm,
                threads = listOf(aftThread), tapers = listOf(taper))
            val (ptPerMm, left) = layoutFor(spec)
            val fwdEdgePt = xAt(oalMm, ptPerMm, left)

            assertTrue("FWD end within margin for aftThreadLen=$threadLen (was $fwdEdgePt)",
                fwdEdgePt <= geomRightPt + eps)
        }
    }
}
