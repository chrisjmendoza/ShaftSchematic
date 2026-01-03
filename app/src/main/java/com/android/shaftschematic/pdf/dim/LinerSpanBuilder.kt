package com.android.shaftschematic.pdf.dim

import com.android.shaftschematic.geom.SetPositions
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.LinerDim
import com.android.shaftschematic.pdf.formatLenDim
import com.android.shaftschematic.util.UnitSystem

/**
 * Builds two spans per liner: (SET→near edge) and (near edge→far edge).
 * FWD-anchored: offset to the FWD edge (toward AFT), length AFT.
 */
fun buildLinerSpans(
    liners: List<LinerDim>,
    sets: SetPositions,
    unit: UnitSystem
): List<DimSpan> = buildList {
    liners.forEach { ln ->
        when (ln.anchor) {
            LinerAnchor.AFT_SET -> {
                val start = sets.aftSETxMm + ln.offsetFromSetMm
                val end = start + ln.lengthMm
                add(DimSpan(sets.aftSETxMm, start, labelTop = formatLenDim(ln.offsetFromSetMm, unit), kind = SpanKind.DATUM, labelBottom = null))
                add(DimSpan(start, end, labelTop = formatLenDim(ln.lengthMm, unit), kind = SpanKind.LOCAL))
            }
            LinerAnchor.FWD_SET -> {
                val fwdEdge = sets.fwdSETxMm - ln.offsetFromSetMm
                val aftEdge = fwdEdge - ln.lengthMm
                add(DimSpan(sets.fwdSETxMm, fwdEdge, labelTop = formatLenDim(ln.offsetFromSetMm, unit), kind = SpanKind.DATUM, labelBottom = null))
                add(DimSpan(fwdEdge, aftEdge, labelTop = formatLenDim(ln.lengthMm, unit), kind = SpanKind.LOCAL))
            }
        }
    }
}

/** OAL span for the top rail. */
fun oalSpan(oalMm: Double, unit: UnitSystem): DimSpan =
    DimSpan(0.0, oalMm, labelTop = "OAL ${formatLenDim(oalMm, unit)}", kind = SpanKind.OAL)
