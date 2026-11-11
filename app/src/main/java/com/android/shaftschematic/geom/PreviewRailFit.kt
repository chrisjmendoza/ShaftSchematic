package com.android.shaftschematic.geom

/**
 * How the editor preview makes room for its dimension rails — pure, so the rule is unit-tested
 * without a canvas (`PreviewRailFitTest`).
 *
 * The preview box is short (a 3:1 strip on a phone), and the schematic's rails stack upward from
 * the shaft: the numbered component rails, then the OAL rail on top. EVERY rail the sheet prints
 * is drawn, always — the preview is a picture of what the sheet will carry, not a fitted layout
 * (on-device ruling: "the rails don't need to fit within the preview constantly"). The box only
 * helps them fit:
 *  1. The rails may take everything the box has above what the SHAFT itself needs — its drawn
 *     height at the width-fitted scale plus the layout's padding and margins (which hold the
 *     selection ring). The shaft keeps its size and only sits lower; the reservation is the
 *     smallest that fits, so a thin shaft with room above it moves no further than needed.
 *  2. When even that is not enough, the shaft sits as low as it goes and the rails run off the
 *     top of the box, where a pan or a zoom-out reaches them. No rail is ever dropped and the
 *     shaft is never drawn smaller to make room.
 *
 * [deficitPx] answers, for a top reservation of `reservePx`, how far the rail block overruns the
 * room above the shaft (≤ 0 = it fits). It must be non-increasing in `reservePx` — a larger
 * reservation only ever moves the shaft down.
 */
object PreviewRailFit {

    /** Overrun (px) treated as a fit — sub-pixel residue of the bisection. */
    const val FIT_TOLERANCE_PX: Float = 0.5f

    private const val BISECT_STEPS = 16

    /** The outcome: the box reserves [reservePx] at its top; [fits] is false when the rails overrun it anyway. */
    data class Fit(val reservePx: Float, val fits: Boolean)

    /**
     * The largest top reservation the rails may take: the box less its layout padding
     * ([layoutChromePx], top and bottom together) less the shaft's width-fitted height
     * [widthFitShaftPx] — the shaft is never squeezed.
     */
    fun maxReservePx(
        boxHeightPx: Float,
        layoutChromePx: Float,
        widthFitShaftPx: Float,
    ): Float = (boxHeightPx - layoutChromePx - widthFitShaftPx).coerceAtLeast(0f)

    /** @param maxReservePx the largest reservation allowed ([maxReservePx]). */
    fun fit(
        maxReservePx: Float,
        deficitPx: (reservePx: Float) -> Float,
    ): Fit {
        val maxReserve = maxReservePx.coerceAtLeast(0f)
        if (deficitPx(0f) <= FIT_TOLERANCE_PX) return Fit(0f, fits = true)
        if (deficitPx(maxReserve) > FIT_TOLERANCE_PX) return Fit(maxReserve, fits = false)
        var lo = 0f
        var hi = maxReserve
        repeat(BISECT_STEPS) {
            val mid = (lo + hi) * 0.5f
            if (deficitPx(mid) <= FIT_TOLERANCE_PX) hi = mid else lo = mid
        }
        return Fit(hi, fits = true)
    }
}
