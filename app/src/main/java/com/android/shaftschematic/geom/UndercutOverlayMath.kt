package com.android.shaftschematic.geom

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutReference

/**
 * Pure math behind the undercut authoring surface — liner-reference resolution, the notch
 * build pipeline, and the sheet's S.E.T. positions. Shared by the detail overlay
 * (`ui/screen/UndercutDetail.kt`) and the undercut route (`ui/screen/UndercutRoute.kt`), so
 * both read a cut the same way. No Android and no Compose: the drawing twins of these
 * helpers live beside them in `ui/screen/UndercutSharedDraw.kt`.
 */

/** Chip labels for [UndercutReference], used in the Distance field's dynamic label. */
internal fun undercutReferenceLabel(reference: UndercutReference): String = when (reference) {
    UndercutReference.AFT_SET -> "AFT S.E.T."
    UndercutReference.FWD_SET -> "FWD S.E.T."
    UndercutReference.LINER_AFT -> "Liner AFT"
    UndercutReference.LINER_FWD -> "Liner FWD"
}

// ─────────────────────────────────────────────────────────────────────────────
// Liner references — shared by the overlay's cards and the route's undercut list
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The reference actually used to display/convert an undercut's Distance. A `LINER_*` reference
 * whose [Undercut.referenceLinerId] no longer resolves falls back to
 * [UndercutReference.AFT_SET] — the model's documented display rule. Canonical storage is never
 * touched by the fallback, and the stored reference is never rewritten behind the machinist's
 * back; the card just shows the AFT S.E.T. chip selected until a reference is picked again.
 */
internal fun effectiveUndercutReference(
    undercut: Undercut,
    linerSpans: List<UndercutLinerSpan>,
): UndercutReference =
    if (undercut.authoredReference == UndercutReference.LINER_AFT ||
        undercut.authoredReference == UndercutReference.LINER_FWD
    ) {
        if (linerSpans.any { it.id == undercut.referenceLinerId }) undercut.authoredReference
        else UndercutReference.AFT_SET
    } else {
        undercut.authoredReference
    }

/**
 * The liner an undercut's `LINER_*` chips convert against, or `null` when no liner is available
 * (the chips are then hidden). Preference order: the undercut's own stored reference liner while
 * it resolves, then the liner of the strip being viewed, then the liner holding the largest share
 * of the cut ([assignUndercutLiner]). The stored liner wins so a cut authored against one liner
 * keeps reading against it even while viewed from a neighbor's strip.
 */
internal fun undercutReferenceLinerFor(
    undercut: Undercut,
    linerSpans: List<UndercutLinerSpan>,
    stripLiner: UndercutLinerSpan?,
    oalMm: Float,
): UndercutLinerSpan? {
    linerSpans.firstOrNull { it.id == undercut.referenceLinerId }?.let { return it }
    stripLiner?.let { return it }
    val clamped = clampUndercutSpan(undercut.startFromAftMm, undercut.lengthMm, oalMm)
    if (clamped.isEmpty) return null
    val assignedId = assignUndercutLiner(
        UndercutSpanMm(undercut.id, clamped.startMm, clamped.endMm), linerSpans,
    ) ?: return null
    return linerSpans.firstOrNull { it.id == assignedId }
}

/**
 * The Distance an undercut reads under its effective reference, in canonical mm — the value the
 * card's field shows and the route's list row summarizes, so the two never disagree.
 */
internal fun undercutDisplayedDistanceMm(
    undercut: Undercut,
    reference: UndercutReference,
    refLiner: UndercutLinerSpan?,
    aftSetXMm: Float,
    fwdSetXMm: Float,
): Float = canonicalToUndercutStartMm(
    reference = reference,
    canonicalStartMm = undercut.startFromAftMm,
    lengthMm = undercut.lengthMm,
    aftSetXMm = aftSetXMm,
    fwdSetXMm = fwdSetXMm,
    linerStartMm = refLiner?.startMm ?: 0f,
    linerEndMm = refLiner?.endMm ?: 0f,
)

// ─────────────────────────────────────────────────────────────────────────────
// Notch pipeline — shared by the overview canvas and the detail overlay
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One undercut's drawable notch: its render-clamped span, the **drawn** floor Ø
 * ([normalizedNotchFloorDiaMm] over [effectiveNotchDiaMm] — drawn depth is exaggerated
 * against the sheet's deepest cut so a 1/16" cut still reads as a cut; a placed-but-empty Ø
 * gets a symbolic shallow floor first), and the surface-relative regions from
 * `geom/SurfaceProfileMath.kt`.
 */
internal data class UndercutNotch(
    val id: String,
    val startMm: Float,
    val endMm: Float,
    val floorDiaMm: Float,
    val profiles: List<NotchProfile>,
)

/**
 * Build every drawable notch for [undercuts] against the local outer surface [segs]. The single
 * pipeline behind both draw sites (this overlay's canvas and the undercut PDF), so the notch a
 * machinist taps on screen is the notch that prints.
 *
 * Regions come from `notchProfiles` at the TRUE effective floor (topology stays honest — a
 * cut that never reached the neighboring body must not draw into it); only the floor Ø on
 * the returned profiles is then swapped for the display-exaggerated one, deepening the
 * drawn floor and shoulders. Printed/stored Ø values are untouched.
 *
 * [exaggerationFrac] is the sheet's drawn-depth setting
 * ([com.android.shaftschematic.model.UndercutRecord.exaggerationFrac]) and
 * [sheetUndercuts] is the WHOLE sheet's cut list — the normalization reference
 * ([deepestUndercutDepthMm]) is per sheet, not per strip, so a strip holding only shallow
 * cuts draws them at the same reduced depth the full drawing gives them. It defaults to
 * [undercuts] for callers that already pass the whole sheet.
 */
internal fun buildUndercutNotches(
    undercuts: List<Undercut>,
    segs: List<SurfaceSeg>,
    oalMm: Float,
    exaggerationFrac: Float,
    sheetUndercuts: List<Undercut> = undercuts,
): List<UndercutNotch> {
    val deepest = deepestUndercutDepthMm(sheetUndercuts, segs, oalMm)
    return undercuts.mapNotNull { u ->
        val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
        if (c.isEmpty) return@mapNotNull null
        val minSurface = minOuterDiaOver(segs, c.startMm, c.endMm)
        val floor = effectiveNotchDiaMm(u.diaMm, minSurface)
        val drawnFloor = normalizedNotchFloorDiaMm(u.diaMm, minSurface, deepest, exaggerationFrac)
        UndercutNotch(
            id = u.id,
            startMm = c.startMm,
            endMm = c.endMm,
            floorDiaMm = drawnFloor,
            profiles = notchProfiles(segs, c.startMm, c.endMm, floor)
                .map { it.copy(floorDiaMm = drawnFloor) },
        )
    }.sortedBy { it.startMm }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas helpers — the pure share; the drawing twins stay in ui/screen
// ─────────────────────────────────────────────────────────────────────────────

/** AFT/FWD S.E.T. x positions in physical shaft space (mm from the AFT face). */
internal fun undercutSetPositions(spec: ShaftSpec): Pair<Float, Float> {
    val win = computeOalWindow(spec)
    val set = computeSetPositionsInMeasureSpace(win, spec)
    return set.aftSETxMm.toFloat() to set.fwdSETxMm.toFloat()
}
