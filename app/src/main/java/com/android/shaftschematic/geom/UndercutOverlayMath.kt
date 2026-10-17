package com.android.shaftschematic.geom

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutReference
import kotlin.math.abs
import kotlin.math.min

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
    UndercutReference.PARENT_AFT -> "Parent AFT"
    UndercutReference.PARENT_FWD -> "Parent FWD"
}

/** Whether [reference] measures from the shoulders of the cut the undercut is nested in. */
internal fun isParentUndercutReference(reference: UndercutReference): Boolean =
    reference == UndercutReference.PARENT_AFT || reference == UndercutReference.PARENT_FWD

// ─────────────────────────────────────────────────────────────────────────────
// Liner references — shared by the overlay's cards and the route's undercut list
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The reference actually used to display/convert an undercut's Distance. A `PARENT_*` reference
 * whose [Undercut.referenceCutId] no longer resolves in [spans], or whose cut no longer contains
 * this one ([undercutSpanContains]), displays from the S.E.T. nearer the cut's own span — the
 * parent's shoulders are the datum, and a cut it does not sit inside has none. With the default
 * empty [spans] a `PARENT_*` reference passes through unchecked (nothing to resolve it against),
 * as the buried-edge check does. Two display-only fallbacks apply to a `LINER_*` reference:
 *
 * - its [Undercut.referenceLinerId] no longer resolves → [UndercutReference.AFT_SET], the model's
 *   documented display rule;
 * - the referenced liner EDGE is buried — some cut in [spans] runs past it into the body
 *   ([undercutLinerEdgeBuried]) → the S.E.T. nearer the cut's own span ([nearestSetReference]
 *   against [aftSetXMm]/[fwdSetXMm], the schematic's convention). The step that edge made is
 *   machined away, so nothing is left there to measure from (on-device request).
 *
 * [spans] is every cut on the sheet, this cut included (its previewed span while it is being
 * edited); the default empty list skips the buried-edge check entirely. Canonical storage is
 * never touched by either fallback, and the stored reference is never rewritten behind the
 * machinist's back; the card just shows the S.E.T. chip selected until a reference is picked
 * again.
 */
internal fun effectiveUndercutReference(
    undercut: Undercut,
    linerSpans: List<UndercutLinerSpan>,
    spans: List<UndercutSpanMm> = emptyList(),
    aftSetXMm: Float = 0f,
    fwdSetXMm: Float = 0f,
): UndercutReference {
    val ref = undercut.authoredReference
    if (isParentUndercutReference(ref)) {
        if (spans.isEmpty()) return ref
        return if (undercutReferenceParentSpan(undercut, spans) != null) ref
        else nearestSetReference(
            undercut.startFromAftMm, undercut.startFromAftMm + undercut.lengthMm,
            aftSetXMm, fwdSetXMm,
        )
    }
    if (ref != UndercutReference.LINER_AFT && ref != UndercutReference.LINER_FWD) return ref
    val liner = linerSpans.firstOrNull { it.id == undercut.referenceLinerId }
        ?: return UndercutReference.AFT_SET
    val edgeMm = if (ref == UndercutReference.LINER_AFT) liner.startMm else liner.endMm
    return if (spans.isNotEmpty() && undercutLinerEdgeBuried(edgeMm, spans)) {
        nearestSetReference(
            undercut.startFromAftMm, undercut.startFromAftMm + undercut.lengthMm,
            aftSetXMm, fwdSetXMm,
        )
    } else {
        ref
    }
}

/**
 * The span of the cut [undercut]'s `PARENT_*` Distance converts against — the cut named by
 * [Undercut.referenceCutId], found in [spans] (every cut on the sheet, render-clamped), and only
 * while it still contains this cut ([undercutSpanContains], the containment forest's rule; this
 * cut's own span is its entry in [spans], else its stored span). `null` when the id is empty or
 * names this cut, the cut is gone, or it no longer contains this one.
 */
internal fun undercutReferenceParentSpan(
    undercut: Undercut,
    spans: List<UndercutSpanMm>,
): UndercutSpanMm? {
    val parentId = undercut.referenceCutId
    if (parentId.isEmpty() || parentId == undercut.id) return null
    val parent = spans.firstOrNull { it.id == parentId } ?: return null
    val self = spans.firstOrNull { it.id == undercut.id }
        ?: UndercutSpanMm(undercut.id, undercut.startFromAftMm, undercut.startFromAftMm + undercut.lengthMm)
    return parent.takeIf { undercutSpanContains(it, self) }
}

