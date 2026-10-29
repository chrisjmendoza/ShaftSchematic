package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.geom.DimensionRailLayout
import com.android.shaftschematic.pdf.dim.DimSpan
import com.android.shaftschematic.pdf.dim.SpanKind
import com.android.shaftschematic.pdf.render.PdfDimensionRenderer
import com.android.shaftschematic.pdf.render.RAIL_VALUE_HALO_PAD_PT
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The rail line paint's color in this test — tells rail ink from the text's own strokes. */
private const val RAIL_INK = 0xFF0000FF.toInt()

/**
 * Every dimension-rail value prints over a sheet-white halo, and every line of every span is drawn
 * BEFORE any halo, so a line crossing a value is knocked out rather than striking through it
 * (on-device ruling). Proved on the real renderer with a canvas that records the draw order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RailValueHaloTest {

    private sealed class Op {
        data class Line(val x0: Float, val y0: Float, val x1: Float, val y1: Float) : Op()
        data class Halo(val rect: RectF) : Op()
        data class Text(val s: String, val x: Float, val y: Float) : Op()
    }

    private class OrderCanvas : Canvas(Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)) {
        val ops = mutableListOf<Op>()
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            // Rail ink only: the text renderer strokes fraction bars with the TEXT paint.
            if (paint.color == RAIL_INK) ops += Op.Line(startX, startY, stopX, stopY)
            super.drawLine(startX, startY, stopX, stopY, paint)
        }
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            if (paint.style == Paint.Style.FILL && paint.color == Color.WHITE) ops += Op.Halo(RectF(left, top, right, bottom))
            super.drawRect(left, top, right, bottom, paint)
        }
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            if (text.isNotBlank()) ops += Op.Text(text, x, y)
            super.drawText(text, x, y, paint)
        }
    }

    private fun renderer(blank: Boolean = false) = PdfDimensionRenderer(
        pageX = { mm -> (mm / 2.0).toFloat() + 40f },
        linePaint = Paint().apply { strokeWidth = 0.7f; color = RAIL_INK },
        textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.BLACK },
        objectTopY = 500f,
        arrowSize = 3f,
        blankLabels = blank,
    )

    // A long inline span, a short span whose value floats wider than the span (centred across its
    // extension lines and the neighbour's), a datum above both, and the OAL.
    private val spans = listOf(
        0 to DimSpan(0.0, 60.0, formatLenDimDualLabel(639.8, UnitSystem.INCHES, true), SpanKind.LOCAL),
        0 to DimSpan(60.0, 700.0, formatLenDimDualLabel(1330.3, UnitSystem.INCHES, true), SpanKind.LOCAL),
        1 to DimSpan(0.0, 66.0, formatLenDimDualLabel(649.3, UnitSystem.INCHES, true), SpanKind.DATUM),
        DimensionRailLayout.TOP_RAIL to DimSpan(0.0, 1400.0, formatLenDimDualLabel(9359.9, UnitSystem.INCHES, true), SpanKind.OAL),
    )

    private fun draw(blank: Boolean = false): Pair<OrderCanvas, DimensionRailLayout.Plan> {
        val r = renderer(blank)
        val inputs = spans.map { (rail, span) ->
            r.spanInput(rail, if (rail == DimensionRailLayout.TOP_RAIL) 380f else 460f - 30f * rail, span)
        }
        val plan = r.plan(inputs, safeTopY = 0f, contentLeft = 36f, contentRight = 756f)
        val c = OrderCanvas()
        r.drawPlan(c, spans.map { it.second }, plan.placements)
        return c to plan
    }

    @Test fun `every value has a halo, and every halo comes after every line`() {
        val (c, plan) = draw()
        val halos = c.ops.filterIsInstance<Op.Halo>()
        assertEquals("one halo per value", spans.size, halos.size)
        val lastLine = c.ops.indexOfLast { it is Op.Line }
        val firstHalo = c.ops.indexOfFirst { it is Op.Halo }
        assertTrue("a line is drawn after a halo (line $lastLine, halo $firstHalo)", lastLine < firstHalo)
        assertTrue("the floating value is wider than its span", plan.placements.any { !it.inline })
        plan.placements.forEach { p ->
            val b = p.label
            assertTrue(
                "no halo covers the value box $b",
                halos.any {
                    it.rect.left <= b.left - RAIL_VALUE_HALO_PAD_PT + 1e-3f && it.rect.right >= b.right + RAIL_VALUE_HALO_PAD_PT - 1e-3f &&
                        it.rect.top <= b.top - RAIL_VALUE_HALO_PAD_PT + 1e-3f && it.rect.bottom >= b.bottom + RAIL_VALUE_HALO_PAD_PT - 1e-3f
                },
            )
        }
    }

    @Test fun `every value's text is drawn over a halo already on the page`() {
        val (c, _) = draw()
        c.ops.forEachIndexed { i, op ->
            if (op !is Op.Text) return@forEachIndexed
            val before = c.ops.subList(0, i).filterIsInstance<Op.Halo>()
            assertTrue(
                "\"${op.s}\" is drawn with no halo under it",
                before.any { op.x >= it.rect.left && op.x <= it.rect.right && op.y >= it.rect.top && op.y <= it.rect.bottom },
            )
        }
    }

    @Test fun `a blank draft cuts its write-in gaps with no halo and no text`() {
        val (c, _) = draw(blank = true)
        assertTrue(c.ops.none { it is Op.Halo })
        assertTrue(c.ops.none { it is Op.Text })
    }
}
