package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.drawnExtentMm
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftEndHoles
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.dim.DimSpan
import com.android.shaftschematic.pdf.dim.RailSpan
import com.android.shaftschematic.ui.resolved.BodyBlend
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.bodyBlends
import com.android.shaftschematic.ui.resolved.resolvedBodyBaseId
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import kotlin.math.max
import kotlin.math.min

// ──────────────────────────────────────────────────────────────────────────────
// Section sheets — one stretch of the shaft drawn alone on the schematic's page.
//
// A section is a WINDOW on the document's own geometry, never a cropped copy of it: the
// composer draws the same spec through an x map that covers only the window, so every rail
// keeps the value the full sheet prints and every id still resolves. Where the window stops
// short of a shaft end it cuts through the stock and the cut draws as an S-break.
// ──────────────────────────────────────────────────────────────────────────────

/**
 * The stretch of shaft one section sheet draws: [startMm]..[endMm] in shaft mm (AFT = 0), and the
 * [title] printed in the sheet's label row (blank prints none). Built by [resolveSheetSection].
 *
 * [coreStartMm]..[coreEndMm] is the part the section is ABOUT — the components it was asked for.
 * The window may run past it at a cut end by a stub of the neighbouring stock, which is there so
 * the cut reads as a break in a shaft that carries on: whatever the stub clips is drawn, but a
 * taper, thread or liner it clips prints no name, no dimension and no footer lines.
 */
data class SheetSection(
    val startMm: Float,
    val endMm: Float,
    val title: String = "",
    val coreStartMm: Float = startMm,
    val coreEndMm: Float = endMm,
) {
    /** True when [mm] lies inside the core, within [SECTION_EPS_MM]. */
    fun coreHolds(mm: Float): Boolean = mm >= coreStartMm - SECTION_EPS_MM && mm <= coreEndMm + SECTION_EPS_MM

    /** True when [a]..[b] shares more than a face with the core. */
    fun coreOverlaps(a: Float, b: Float): Boolean =
        min(a, b) < coreEndMm - SECTION_EPS_MM && max(a, b) > coreStartMm + SECTION_EPS_MM

    /** True when [mm] lies inside the window, within [SECTION_EPS_MM]. */
    fun holds(mm: Float): Boolean = mm >= startMm - SECTION_EPS_MM && mm <= endMm + SECTION_EPS_MM

    /** True when [a]..[b] shares more than a face with the window. */
    fun overlaps(a: Float, b: Float): Boolean =
        min(a, b) < endMm - SECTION_EPS_MM && max(a, b) > startMm + SECTION_EPS_MM
}

/** Slop for "at the window edge" and "at the shaft end" comparisons. */
const val SECTION_EPS_MM = 0.05f

/** A cut end's stub of neighbouring stock, as a fraction of the diameter it cuts through. */
const val SECTION_STUB_FRAC_OF_DIA = 0.75f

/** Stock a stub keeps past a seal area or blend it shows, as a fraction of the cut diameter. */
const val SECTION_STUB_FEATURE_PAD_FRAC = 0.25f

/** S-break amplitude at a section's cut end, as a fraction of the drawn radius there … */
const val SECTION_BREAK_AMP_FRAC_OF_R = 0.6f

/** … and its absolute cap (the undercut strips' window-end glyph, the same construction). */
const val SECTION_BREAK_AMP_MAX_PT = 18f

/** Page room the x map leaves outside a cut end, so the S-break's lobes stay on the content. */
const val SECTION_BREAK_ROOM_PT = SECTION_BREAK_AMP_MAX_PT * 0.5f + 2f

/**
 * The window a stored section resolves to, or null when a component it names no longer exists.
 *
 * [firstId] / [lastId] are STORED component ids (a body split by liners is one component: every
 * fragment counts); null means "from the AFT end" / "to the FWD end" of the drawn shaft. The two
 * may be given in either order. Where the window stops short of a shaft end it takes a stub of
 * the neighbouring stock — [SECTION_STUB_FRAC_OF_DIA] of the diameter there, and never less than
 * a seal area or blend on the face it butts against plus [SECTION_STUB_FEATURE_PAD_FRAC] — so the
 * cut reads as a break in a shaft that carries on, and a stub that would leave only a sliver
 * before the shaft end runs to the end instead.
 */
