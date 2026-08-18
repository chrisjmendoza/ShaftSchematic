package com.android.shaftschematic.pdf

import android.graphics.Color
import android.graphics.Paint
import com.android.shaftschematic.geom.DimensionRailLayout
import com.android.shaftschematic.pdf.dim.DimSpan
import com.android.shaftschematic.pdf.dim.SpanKind
import com.android.shaftschematic.pdf.render.PdfDimensionRenderer
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.measureDualLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.max

/**
 * The ledger the whole feature rests on (`docs/DualUnitStacking_PLAN.md` §2, §9).
 *
 * Stacking a dual value costs a taller label band, and refunds it by seating values back in the
 * dimension line — every value restored to the break removes a fallback rail, and each fallback
 * rail lifts every rail above it by a whole band. The claim is that the refund covers the cost.
 *
 * The quantity compared is the WHOLE rail block, `railGap × (maxRail + 1) + topLift`, not the lift
 * alone: stacked mode also widens the lane pitch, so lifts can shrink while the block still grows.
 * The lane rule mirrors `ShaftPdfComposer`'s fit loop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DualStackLedgerTest {

    private val inch = UnitSystem.INCHES

    private fun dimText() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        color = Color.BLACK
    }

    /**
     * Page x for a mm position on the 133" shaft from the on-device sheet, drawn across the width
     * a landscape Letter page actually leaves for the profile once margins and the SET-to-SET
     * window are taken out. At this scale a ~25" span is ~100 pt of paper — which the inline dual
     * pair overflows and the stack does not, exactly as the printed sheet showed.
     */
    private fun pageX(mm: Double): Float = (mm / 3378.2 * 520.0).toFloat() + 40f

    private fun renderer(text: Paint, stacked: Boolean) = PdfDimensionRenderer(
        pageX = ::pageX,
        linePaint = Paint(),
        textPaint = text,
        objectTopY = 400f,
        arrowSize = 3f,
        dualStacked = stacked,
    )

    /** The composer's lane rule: a flat floor inline, a metrics-derived one when stacked. */
    private fun railGap(r: PdfDimensionRenderer, stacked: Boolean): Float =
        if (!stacked) 30f
        else max(30f, r.labelHeight() + 2f * DimensionRailLayout.LINE_HALF_CLEAR + 2f)

    /** Total height the rail block occupies above the shaft. */
    private fun blockHeight(spans: List<Pair<Int, DimSpan>>, stacked: Boolean): Float {
        val text = dimText()
        val r = renderer(text, stacked)
        val gap = railGap(r, stacked)
        val maxRail = spans.maxOf { it.first }
        val inputs = spans.map { (rail, span) -> r.spanInput(rail, 400f - gap * rail, span) }
        return gap * (maxRail + 1) + r.topLift(inputs)
    }

    /** The sheet from the on-device report: a 133" shaft with two ~25" datum spans and a taper. */
    private fun realSheetSpans(dual: Boolean): List<Pair<Int, DimSpan>> = listOf(
        0 to DimSpan(0.0, 649.3, formatLenDimDualLabel(649.3, inch, dual), SpanKind.DATUM),
        1 to DimSpan(0.0, 639.8, formatLenDimDualLabel(639.8, inch, dual), SpanKind.DATUM),
        2 to DimSpan(649.3, 1979.6, formatLenDimDualLabel(1330.3, inch, dual), SpanKind.LOCAL),
        DimensionRailLayout.TOP_RAIL to
            DimSpan(0.0, 3378.2, formatLenDimDualLabel(3378.2, inch, dual), SpanKind.OAL),
    )

    @Test
    fun `stacking does not cost the sheet height it saves`() {
        val spans = realSheetSpans(dual = true)
        val inlineH = blockHeight(spans, stacked = false)
        val stackedH = blockHeight(spans, stacked = true)
        assertTrue(
            "stacked block ($stackedH pt) should not exceed the inline one ($inlineH pt)",
            stackedH <= inlineH + 0.5f,
        )
    }

    @Test
    fun `stacking seats values in the line that inline dual pushed above it`() {
        val spans = realSheetSpans(dual = true)
        val text = dimText()

        fun fallbackCount(stacked: Boolean): Int {
            val r = renderer(text, stacked)
            val gap = railGap(r, stacked)
            val inputs = spans.map { (rail, span) -> r.spanInput(rail, 400f - gap * rail, span) }
            return r.plan(inputs).placements.count { !it.inline }
        }

        val inlineFallbacks = fallbackCount(stacked = false)
        val stackedFallbacks = fallbackCount(stacked = true)
        assertTrue(
            "inline dual should push values above the line on this sheet (got $inlineFallbacks)",
            inlineFallbacks > 0,
        )
        assertTrue(
            "stacking should seat more values in the break " +
                "(inline=$inlineFallbacks, stacked=$stackedFallbacks)",
            stackedFallbacks < inlineFallbacks,
        )
    }

    @Test
    fun `a single-unit sheet is untouched by the stacked setting`() {
        val spans = realSheetSpans(dual = false)
        val inlineH = blockHeight(spans, stacked = false)
        val stackedH = blockHeight(spans, stacked = true)
        // Nothing on this sheet has a second term, so no label sets as a stack and the block is
        // the same height either way — a stacked PREF must never move a single-unit document.
        assertTrue(
            "single-unit block moved: inline=$inlineH stacked=$stackedH",
            kotlin.math.abs(stackedH - inlineH) < 0.5f,
        )
    }

    @Test
    fun `the compact secondary is what keeps a dual label narrow enough to matter`() {
        // The rails used to print the secondary at the DIMENSION formatter's 3 decimals, while the
        // Ø callouts on the same sheet printed 1. The compact form is both the right precision for
        // a converted courtesy value and ~17 pt narrower per label at this text size.
        val text = dimText()
        val compact = formatLenDimDualLabel(3378.2, inch, dual = true)
        assertEquals("3378.2 mm", compact.secondary)

        val noisy = "133\" [3378.200 mm]"
        assertTrue(
            "the compact pair should measure narrower than the 3-decimal one",
            text.measureText(compact.inline()) < text.measureText(noisy),
        )
        // And stacked it is narrower still — the width that puts values back in the line.
        assertTrue(
            text.measureDualLabel(compact, stacked = true) <
                text.measureDualLabel(compact, stacked = false),
        )
    }
}
