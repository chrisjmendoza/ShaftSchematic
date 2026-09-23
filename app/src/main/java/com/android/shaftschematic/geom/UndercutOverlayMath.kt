package com.android.shaftschematic.geom

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutReference
import kotlin.math.abs

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
 * One cut's resolved floors, the shared step behind [buildUndercutNotches] and the authoring
 * card's implausible-Ø warning: where the cut sits in the containment forest ([nesting]), the
 * local surface it is cut against ([surfaceDiaMm] — the outer envelope's minimum for a
 * top-level cut, the PARENT's true floor for a nested one), the surface as DRAWN
 * ([drawnSurfaceDiaMm], which for a nested cut is the parent's exaggerated floor), and the
 * cut's own true / drawn floors.
 */
internal data class UndercutFloors(
    val nesting: UndercutNesting,
    val span: UndercutSpanMm,
    val surfaceDiaMm: Float,
    val drawnSurfaceDiaMm: Float,
    val trueFloorDiaMm: Float,
    val drawnFloorDiaMm: Float,
    /**
     * Drawn Ø the cut's AFT / FWD **section face** rises to. Normally the local surface it is
     * cut against ([drawnSurfaceDiaMm]), but a nested cut running right up to its parent's own
     * shoulder shares that face: there is no material at the parent's floor at that station, so
     * the face runs from the OUTER surface straight down to this cut's floor — the parent's
     * face top, carried down the chain. That is exactly the silhouette two separately-authored
     * adjacent sections would print.
     */
    val aftFaceTopDiaMm: Float,
    val fwdFaceTopDiaMm: Float,
)

/**
 * Resolve every drawable cut's floors against the local outer surface [segs], **parents before
 * children** so a nested cut can read its parent's results.
 *
 * A TOP-LEVEL cut is cut against the shaft's own surface: true floor from [effectiveNotchDiaMm]
 * over `minOuterDiaOver`, drawn floor from [normalizedNotchFloorDiaMm]. A NESTED cut is cut
 * against its PARENT's floor instead — that floor is its local surface — and its drawn depth
 * stacks below the parent's drawn floor ([nestedNotchFloorDiaMm]). Cuts whose clamped span is
 * empty are dropped.
 *
 * [exaggerationFrac] is the sheet's drawn-depth setting and [sheetUndercuts] the whole sheet's
 * list, since the normalization reference ([deepestUndercutDepthMm]) is per sheet.
 */
internal fun resolveUndercutFloors(
    undercuts: List<Undercut>,
    segs: List<SurfaceSeg>,
    oalMm: Float,
    exaggerationFrac: Float,
    sheetUndercuts: List<Undercut> = undercuts,
): List<UndercutFloors> {
    val deepest = deepestUndercutDepthMm(sheetUndercuts, segs, oalMm)
    val byId = undercuts.associateBy { it.id }
    val spans = undercuts.mapNotNull { u ->
        val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
        if (c.isEmpty) null else UndercutSpanMm(u.id, c.startMm, c.endMm)
    }
    val nestingById = undercutNestingForest(spans).associateBy { it.id }
    val ordered = spans.sortedWith(
        compareBy({ nestingById[it.id]?.level ?: 0 }, { it.startMm }),
    )

    val resolvedById = HashMap<String, UndercutFloors>()
    val out = mutableListOf<UndercutFloors>()
    for (span in ordered) {
        val u = byId[span.id] ?: continue
        val nesting = nestingById[span.id] ?: UndercutNesting(span.id, 0, null)
        val parent = nesting.parentId?.let { resolvedById[it] }
        val floors = if (parent == null) {
            val minSurface = minOuterDiaOver(segs, span.startMm, span.endMm)
            UndercutFloors(
                nesting = nesting,
                span = span,
                surfaceDiaMm = minSurface,
                drawnSurfaceDiaMm = minSurface,
                trueFloorDiaMm = effectiveNotchDiaMm(u.diaMm, minSurface),
                drawnFloorDiaMm = normalizedNotchFloorDiaMm(
                    u.diaMm, minSurface, deepest, exaggerationFrac,
                ),
                aftFaceTopDiaMm = minSurface,
                fwdFaceTopDiaMm = minSurface,
            )
        } else {
            // A face shared with the parent's own shoulder rises to whatever the parent's face
            // rises to (recursively, so a level-2 cut flush through both levels still reaches the
            // shaft surface); a face inboard of the parent steps off the parent's floor.
            val sharesAft = abs(span.startMm - parent.span.startMm) <= UNDERCUT_SPAN_EPS_MM
            val sharesFwd = abs(span.endMm - parent.span.endMm) <= UNDERCUT_SPAN_EPS_MM
            UndercutFloors(
                nesting = nesting,
                span = span,
                surfaceDiaMm = parent.trueFloorDiaMm,
                drawnSurfaceDiaMm = parent.drawnFloorDiaMm,
                trueFloorDiaMm = effectiveNotchDiaMm(u.diaMm, parent.trueFloorDiaMm),
                drawnFloorDiaMm = nestedNotchFloorDiaMm(
                    childDiaMm = u.diaMm,
                    parentTrueFloorDiaMm = parent.trueFloorDiaMm,
                    parentDrawnFloorDiaMm = parent.drawnFloorDiaMm,
                    deepestDepthMm = deepest,
                    exaggerationFrac = exaggerationFrac,
                ),
                aftFaceTopDiaMm = if (sharesAft) parent.aftFaceTopDiaMm else parent.drawnFloorDiaMm,
                fwdFaceTopDiaMm = if (sharesFwd) parent.fwdFaceTopDiaMm else parent.drawnFloorDiaMm,
            )
        }
        resolvedById[span.id] = floors
        out += floors
    }
    return out
}

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
 * ## Nested cuts
 * A cut machined INSIDE another one ([undercutNestingForest]) is cut against its PARENT's
 * floor, not the shaft surface: its topology comes from a one-segment local surface at the
 * parent's TRUE floor, so faces and regions top out there and a child at or above that floor
 * yields no region at all (nothing drawn — the card's non-blocking Ø warning is what tells the
 * machinist). Its profiles are then swapped for drawing — floor to the child's DRAWN floor
 * ([nestedNotchFloorDiaMm]), every surface point to the parent's DRAWN floor — so the faces run
 * outer-drawn-floor → inner-drawn-floor and the two levels read as a staircase. Neither draw
 * site needs any nesting logic of its own: the void erasing the parent's floor line across the
 * child span, and the child core refilled between the child's floors, are exactly right. A
 * child running right up to its parent's own shoulder gets a face reaching the OUTER surface
 * there ([nestedSurfacePoints]), so a shared edge prints as one continuous face — the
 * silhouette two separately-authored adjacent sections would give.
 *
 * The result is ordered **parents before children** (by nesting level, then aft → fwd), so the
 * later-painted child always lands on top of the relief around it — paint-over correctness no
 * longer depends on start-coordinate luck.
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
): List<UndercutNotch> =
    resolveUndercutFloors(undercuts, segs, oalMm, exaggerationFrac, sheetUndercuts).map { f ->
        val span = f.span
        val nested = f.nesting.parentId != null
        val localSegs =
            if (!nested) segs
            else listOf(SurfaceSeg(span.startMm, span.endMm, f.surfaceDiaMm, f.surfaceDiaMm))
        val profiles = notchProfiles(localSegs, span.startMm, span.endMm, f.trueFloorDiaMm)
            .map { p ->
                if (!nested) p.copy(floorDiaMm = f.drawnFloorDiaMm)
                else p.copy(
                    floorDiaMm = f.drawnFloorDiaMm,
                    surface = nestedSurfacePoints(p, f),
                )
            }
        UndercutNotch(
            id = span.id,
            startMm = span.startMm,
            endMm = span.endMm,
            floorDiaMm = f.drawnFloorDiaMm,
            profiles = profiles,
        )
    }

/**
 * A nested notch region's surface polyline for DRAWING: every point on the parent's drawn floor
 * (the child's local surface), with a **duplicated-x step point** prepended/appended at an end
 * the cut shares with its parent's own shoulder — `SurfaceProfileMath`'s step convention.
 *
 * That step is what makes a shared edge print as ONE continuous section face from the outer
 * surface down to this cut's floor, the way two separately-authored adjacent sections would.
 * Both draw sites take a region's face height from its first/last surface point and draw the
 * face AFTER the void, so the child's own face covers the stroke-width sliver its void erased
 * off the parent's face at that station — without either draw site knowing about nesting. The
 * step has zero axial width, so the void fill's area is unchanged.
 */
private fun nestedSurfacePoints(p: NotchProfile, f: UndercutFloors): List<SurfacePoint> {
    val eps = UNDERCUT_SPAN_EPS_MM
    val onFloor = p.surface.map { it.copy(diaMm = f.drawnSurfaceDiaMm) }
    val aftShared = f.aftFaceTopDiaMm > f.drawnSurfaceDiaMm + eps &&
        abs(p.startMm - f.span.startMm) <= eps
    val fwdShared = f.fwdFaceTopDiaMm > f.drawnSurfaceDiaMm + eps &&
        abs(p.endMm - f.span.endMm) <= eps
    if (!aftShared && !fwdShared) return onFloor
    return buildList {
        if (aftShared) add(SurfacePoint(p.startMm, f.aftFaceTopDiaMm))
        addAll(onFloor)
        if (fwdShared) add(SurfacePoint(p.endMm, f.fwdFaceTopDiaMm))
    }
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
