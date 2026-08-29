package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.AutoDiaOverride
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.LinerShoulder
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.shoulderOn
import com.android.shaftschematic.model.autoSectionDiaMmFor
import com.android.shaftschematic.model.hasAutoSectionDia
import com.android.shaftschematic.ui.config.AddDefaultsConfig
import java.util.Locale
import kotlin.math.max

/**
 * Resolved component model used for layout/rendering and UI visibility.
 * Canonical units: millimeters (mm).
 */
enum class ResolvedComponentType { BODY, BODY_AUTO, TAPER, THREAD, LINER, COUPLER_BOLT_SLOT }

enum class ResolvedComponentSource { EXPLICIT, AUTO }

sealed class ResolvedComponent {
    abstract val id: String
    abstract val type: ResolvedComponentType
    abstract val source: ResolvedComponentSource
    abstract val startMmPhysical: Float
    abstract val endMmPhysical: Float
}

data class ResolvedBody(
    override val id: String,
    override val type: ResolvedComponentType,
    override val source: ResolvedComponentSource,
    override val startMmPhysical: Float,
    override val endMmPhysical: Float,
    val diaMm: Float,
) : ResolvedComponent()

data class ResolvedTaper(
    override val id: String,
    override val type: ResolvedComponentType = ResolvedComponentType.TAPER,
    override val source: ResolvedComponentSource = ResolvedComponentSource.EXPLICIT,
    override val startMmPhysical: Float,
    override val endMmPhysical: Float,
    val startDiaMm: Float,
    val endDiaMm: Float,
) : ResolvedComponent()

data class ResolvedThread(
    override val id: String,
    override val type: ResolvedComponentType = ResolvedComponentType.THREAD,
    override val source: ResolvedComponentSource = ResolvedComponentSource.EXPLICIT,
    override val startMmPhysical: Float,
    override val endMmPhysical: Float,
    val majorDiaMm: Float,
    val pitchMm: Float,
) : ResolvedComponent()

/**
 * A resolved liner. Liners never fragment, so the resolved id is the stored id and the
 * shoulders are copied straight off the stored [com.android.shaftschematic.model.Liner] —
 * the surface envelope ([surfaceSegsFrom]) needs the stepped OD, and carrying it here means
 * no call site can forget to supply it.
 */
data class ResolvedLiner(
    override val id: String,
    override val type: ResolvedComponentType = ResolvedComponentType.LINER,
    override val source: ResolvedComponentSource = ResolvedComponentSource.EXPLICIT,
    override val startMmPhysical: Float,
    override val endMmPhysical: Float,
    val odMm: Float,
    val shoulderAft: LinerShoulder? = null,
    val shoulderFwd: LinerShoulder? = null,
) : ResolvedComponent()

/**
 * A row of coupler bolt cutouts. Purely an overlay/reference feature — it does NOT participate
 * in auto-body derivation or body subtraction (it is appended after body resolution). It exists
 * as a [ResolvedComponent] so it gets a carousel card, ordering, and selection/highlight.
 */
data class ResolvedCouplerBoltSlot(
    override val id: String,
    override val type: ResolvedComponentType = ResolvedComponentType.COUPLER_BOLT_SLOT,
    override val source: ResolvedComponentSource = ResolvedComponentSource.EXPLICIT,
    override val startMmPhysical: Float,
    override val endMmPhysical: Float,
    val holeDiaMm: Float,
    val count: Int,
    val spacingMm: Float,
    val through: Boolean,
    val depthMm: Float,
) : ResolvedComponent()

fun resolveComponents(spec: ShaftSpec): List<ResolvedComponent> {
    val explicit = resolveExplicitComponents(spec)
    val autoBodies = deriveAutoBodies(
        overallLengthMm = spec.overallLengthMm,
        explicitComponents = explicit,
        overrideDiaMm = spec.autoBodyDiaMm,
        sectionOverrides = spec.autoDiaOverrides
    )
    val merged = (explicit + autoBodies).sortedWith(
        compareBy<ResolvedComponent>({ it.startMmPhysical }, { it.typeSortKey() })
    )
    val subtracted = subtractBodiesAgainstNonBodies(merged)
    val bodiesAndFeatures = normalizeBodies(
        subtracted,
        overrideDiaMm = spec.autoBodyDiaMm,
        sectionOverrides = spec.autoDiaOverrides
    )

    // Coupler bolt slots are overlays: resolved separately and appended so they never enter
    // auto-body/subtraction geometry, then merged back in physical order for the carousel.
    val slots = resolveCouplerBoltSlots(spec)
    if (slots.isEmpty()) return bodiesAndFeatures
    return (bodiesAndFeatures + slots).sortedWith(
        compareBy<ResolvedComponent>({ it.startMmPhysical }, { it.typeSortKey() })
    )
}

