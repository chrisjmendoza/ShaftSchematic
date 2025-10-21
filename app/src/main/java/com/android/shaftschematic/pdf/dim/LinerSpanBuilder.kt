package com.android.shaftschematic.pdf.dim

import com.android.shaftschematic.domain.geom.SetPositions
import com.android.shaftschematic.domain.model.LinerAnchor
import com.android.shaftschematic.domain.model.LinerDim

private fun fmt(mm: Double): String = "${"%.3f".format(mm)} mm"

/**
 * Builds two spans per liner: (SET→near edge) and (near edge→far edge).
 */
fun buildLinerSpans(liners: List<LinerDim>, sets: SetPositions): List<DimSpan> = buildList {
    liners.forEach { ln ->
        when (ln.anchor) {
            LinerAnchor.AFT_SET -> {
                val start = sets.aftSETxMm + ln.offsetFromSetMm
                val end = start + ln.lengthMm
                add(DimSpan(sets.aftSETxMm, start, labelTop = fmt(ln.offsetFromSetMm), labelBottom = "AFT SET"))
                add(DimSpan(start, end, labelTop = fmt(ln.lengthMm)))
            }
            LinerAnchor.FWD_SET -> {
                val fwdEdge = sets.fwdSETxMm - ln.offsetFromSetMm
                val aftEdge = fwdEdge - ln.lengthMm
                add(DimSpan(sets.fwdSETxMm, fwdEdge, labelTop = fmt(ln.offsetFromSetMm), labelBottom = "FWD SET"))
                add(DimSpan(fwdEdge, aftEdge, labelTop = fmt(ln.lengthMm)))
            }
        }
    }
}

/** OAL span reserved for top rail. */
fun oalSpan(oalMm: Double): DimSpan =
    DimSpan(0.0, oalMm, labelTop = "OAL ${"%.3f".format(oalMm)}")
