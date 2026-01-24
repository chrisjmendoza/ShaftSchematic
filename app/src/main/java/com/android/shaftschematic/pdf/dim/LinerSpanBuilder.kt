package com.android.shaftschematic.pdf.dim

import com.android.shaftschematic.geom.SetPositions
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.LinerDim
import com.android.shaftschematic.pdf.formatLenDim
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs

/**
 * Builds two spans per liner: (SET→near edge) and (near edge→far edge).
 * FWD-anchored: offset to the FWD edge (toward AFT), length AFT.
 */
fun buildLinerSpans(
    liners: List<LinerDim>,
    sets: SetPositions,
    unit: UnitSystem,
    measureFrom: PdfTieringMode
): List<DimSpan> = buildList {
    liners.forEach { ln ->
        val anchor = when (measureFrom) {
            PdfTieringMode.AFT -> LinerAnchor.AFT_SET
            PdfTieringMode.FWD -> LinerAnchor.FWD_SET
            PdfTieringMode.AUTO -> ln.anchor
        }
        val forcedRefMm = when (measureFrom) {
            PdfTieringMode.AFT -> sets.aftSETxMm
            PdfTieringMode.FWD -> sets.fwdSETxMm
            PdfTieringMode.AUTO -> null
        }
        when (anchor) {
            LinerAnchor.AFT_SET -> {
                val start = sets.aftSETxMm + ln.offsetFromSetMm
                val end = start + ln.lengthMm
                val datumLabel = forcedRefMm?.let { formatLenDim(abs(start - it), unit) }
                    ?: formatLenDim(ln.offsetFromSetMm, unit)
                val localLabel = forcedRefMm?.let { formatLenDim(abs(end - it), unit) }
                    ?: formatLenDim(ln.lengthMm, unit)
                add(DimSpan(sets.aftSETxMm, start, labelTop = datumLabel, kind = SpanKind.DATUM, labelBottom = null))
                add(DimSpan(start, end, labelTop = localLabel, kind = SpanKind.LOCAL))
            }
            LinerAnchor.FWD_SET -> {
                val fwdEdge = sets.fwdSETxMm - ln.offsetFromSetMm
                val aftEdge = fwdEdge - ln.lengthMm
                val datumLabel = forcedRefMm?.let { formatLenDim(abs(fwdEdge - it), unit) }
                    ?: formatLenDim(ln.offsetFromSetMm, unit)
                val localLabel = forcedRefMm?.let { formatLenDim(abs(aftEdge - it), unit) }
                    ?: formatLenDim(ln.lengthMm, unit)
                add(DimSpan(sets.fwdSETxMm, fwdEdge, labelTop = datumLabel, kind = SpanKind.DATUM, labelBottom = null))
                add(DimSpan(fwdEdge, aftEdge, labelTop = localLabel, kind = SpanKind.LOCAL))
            }
        }
    }
}

/** OAL span for the top rail. */
fun oalSpan(oalMm: Double, unit: UnitSystem): DimSpan =
    DimSpan(0.0, oalMm, labelTop = "OAL ${formatLenDim(oalMm, unit)}", kind = SpanKind.OAL)