fun resolveSheetSection(
    spec: ShaftSpec,
    components: List<ResolvedComponent>,
    firstId: String?,
    lastId: String?,
    title: String = "",
): SheetSection? {
    val extent = spec.drawnExtentMm()
    fun spanOf(id: String): Pair<Float, Float>? {
        val runs = components.filter { resolvedBodyBaseId(it.id) == id }
        if (runs.isEmpty()) return null
        return runs.minOf { it.startMmPhysical } to runs.maxOf { it.endMmPhysical }
    }
    val first = if (firstId == null) null else (spanOf(firstId) ?: return null)
    val last = if (lastId == null) null else (spanOf(lastId) ?: return null)
    val starts = listOfNotNull(first?.first, last?.first)
    val ends = listOfNotNull(first?.second, last?.second)
    var start = if (firstId == null) extent.startMm else starts.min()
    var end = if (lastId == null) extent.endMm else ends.max()
    start = start.coerceIn(extent.startMm, extent.endMm)
    end = end.coerceIn(extent.startMm, extent.endMm)
    if (end - start <= SECTION_EPS_MM) return null
    val coreStart = start
    val coreEnd = end

    val segs = surfaceSegsFrom(components)
    val blends = bodyBlends(spec, components)
    // What a body face butting the window from OUTSIDE carries inward of itself: its blend and
    // its seal area both run away from the window, into the stub.
    fun featureAt(faceMm: Float, end: LinerAuthoredReference): Float = blends
        .filter { it.end == end && kotlin.math.abs(it.faceMm - faceMm) <= SECTION_EPS_MM * 4f }
        .maxOfOrNull { max(it.lengthMm, it.sealLenMm) } ?: 0f

    if (start > extent.startMm + SECTION_EPS_MM) {
        val dia = outerDiaAt(segs, start - 1f).takeIf { it > 0f } ?: outerDiaAt(segs, start + 1f)
        val feature = featureAt(start, LinerAuthoredReference.FWD)
        val stub = max(dia * SECTION_STUB_FRAC_OF_DIA, feature + dia * SECTION_STUB_FEATURE_PAD_FRAC)
        start = (start - stub).coerceAtLeast(extent.startMm)
        if (start - extent.startMm < dia * SECTION_STUB_FEATURE_PAD_FRAC) start = extent.startMm
    }
    if (end < extent.endMm - SECTION_EPS_MM) {
        val dia = outerDiaAt(segs, end + 1f).takeIf { it > 0f } ?: outerDiaAt(segs, end - 1f)
        val feature = featureAt(end, LinerAuthoredReference.AFT)
        val stub = max(dia * SECTION_STUB_FRAC_OF_DIA, feature + dia * SECTION_STUB_FEATURE_PAD_FRAC)
        end = (end + stub).coerceAtMost(extent.endMm)
        if (extent.endMm - end < dia * SECTION_STUB_FEATURE_PAD_FRAC) end = extent.endMm
    }
    return SheetSection(start, end, title, coreStart, coreEnd)
}

/** True when the window's AFT edge cuts through stock rather than sitting on the shaft's own end. */
fun SheetSection.cutsAft(spec: ShaftSpec): Boolean = startMm > spec.drawnExtentMm().startMm + SECTION_EPS_MM

/** True when the window's FWD edge cuts through stock rather than sitting on the shaft's own end. */
fun SheetSection.cutsFwd(spec: ShaftSpec): Boolean = endMm < spec.drawnExtentMm().endMm - SECTION_EPS_MM

/**
 * [this] with only what the section shows: every taper, thread, liner, bolt row and stored body
 * that shares more than a face with the window, each exactly as stored — nothing moves, the
 * overall length included, so AFT / FWD still mean the shaft's own ends. An end's draw-plate
 * holes stay only where the window reaches that end. The draw passes and the footer read this.
 */
fun ShaftSpec.inSection(section: SheetSection): ShaftSpec = copy(
    bodies = bodies.filter { section.overlaps(it.startFromAftMm, it.startFromAftMm + it.lengthMm) },
    tapers = tapers.filter { section.overlaps(it.startFromAftMm, it.startFromAftMm + it.lengthMm) },
    threads = threads.filter { section.overlaps(it.startFromAftMm, it.startFromAftMm + it.lengthMm) },
    liners = liners.filter { section.overlaps(it.startFromAftMm, it.startFromAftMm + it.lengthMm) },
    couplerBoltSlots = couplerBoltSlots.filter { s ->
        s.count >= 1 && section.overlaps(
            s.centerMmAt(0) - s.holeDiaMm * 0.5f,
            s.centerMmAt(s.count - 1) + s.holeDiaMm * 0.5f,
        )
    },
    aftEndHoles = if (section.cutsAft(this)) ShaftEndHoles() else aftEndHoles,
    fwdEndHoles = if (section.cutsFwd(this)) ShaftEndHoles() else fwdEndHoles,
)