/**
 * The cut the span [id] sits FULLY inside on the sheet [spans] — the smallest container, the
 * containment forest's parent rule ([undercutNestingForest]) — or `null` when it sits inside
 * none. What the card's Parent AFT / Parent FWD chips measure from.
 */
internal fun undercutParentCandidate(id: String, spans: List<UndercutSpanMm>): UndercutSpanMm? {
    val parentId = undercutNestingForest(spans).firstOrNull { it.id == id }?.parentId ?: return null
    return spans.firstOrNull { it.id == parentId }
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
    /** The `PARENT_*` datum's span ([undercutReferenceParentSpan]); read only by those references. */
    refParent: UndercutSpanMm? = null,
): Float = canonicalToUndercutStartMm(
    reference = reference,
    canonicalStartMm = undercut.startFromAftMm,
    lengthMm = undercut.lengthMm,
    aftSetXMm = aftSetXMm,
    fwdSetXMm = fwdSetXMm,
    linerStartMm = refLiner?.startMm ?: 0f,
    linerEndMm = refLiner?.endMm ?: 0f,
    parentStartMm = refParent?.startMm ?: 0f,
    parentEndMm = refParent?.endMm ?: 0f,
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
     *
     * A top-level end BUTTED against another top-level cut ([aftButted]/[fwdButted]) instead
     * rises to that neighbour's DRAWN floor — the material left at the boundary is the
     * neighbour's floor, not the surface — so the pair prints ONE step between the two floors.
     * At or below this cut's own drawn floor it is no face at all (the neighbour is the deeper
     * cut and owns the step).
     */
    val aftFaceTopDiaMm: Float,
    val fwdFaceTopDiaMm: Float,
    /**
     * Whether another top-level cut's FWD end touches this cut's AFT end (and the mirror), within
     * [UNDERCUT_TOUCH_EPS_MM] — the edge-to-edge preset's posture. Always false on a nested cut, whose
     * faces run against its parent instead.
     */
    val aftButted: Boolean = false,
    val fwdButted: Boolean = false,
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
 * Two TOP-LEVEL cuts whose ends touch ([withButtedSiblingFaces]) share their boundary face:
 * each end's face top becomes the neighbour's drawn floor, so the boundary prints one step from
 * the deeper floor up to the shallower one, never a sliver of full-height surface between them.
 * That runs before the nested cuts resolve, so a child flush with a butted shoulder inherits
 * the stepped face top rather than the surface.
 *
 * The result is in PAINT order: level-0 cuts first, each run of butted cuts shallowest first
 * ([inButtedPaintOrder]), then nested cuts by level and aft → fwd.
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
    fun resolve(span: UndercutSpanMm): UndercutFloors? {
        val u = byId[span.id] ?: return null
        val nesting = nestingById[span.id] ?: UndercutNesting(span.id, 0, null)
        val parent = nesting.parentId?.let { resolvedById[it] }
        return if (parent == null) {
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
            // Same slop as containment ([undercutSpanContains]): a child the forest nests on its
            // parent's shoulder within Float drift shares that shoulder's face.
            val sharesAft = abs(span.startMm - parent.span.startMm) <= UNDERCUT_TOUCH_EPS_MM
            val sharesFwd = abs(span.endMm - parent.span.endMm) <= UNDERCUT_TOUCH_EPS_MM
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
    }

    // Top level first and all at once: a butted face needs its neighbour's drawn floor, and a
    // child flush with a butted shoulder needs the stepped face top.
    val (topSpans, nestedSpans) = ordered.partition { nestingById[it.id]?.parentId == null }
    val top = withButtedSiblingFaces(topSpans.mapNotNull(::resolve))
    top.forEach { resolvedById[it.span.id] = it }
    val nested = nestedSpans.mapNotNull { span ->
        resolve(span)?.also { resolvedById[span.id] = it }
    }
    return inButtedPaintOrder(top) + nested
}

private fun undercutEndsTouch(endMm: Float, startMm: Float): Boolean =
    abs(endMm - startMm) <= UNDERCUT_TOUCH_EPS_MM

/**
 * Re-top every butted end of the top-level cuts [top]: an end that another top-level cut's
 * opposite end touches rises to the lowest such neighbour's drawn floor. Drawn floors, because
 * the faces are drawn at the exaggerated depth; true floors are untouched. A run of three or
 * more butted cuts chains pairwise. An end touching a liner edge, the shaft end, or nothing
 * keeps its surface face top.
 */
