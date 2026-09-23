package com.android.shaftschematic.pdf.dim

import com.android.shaftschematic.geom.SetPositions
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.LinerDim
import com.android.shaftschematic.pdf.formatLenDimDualLabel
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs

/** Below this, a datum span is the liner sitting on its SET — nothing to dimension. */
private const val ZERO_SPAN_EPS_MM = 1e-3

/**
 * Builds two spans per liner: (SET→near edge) and (near edge→far edge) — the datum span
 * only when the near edge actually stands off its SET.
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
                val datumLabel = forcedRefMm?.let { formatLenDimDualLabel(abs(start - it), lnUnit, dual) }
                    ?: formatLenDimDualLabel(ln.offsetFromSetMm, lnUnit, dual)
                val localLabel = forcedRefMm?.let { formatLenDimDualLabel(abs(end - it), lnUnit, dual) }
                    ?: formatLenDimDualLabel(ln.lengthMm, lnUnit, dual)
                // A liner sitting ON its datum has no offset to dimension: a zero-length
                // datum span printed a floating "0.000" beside the SET (on-device report).
                if (abs(start - sets.aftSETxMm) > ZERO_SPAN_EPS_MM) {
                    add(DimSpan(sets.aftSETxMm, start, label = datumLabel, kind = SpanKind.DATUM))
                }
                add(DimSpan(start, end, label = localLabel, kind = SpanKind.LOCAL))
            }
            LinerAnchor.FWD_SET -> {
                val fwdEdge = sets.fwdSETxMm - ln.offsetFromSetMm
                val aftEdge = fwdEdge - ln.lengthMm
                val datumLabel = forcedRefMm?.let { formatLenDimDualLabel(abs(fwdEdge - it), lnUnit, dual) }
                    ?: formatLenDimDualLabel(ln.offsetFromSetMm, lnUnit, dual)
                val localLabel = forcedRefMm?.let { formatLenDimDualLabel(abs(aftEdge - it), lnUnit, dual) }
                    ?: formatLenDimDualLabel(ln.lengthMm, lnUnit, dual)
                if (abs(fwdEdge - sets.fwdSETxMm) > ZERO_SPAN_EPS_MM) {
                    add(DimSpan(sets.fwdSETxMm, fwdEdge, label = datumLabel, kind = SpanKind.DATUM))
                }
                add(DimSpan(fwdEdge, aftEdge, label = localLabel, kind = SpanKind.LOCAL))
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
    val value = formatLenDimDualLabel(labelMm, unit, dual)
    return DimSpan(
        x1Mm, x2Mm,
        // The "OAL" identifier rides the PRIMARY term, so a stacked label reads
        // `OAL 133"` over `3378.2 mm` rather than repeating the prefix.
        label = value.copy(primary = "OAL ${value.primary}"),
        kind = SpanKind.OAL,
    )
}