/**
 * What a section sheet's TEXT is about — the footer and the Ø callouts read this: [inSection]
 * less every taper, thread, liner and bolt row that only a cut end's stub clips. Bodies stay:
 * the stock in a stub is the same body the section's own components sit on.
 */
fun ShaftSpec.inSectionCore(section: SheetSection): ShaftSpec = inSection(section).let { shown ->
    shown.copy(
        tapers = shown.tapers.filter { section.coreOverlaps(it.startFromAftMm, it.startFromAftMm + it.lengthMm) },
        threads = shown.threads.filter { section.coreOverlaps(it.startFromAftMm, it.startFromAftMm + it.lengthMm) },
        liners = shown.liners.filter { section.coreOverlaps(it.startFromAftMm, it.startFromAftMm + it.lengthMm) },
        couplerBoltSlots = shown.couplerBoltSlots.filter { s ->
            section.coreOverlaps(s.centerMmAt(0) - s.holeDiaMm * 0.5f, s.centerMmAt(s.count - 1) + s.holeDiaMm * 0.5f)
        },
    )
}

/**
 * The drawn body runs of a section: each run of [bodies] cut back to the window, running
 * [reachAftMm] / [reachFwdMm] PAST a cut end so its fill and outline have something for the
 * S-break's clip to cut ([breakWindowClipPath]) and its square end face falls outside the clip.
 * Runs that share no more than a face with the window are dropped.
 */
fun bodiesInSection(
    bodies: List<Body>,
    section: SheetSection,
    reachAftMm: Float,
    reachFwdMm: Float,
): List<Body> = bodies.mapNotNull { b ->
    val a = b.startFromAftMm
    val e = b.startFromAftMm + b.lengthMm
    if (!section.overlaps(a, e)) return@mapNotNull null
    val lo = max(a, section.startMm - reachAftMm)
    val hi = min(e, section.endMm + reachFwdMm)
    if (lo == a && hi == e) b else b.copy(startFromAftMm = lo, lengthMm = hi - lo)
}

/** The blends whose face the section shows; a face the window cut away has no blend to draw. */
fun blendsInSection(blends: List<BodyBlend>, section: SheetSection): List<BodyBlend> =
    blends.filter { section.holds(it.faceMm) }

/**
 * [spans] cut back to the window. A name whose component the window does not show is dropped, and
 * so is the name of a taper, thread or liner that only a cut end's stub clips; a body's name
 * prints wherever the body shows.
 */
fun labelSpansInSection(spans: List<ComponentLabelSpan>, section: SheetSection): List<ComponentLabelSpan> =
    spans.mapNotNull { s ->
        val shown = if (s.isBody) section.overlaps(s.startMm, s.endMm) else section.coreOverlaps(s.startMm, s.endMm)
        if (!shown) null
        else s.copy(startMm = max(s.startMm, section.startMm), endMm = min(s.endMm, section.endMm))
    }

/**
 * The rails a section sheet prints, out of the full sheet's, on consecutive rails (a rail left
 * empty closes up). A span prints when it dimensions something the section is about — at least
 * one end on the section's core — and its other end is either on the sheet too, or is a DATUM
 * (a S.E.T. or a shaft end, [datumsMm]) the sheet does not show: that span keeps its full value
 * and prints open at the cut (`PdfDimensionRenderer` clips it to the window). A span from a datum
 * on the sheet to a feature off it dimensions nothing here and is dropped. The overall-length
 * rail prints only when the window holds both of its ends.
 */
fun SchematicRailSpans.inSection(section: SheetSection): SchematicRailSpans {
    fun shaftMm(dimMm: Double) = (dimMm + win.measureStartMm).toFloat()
    fun inside(dimMm: Double) = section.holds(shaftMm(dimMm))
    fun inCore(dimMm: Double) = section.coreHolds(shaftMm(dimMm))
    fun isDatum(dimMm: Double) = datumsMm.any { kotlin.math.abs(it - dimMm) <= SECTION_EPS_MM }
    fun shown(span: DimSpan): Boolean {
        val a = span.x1Mm
        val b = span.x2Mm
        if (!inCore(a) && !inCore(b)) return false
        return (inside(a) || isDatum(a)) && (inside(b) || isDatum(b))
    }
    val kept = assignments.filter { shown(it.span) }
    val rank = kept.map { it.rail }.distinct().sorted().withIndex().associate { (i, rail) -> rail to i }
    return SchematicRailSpans(
        win = win,
        assignments = kept.map { RailSpan(rank.getValue(it.rail), it.span) },
        oalDimSpan = oalDimSpan,
        datumsMm = datumsMm,
        printsOal = inside(oalDimSpan.x1Mm) && inside(oalDimSpan.x2Mm),
    )
}