fun resolveCouplerBoltSlots(spec: ShaftSpec): List<ResolvedComponent> =
    spec.couplerBoltSlots.map { cs ->
        ResolvedCouplerBoltSlot(
            id = cs.id,
            startMmPhysical = cs.startFromAftMm,
            endMmPhysical = cs.startFromAftMm + cs.lengthMm,
            holeDiaMm = cs.holeDiaMm,
            count = cs.count,
            spacingMm = cs.spacingMm,
            through = cs.through,
            depthMm = cs.depthMm,
        )
    }

fun resolveExplicitComponents(spec: ShaftSpec): List<ResolvedComponent> = buildList {
    spec.bodies.forEach { b ->
        add(
            ResolvedBody(
                id = b.id,
                type = ResolvedComponentType.BODY,
                source = ResolvedComponentSource.EXPLICIT,
                startMmPhysical = b.startFromAftMm,
                endMmPhysical = b.startFromAftMm + b.lengthMm,
                diaMm = b.diaMm
            )
        )
    }
    spec.tapers.forEach { t ->
        add(
            ResolvedTaper(
                id = t.id,
                startMmPhysical = t.startFromAftMm,
                endMmPhysical = t.startFromAftMm + t.lengthMm,
                startDiaMm = t.startDiaMm,
                endDiaMm = t.endDiaMm
            )
        )
    }
    spec.threads.forEach { th ->
        add(
            ResolvedThread(
                id = th.id,
                startMmPhysical = th.startFromAftMm,
                endMmPhysical = th.startFromAftMm + th.lengthMm,
                majorDiaMm = th.majorDiaMm,
                pitchMm = th.pitchMm
            )
        )
    }
    spec.liners.forEach { ln ->
        add(
            ResolvedLiner(
                id = ln.id,
                startMmPhysical = ln.startFromAftMm,
                endMmPhysical = ln.startFromAftMm + ln.lengthMm,
                odMm = ln.odMm,
                shoulderAft = ln.shoulderOn(LinerAuthoredReference.AFT),
                shoulderFwd = ln.shoulderOn(LinerAuthoredReference.FWD)
            )
        )
    }
}

/**
 * Derive auto body segments from explicit component spans.
 *
 * Per-span diameter precedence:
 * 1. [sectionOverrides] — the aft-most [AutoDiaOverride] anchored inside the span
 *    (`[start, end)`); the rest stay dormant. A merged run therefore takes the Ø of its more
 *    aftward section, since aft is authored first.
 * 2. [overrideDiaMm] > 0 — the legacy shaft-wide bare-shaft Ø ([ShaftSpec.autoBodyDiaMm]).
 * 3. Neighbor derivation ([resolveAutoBodyDia]).
 *
 * Diameter only: no input here moves a span boundary.
 */
fun deriveAutoBodies(
    overallLengthMm: Float,
    explicitComponents: List<ResolvedComponent>,
    overrideDiaMm: Float = 0f,
    sectionOverrides: List<AutoDiaOverride> = emptyList()
): List<ResolvedComponent> {
    val explicit = explicitComponents
        .filter { it.source == ResolvedComponentSource.EXPLICIT }
        .sortedBy { it.startMmPhysical }

    data class Span(val start: Float, val end: Float)

    fun autoDia(startMm: Float, endMm: Float): Float =
        sectionOverrides.autoSectionDiaMmFor(startMm, endMm)
            ?: if (overrideDiaMm > 0f) overrideDiaMm else resolveAutoBodyDia(startMm, explicit)

    if (explicit.isEmpty()) {
        if (overallLengthMm <= 0f) return emptyList()
        return listOf(
            ResolvedBody(
                id = autoBodyId(0f, overallLengthMm),
                type = ResolvedComponentType.BODY_AUTO,
                source = ResolvedComponentSource.AUTO,
                startMmPhysical = 0f,
                endMmPhysical = overallLengthMm,
                diaMm = autoDia(0f, overallLengthMm)
            )
        )
    }

    val spans = mutableListOf<Span>()

    // Gaps between explicit components (always on)
    for (i in 0 until explicit.size - 1) {
        val gapStart = explicit[i].endMmPhysical
        val gapEnd = explicit[i + 1].startMmPhysical
        if (gapEnd > gapStart) spans.add(Span(gapStart, gapEnd))
    }

    // Leading/trailing spans only when OAL is manually specified (overallLengthMm > 0)
    if (overallLengthMm > 0f) {
        val first = explicit.first()
        val last = explicit.last()
        if (first.startMmPhysical > 0f) spans.add(Span(0f, first.startMmPhysical))
        if (overallLengthMm > last.endMmPhysical) spans.add(Span(last.endMmPhysical, overallLengthMm))
    }

    return spans.mapNotNull { span ->
        val length = span.end - span.start
        if (length <= 0f) return@mapNotNull null

        val dia = autoDia(span.start, span.end)
        val id = autoBodyId(span.start, span.end)
        ResolvedBody(
            id = id,
            type = ResolvedComponentType.BODY_AUTO,
            source = ResolvedComponentSource.AUTO,
            startMmPhysical = span.start,
            endMmPhysical = span.end,
            diaMm = dia
        )
    }
}