private fun withButtedSiblingFaces(top: List<UndercutFloors>): List<UndercutFloors> = top.map { f ->
    val aftNeighbourFloor = top
        .filter { it !== f && undercutEndsTouch(it.span.endMm, f.span.startMm) }
        .minOfOrNull { it.drawnFloorDiaMm }
    val fwdNeighbourFloor = top
        .filter { it !== f && undercutEndsTouch(f.span.endMm, it.span.startMm) }
        .minOfOrNull { it.drawnFloorDiaMm }
    if (aftNeighbourFloor == null && fwdNeighbourFloor == null) f
    else f.copy(
        aftFaceTopDiaMm = aftNeighbourFloor ?: f.aftFaceTopDiaMm,
        fwdFaceTopDiaMm = fwdNeighbourFloor ?: f.fwdFaceTopDiaMm,
        aftButted = aftNeighbourFloor != null,
        fwdButted = fwdNeighbourFloor != null,
    )
}

/**
 * Paint order for the top-level cuts [top] (already aft → fwd): each run of butted cuts paints
 * shallowest drawn floor first, so at every shared boundary the DEEPER cut — the one whose face
 * carries the step — paints last. Painted the other way, the later cut's core erase would wipe
 * half the stroke of the face it no longer draws. Cuts that touch nothing keep aft → fwd.
 */
private fun inButtedPaintOrder(top: List<UndercutFloors>): List<UndercutFloors> {
    val runs = mutableListOf<MutableList<UndercutFloors>>()
    for (f in top) {
        val run = runs.firstOrNull { r -> r.any { undercutEndsTouch(it.span.endMm, f.span.startMm) } }
        if (run != null) run += f else runs += mutableListOf(f)
    }
    return runs.flatMap { r -> r.sortedByDescending { it.drawnFloorDiaMm } }
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
 * ## Butted cuts
 * Two top-level cuts whose ends touch print ONE step at the boundary: the shallower cut's face
 * there is dropped and the deeper cut's face rises only to the shallower cut's drawn floor
 * ([buttedSurfacePoints]). Equal floors draw no face at all — one continuous floor.
 *
 * The result is ordered **parents before children** (by nesting level, then aft → fwd, except
 * that a run of butted cuts paints shallowest first so the deeper cut's step face lands on top),
 * so the later-painted child always lands on top of the relief around it — paint-over
 * correctness no longer depends on start-coordinate luck.
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
                if (!nested) p.copy(
                    floorDiaMm = f.drawnFloorDiaMm,
                    surface = buttedSurfacePoints(p, f),
                )
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
 * A top-level notch region's surface polyline for DRAWING, with a **duplicated-x step point**
 * prepended/appended at an end butted against a sibling cut — the [nestedSurfacePoints]
 * construction. The step point sits at the sibling's drawn floor, so the face both draw sites
 * take from the first/last surface point runs from that floor down to this cut's floor: the one
 * real step at the boundary. It is clamped to the surface actually standing at that end, never
 * the span-wide minimum, so a cut overhanging onto smaller stock still steps up to its neighbour.
 * Where the sibling is as deep or deeper the step point lands ON this cut's floor — a
 * zero-height face, which every draw site's `NOTCH_FACE_MIN_STEP_PX` test already skips. The
 * step has zero axial width, so the void fill still erases the whole mouth.
 */
private fun buttedSurfacePoints(p: NotchProfile, f: UndercutFloors): List<SurfacePoint> {
    val eps = UNDERCUT_SPAN_EPS_MM
    val atAft = f.aftButted && abs(p.startMm - f.span.startMm) <= eps
    val atFwd = f.fwdButted && abs(p.endMm - f.span.endMm) <= eps
    if (!atAft && !atFwd) return p.surface
    fun faceTop(surfaceDiaMm: Float, neighbourFloorDiaMm: Float): Float =
        min(surfaceDiaMm, neighbourFloorDiaMm).coerceAtLeast(f.drawnFloorDiaMm)
    return buildList {
        if (atAft) {
            val s = p.surface.first()
            add(SurfacePoint(s.xMm, faceTop(s.diaMm, f.aftFaceTopDiaMm)))
        }
        addAll(p.surface)
        if (atFwd) {
            val s = p.surface.last()
            add(SurfacePoint(s.xMm, faceTop(s.diaMm, f.fwdFaceTopDiaMm)))
        }
    }
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
