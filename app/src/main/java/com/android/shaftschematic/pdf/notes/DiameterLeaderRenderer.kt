package com.android.shaftschematic.pdf.notes

import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.pdf.formatDim
import com.android.shaftschematic.util.UnitSystem

/**
 * Renders simple diameter leaders with a dog-leg and "Ø" label.
 * Keeps geometry minimal; offsets can be tuned visually.
 */
class DiameterLeaderRenderer(
    private val pageX: (Double) -> Float,   // mm → page X
    private val shaftTopY: Float,
    private val shaftBottomY: Float,
    private val leaderRise: Float = 16f,
    private val leaderDogleg: Float = 14f,
    private val linePaint: Paint,
    private val textPaint: Paint
) {
    fun draw(canvas: Canvas, calls: List<DiaCallout>, unit: UnitSystem) {
        calls.forEach { drawOne(canvas, it, unit) }
    }

    private fun drawOne(canvas: Canvas, call: DiaCallout, unit: UnitSystem) {
        val x = pageX(call.xMm)
        val (startY, kinkY, textY) = when (call.side) {
            LeaderSide.ABOVE -> {
                val s = shaftTopY
                Triple(s, s - leaderRise, s - leaderRise - 2f)
            }
            LeaderSide.BELOW -> {
                val s = shaftBottomY
                Triple(s, s + leaderRise, s + leaderRise + 10f)
            }
        }
        val textX = x + leaderDogleg

        canvas.drawLine(x, startY, x, kinkY, linePaint)
        canvas.drawLine(x, kinkY, textX, kinkY, linePaint)

        val label = "Ø " + formatDim(call.valueMm, unit)
        canvas.drawText(label, textX, textY, textPaint)
    }
}