fun ResolvedComponent.maxDiaMm(): Float = when (this) {
    is ResolvedBody -> diaMm
    is ResolvedTaper -> max(startDiaMm, endDiaMm)
    is ResolvedThread -> majorDiaMm
    is ResolvedLiner -> odMm
    is ResolvedCouplerBoltSlot -> 0f // overlay; does not define shaft OD
}

private fun ResolvedComponent.aftDiaMm(): Float = when (this) {
    is ResolvedBody -> diaMm
    is ResolvedTaper -> startDiaMm
    is ResolvedThread -> majorDiaMm
    is ResolvedLiner -> odMm
    is ResolvedCouplerBoltSlot -> 0f
}

private fun ResolvedComponent.fwdDiaMm(): Float = when (this) {
    is ResolvedBody -> diaMm
    is ResolvedTaper -> endDiaMm
    is ResolvedThread -> majorDiaMm
    is ResolvedLiner -> odMm
    is ResolvedCouplerBoltSlot -> 0f
}

private fun resolveAutoBodyDia(startMm: Float, explicit: List<ResolvedComponent>): Float {
    val upstreamBody = explicit
        .filterIsInstance<ResolvedBody>()
        .filter { it.source == ResolvedComponentSource.EXPLICIT && it.endMmPhysical <= startMm }
        .maxByOrNull { it.endMmPhysical }
    if (upstreamBody != null) return upstreamBody.diaMm

    val upstream = explicit
        .filter { it.endMmPhysical <= startMm }
        .maxByOrNull { it.endMmPhysical }
    if (upstream != null) return upstream.fwdDiaMm()

    val downstreamBody = explicit
        .filterIsInstance<ResolvedBody>()
        .filter { it.source == ResolvedComponentSource.EXPLICIT }
        .minByOrNull { it.startMmPhysical }
    if (downstreamBody != null) return downstreamBody.diaMm

    val downstream = explicit.minByOrNull { it.startMmPhysical }
    if (downstream != null) return downstream.aftDiaMm()

    return AddDefaultsConfig.BODY_DIA_MM
}

private fun autoBodyId(startMm: Float, endMm: Float): String =
    "auto_body_${"%.3f".format(Locale.US, startMm)}_${"%.3f".format(Locale.US, endMm)}"

private fun ResolvedComponent.typeSortKey(): Int = when (type) {
    ResolvedComponentType.BODY -> 0
    ResolvedComponentType.BODY_AUTO -> 1
    ResolvedComponentType.TAPER -> 2
    ResolvedComponentType.THREAD -> 3
    ResolvedComponentType.LINER -> 4
    ResolvedComponentType.COUPLER_BOLT_SLOT -> 5
}

/**
 * Separator between a stored body id and the fragment ordinal appended by
 * [subtractBodiesAgainstNonBodies]. Cannot occur in a stored id (UUID) or an auto-body id
 * (`auto_body_<start>_<end>`), so [resolvedBodyBaseId] is unambiguous.
 */
private const val BODY_FRAGMENT_ID_SEPARATOR = '#'

/**
 * The stored (spec) body id behind a resolved body id.
 *
 * A stored body trimmed around a taper/thread/liner resolves into several [ResolvedBody]
 * rows; the first keeps the stored id and the rest get `"<id>#2"`, `"<id>#3"`, … so every
 * resolved row is uniquely identifiable (carousel pager keys, selection, highlight).
 * Anything that looks a fragment id back up in the spec must strip the suffix first.
 */
fun resolvedBodyBaseId(id: String): String = id.substringBefore(BODY_FRAGMENT_ID_SEPARATOR)

