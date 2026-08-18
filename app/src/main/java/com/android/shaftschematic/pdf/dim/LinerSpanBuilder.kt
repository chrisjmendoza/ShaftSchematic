package com.android.shaftschematic.pdf.dim

import com.android.shaftschematic.geom.SetPositions
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.LinerDim
import com.android.shaftschematic.pdf.formatLenDimDual
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs

/**
 * Builds two spans per liner: (SET→near edge) and (near edge→far edge).
 * FWD-anchored: offset to the FWD edge (toward AFT), length AFT.
 *
 * Each liner's labels resolve their own display unit via [displayUnits] (keyed by the
 * liner's [LinerDim.id], carried over from the source `Liner.id` — see
 * `ShaftPdfComposer.mapToLinerDimsForPdf`), independent of [unit]. [unit] remains the
 * fallback resolved unit when [displayUnits] is the default single-unit resolver.
 */
fun buildLinerSpans(
    liners: List<LinerDim>,
    sets: SetPositions,
    unit: UnitSystem,
    measureFrom: PdfTieringMode,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
): List<DimSpan> = buildList {
    liners.forEach { ln ->
        val lnUnit = displayUnits.unitFor(ln.id)
        val dual = displayUnits.dual
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
                val datumLabel = forcedRefMm?.let { formatLenDimDual(abs(start - it), lnUnit, dual) }
                    ?: formatLenDimDual(ln.offsetFromSetMm, lnUnit, dual)
                val localLabel = forcedRefMm?.let { formatLenDimDual(abs(end - it), lnUnit, dual) }
                    ?: formatLenDimDual(ln.lengthMm, lnUnit, dual)
                add(DimSpan(sets.aftSETxMm, start, labelTop = datumLabel, kind = SpanKind.DATUM))
                add(DimSpan(start, end, labelTop = localLabel, kind = SpanKind.LOCAL))
            }
            LinerAnchor.FWD_SET -> {
                val fwdEdge = sets.fwdSETxMm - ln.offsetFromSetMm
                val aftEdge = fwdEdge - ln.lengthMm
                val datumLabel = forcedRefMm?.let { formatLenDimDual(abs(fwdEdge - it), lnUnit, dual) }
                    ?: formatLenDimDual(ln.offsetFromSetMm, lnUnit, dual)
                val localLabel = forcedRefMm?.let { formatLenDimDual(abs(aftEdge - it), lnUnit, dual) }
                    ?: formatLenDimDual(ln.lengthMm, lnUnit, dual)
                add(DimSpan(sets.fwdSETxMm, fwdEdge, labelTop = datumLabel, kind = SpanKind.DATUM))
                add(DimSpan(fwdEdge, aftEdge, labelTop = localLabel, kind = SpanKind.LOCAL))
            }
        }
    }
}

/**
 * OAL span for the top rail.
 * [x1Mm]..[x2Mm] sets the bracket position (moves with include/exclude).
 * [labelMm] is the value printed on the label — always the user's typed OAL input,
 * regardless of bracket width.
 *
 * OAL has no owning component, so its unit is always the document unit — callers pass
 * [DisplayUnits.documentUnit] (and [DisplayUnits.dual]) rather than a per-component lookup.
 * [dual] defaults false so the existing single-unit callers (e.g. the consolidated sheet)
 * are unaffected.
 */
fun oalSpan(x1Mm: Double, x2Mm: Double, unit: UnitSystem, labelMm: Double = x2Mm - x1Mm, dual: Boolean = false): DimSpan {
    // Printed spans keep the small "OAL" prefix as a visual identifier (product decision);
    // blank drafts never see it — the renderer cuts an empty break and drops label text.
    return DimSpan(x1Mm, x2Mm, labelTop = "OAL ${formatLenDimDual(labelMm, unit, dual)}", kind = SpanKind.OAL)
}