/**
 * Ids of the body runs that must draw UNFILLED — the composers' whole shade decision for
 * bodies, resolved to a set of run ids.
 *
 * Per run: an AUTO span (bare shaft) follows the kind's checkbox narrowed by
 * `PdfPrefs.shadeExplicitBodiesOnly`, which bares AUTO runs and nothing else; an EXPLICIT run
 * follows its stored body's tri-state [com.android.shaftschematic.model.Body.shadeOnDrawing]
 * — `null` takes [shadedBodies], an explicit value overrides it either way (a named section
 * can shade with the kind off, or stay bare with it on). Fragments of a split body look their
 * author's choice up through [resolvedBodyBaseId], so every run of one body agrees.
 *
 * The composers' drawable bodies come from `ShaftSpec.bodyForPdf`, which keeps the RESOLVED id
 * (fragments included) and drops the source and the flags, so the decision has to be made here
 * and handed to the one body pass as ids. Suppressing per run inside that single pass, rather
 * than splitting the run list into a filled and an unfilled pass, is what keeps the
 * fill-then-outline z-order each run already has — which is why the composers build the fill
 * paint unconditionally and let this set decide: a kind switched off simply names every run.
 *
 * Without a resolve pass ([resolved] null) the drawn bodies are the stored ones, so the same
 * rule runs over [spec]'s own list, keyed by stored id.
 */
fun unshadedBodyRunIds(
    spec: ShaftSpec,
    resolved: List<ResolvedComponent>?,
    shadedBodies: Boolean,
    shadeExplicitBodiesOnly: Boolean,
): Set<String> {
    if (resolved == null) {
        return spec.bodies.filterNot { it.shadeOnDrawing ?: shadedBodies }.map { it.id }.toSet()
    }
    return resolved
        .filterIsInstance<ResolvedBody>()
        .filterNot { run ->
            if (run.source == ResolvedComponentSource.AUTO) {
                shadedBodies && !shadeExplicitBodiesOnly
            } else {
                spec.bodies.firstOrNull { it.id == resolvedBodyBaseId(run.id) }?.shadeOnDrawing
                    ?: shadedBodies
            }
        }
        .map { it.id }
        .toSet()
}

/**
 * Taper mirror of [unshadedBodyRunIds]: ids of the tapers that must draw unfilled, each
 * following its own tri-state [com.android.shaftschematic.model.Taper.shadeOnDrawing] with
 * [shadedTapers] as the default. Tapers never fragment, so a stored id IS the drawn id and
 * there is no resolved list to consult.
 */
fun unshadedTaperIds(spec: ShaftSpec, shadedTapers: Boolean): Set<String> =
    spec.tapers.filterNot { it.shadeOnDrawing ?: shadedTapers }.map { it.id }.toSet()

/**
 * Liner mirror of [unshadedBodyRunIds], same stored-id rule as [unshadedTaperIds].
 *
 * A consolidated sheet printing measured Ø values inside the profile outranks every value
 * here — the composer drops the liner fill whole on such a sheet, because a sheet-white
 * knockout halo over grey reads as a pasted box.
 */
fun unshadedLinerIds(spec: ShaftSpec, shadedLiners: Boolean): Set<String> =
    spec.liners.filterNot { it.shadeOnDrawing ?: shadedLiners }.map { it.id }.toSet()

private fun subtractBodiesAgainstNonBodies(components: List<ResolvedComponent>): List<ResolvedComponent> {
    if (components.isEmpty()) return components

    data class Span(val start: Float, val end: Float)
    val eps = 1e-3f

    val nonBodies = components.filterNot { it is ResolvedBody }
    val bodyComponents = components.filterIsInstance<ResolvedBody>()

    fun overlaps(bStart: Float, bEnd: Float, fStart: Float, fEnd: Float): Boolean =
        bStart < fEnd - eps && bEnd > fStart + eps

    val subtractedBodies = bodyComponents.flatMap { body ->
        var fragments = listOf(Span(body.startMmPhysical, body.endMmPhysical))

        nonBodies.forEach { feature ->
            val fStart = feature.startMmPhysical
            val fEnd = feature.endMmPhysical
            fragments = fragments.flatMap { frag ->
                if (!overlaps(frag.start, frag.end, fStart, fEnd)) {
                    listOf(frag)
                } else {
                    buildList {
                        if (fStart > frag.start + eps) add(Span(frag.start, fStart))
                        if (fEnd < frag.end - eps) add(Span(fEnd, frag.end))
                    }
                }
            }
        }

        // Fragments stay in ascending-start order. The first keeps the source id so existing
        // references (wear pits, runout readings, selection) still resolve to the primary
        // fragment; later ones get a deterministic "#2", "#3", … suffix so no two resolved
        // rows share an id (duplicate keys crash the carousel pager).
        fragments
            .filter { it.end - it.start > eps }
            .mapIndexed { i, span ->
                body.copy(
                    id = if (i == 0) body.id else "${body.id}$BODY_FRAGMENT_ID_SEPARATOR${i + 1}",
                    startMmPhysical = span.start,
                    endMmPhysical = span.end
                )
            }
    }

    return (nonBodies + subtractedBodies).sortedWith(
        compareBy<ResolvedComponent>({ it.startMmPhysical }, { it.typeSortKey() })
    )
}

private fun normalizeBodies(
    components: List<ResolvedComponent>,
    overrideDiaMm: Float = 0f,
    sectionOverrides: List<AutoDiaOverride> = emptyList()
): List<ResolvedComponent> {
    if (components.isEmpty()) return components

    data class BodyAccum(
        var start: Float,
        var end: Float,
        var diaMm: Float,
        var hasExplicit: Boolean,
        var explicitId: String?,
        /** Set when this run's Ø came from an [AutoDiaOverride] anchored in the opening span. */
        var sectionAuthored: Boolean = false,
    ) {
        fun toResolved(): ResolvedBody = ResolvedBody(
            id = explicitId ?: autoBodyId(start, end),
            type = if (hasExplicit) ResolvedComponentType.BODY else ResolvedComponentType.BODY_AUTO,
            source = if (hasExplicit) ResolvedComponentSource.EXPLICIT else ResolvedComponentSource.AUTO,
            startMmPhysical = start,
            endMmPhysical = end,
            diaMm = diaMm
        )
    }

    val result = mutableListOf<ResolvedComponent>()
    var current: BodyAccum? = null
    var lastMergedDia: Float? = null
    val eps = 1e-3f

    fun startAccum(comp: ResolvedBody): BodyAccum {
        val isExplicit = comp.source == ResolvedComponentSource.EXPLICIT
        val sectionAuthored = !isExplicit &&
            sectionOverrides.hasAutoSectionDia(comp.startMmPhysical, comp.endMmPhysical)
        // An auto span with a user-set Ø of its own — a section override anchored inside it,
        // or the legacy shaft-wide bare-shaft Ø — keeps that value; only a span with no
        // authored Ø inherits diameter continuity from a flanking explicit body.
        val dia = when {
            isExplicit -> comp.diaMm
            overrideDiaMm > 0f -> comp.diaMm
            sectionAuthored -> comp.diaMm
            else -> lastMergedDia ?: comp.diaMm
        }
        return BodyAccum(
            start = comp.startMmPhysical,
            end = comp.endMmPhysical,
            diaMm = dia,
            hasExplicit = isExplicit,
            explicitId = if (isExplicit) comp.id else null,
            sectionAuthored = sectionAuthored
        )
    }

    fun flush() {
        current?.let {
            result.add(it.toResolved())
            // A section-authored auto run states one section's Ø, so it seeds no continuity:
            // the next auto run falls back to the shaft-wide Ø or neighbor derivation rather
            // than inheriting a value that was only ever true of the run before it. An
            // explicit body's run seeds continuity, so the auto fill beyond it keeps
            // drawing at the body's Ø.
            lastMergedDia = if (it.sectionAuthored && !it.hasExplicit) null else it.diaMm
            current = null
        }
    }

    components.forEach { comp ->
        when (comp) {
            is ResolvedBody -> {
                if (current == null) {
                    current = startAccum(comp)
                } else if (
                    comp.source == ResolvedComponentSource.EXPLICIT || current!!.hasExplicit
                ) {
                    // An explicit body's span is AUTHORED: it never fuses with another
                    // explicit body and never absorbs neighbouring auto fill in either
                    // direction. Absorbing the adjacent gap would make a shortened explicit
                    // body span the whole run again — the typed length would have no visible
                    // effect, its selection highlight would cover the merged run, and the
                    // remainder's auto card would vanish (on-device report). Only auto
                    // spans merge with each other; an auto run that follows an explicit
                    // body still inherits its Ø via [lastMergedDia], so a same-Ø neighbour
                    // draws at the same diameter with only the component face line between.
                    flush()
                    current = startAccum(comp)
                } else if (comp.startMmPhysical <= current!!.end + eps) {
                    current!!.start = kotlin.math.min(current!!.start, comp.startMmPhysical)
                    current!!.end = kotlin.math.max(current!!.end, comp.endMmPhysical)
                } else {
                    flush()
                    current = startAccum(comp)
                }
            }
            else -> {
                flush()
                result.add(comp)
            }
        }
    }
    flush()

    return result
}
