package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.geom.computeMeasurementDatums
import com.android.shaftschematic.model.AuthoredReference
import com.android.shaftschematic.model.AutoBodyKey
import com.android.shaftschematic.model.AutoBodyOverride
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.config.AddDefaultsConfig
import kotlin.math.max

/**
 * Resolved component model used for layout/rendering and UI visibility.
 * Canonical units: millimeters (mm).
 */
enum class ResolvedComponentType { BODY, BODY_AUTO, TAPER, THREAD, LINER }

enum class ResolvedComponentSource { EXPLICIT, AUTO, DRAFT }

sealed class ResolvedComponent {
    abstract val id: String
    abstract val authoredSourceId: String
    abstract val type: ResolvedComponentType
    abstract val source: ResolvedComponentSource
    abstract val startMmPhysical: Float
    abstract val endMmPhysical: Float
    val lengthMm: Float get() = endMmPhysical - startMmPhysical
}

data class ResolvedBody(
    override val id: String,
    override val authoredSourceId: String,
    override val type: ResolvedComponentType,
    override val source: ResolvedComponentSource,
    override val startMmPhysical: Float,
    override val endMmPhysical: Float,
    val diaMm: Float,
    val autoBodyKey: AutoBodyKey? = null,
) : ResolvedComponent()

data class ResolvedTaper(
    override val id: String,
    override val authoredSourceId: String,
    override val type: ResolvedComponentType = ResolvedComponentType.TAPER,
    override val source: ResolvedComponentSource = ResolvedComponentSource.EXPLICIT,
    override val startMmPhysical: Float,
    override val endMmPhysical: Float,
    val startDiaMm: Float,
    val endDiaMm: Float,
) : ResolvedComponent()

data class ResolvedThread(
    override val id: String,
    override val authoredSourceId: String,
    override val type: ResolvedComponentType = ResolvedComponentType.THREAD,
    override val source: ResolvedComponentSource = ResolvedComponentSource.EXPLICIT,
    override val startMmPhysical: Float,
    override val endMmPhysical: Float,
    val majorDiaMm: Float,
    val pitchMm: Float,
    val excludeFromOal: Boolean = false,
) : ResolvedComponent()

data class ResolvedLiner(
    override val id: String,
    override val authoredSourceId: String,
    override val type: ResolvedComponentType = ResolvedComponentType.LINER,
    override val source: ResolvedComponentSource = ResolvedComponentSource.EXPLICIT,
    override val startMmPhysical: Float,
    override val endMmPhysical: Float,
    val odMm: Float,
) : ResolvedComponent()

fun resolveComponents(
    spec: ShaftSpec,
    overallIsManual: Boolean,
    draft: DraftComponent? = null,
): List<ResolvedComponent> {
    val explicit = resolveExplicitComponents(spec)
    val draftResolved = draft?.let { resolveDraftComponent(it) }?.let(::listOf).orEmpty()
    val explicitAndDraft = explicit + draftResolved
    val autoBodies = deriveAutoBodies(
        overallLengthMm = if (overallIsManual) spec.overallLengthMm else 0f,
        explicitComponents = explicitAndDraft,
        overrides = spec.autoBodyOverrides
    )
    val merged = (explicitAndDraft + autoBodies).sortedWith(
        compareBy<ResolvedComponent>({ it.startMmPhysical }, { it.typeSortKey() })
    )
    val subtracted = subtractBodiesAgainstNonBodies(merged)
    return normalizeBodies(subtracted)
}

fun resolveExplicitComponents(spec: ShaftSpec): List<ResolvedComponent> = buildList {
    val datums = computeMeasurementDatums(spec)
    val mfd = datums.measurementForwardMm.toFloat()
    val eps = 1e-3f

    data class FwdItem(
        val key: ComponentKey,
        val lengthMm: Float,
        val authoredStartFromFwdMm: Float,
    )

    val fwdItems = buildList {
        spec.threads.forEach { th ->
            if (th.authoredReference == AuthoredReference.FWD && th.authoredStartFromFwdMm <= eps) {
                add(
                    FwdItem(
                        key = ComponentKey(th.id, ComponentKind.THREAD),
                        lengthMm = th.lengthMm,
                        authoredStartFromFwdMm = th.authoredStartFromFwdMm
                    )
                )
            }
        }
        spec.tapers.forEach { tp ->
            if (tp.authoredReference == AuthoredReference.FWD && tp.authoredStartFromFwdMm <= eps) {
                add(
                    FwdItem(
                        key = ComponentKey(tp.id, ComponentKind.TAPER),
                        lengthMm = tp.lengthMm,
                        authoredStartFromFwdMm = tp.authoredStartFromFwdMm
                    )
                )
            }
        }
        spec.liners.forEach { ln ->
            if (ln.authoredReference == LinerAuthoredReference.FWD && ln.authoredStartFromFwdMm <= eps) {
                add(
                    FwdItem(
                        key = ComponentKey(ln.id, ComponentKind.LINER),
                        lengthMm = ln.lengthMm,
                        authoredStartFromFwdMm = ln.authoredStartFromFwdMm
                    )
                )
            }
        }
    }

    val fwdOverrides = mutableMapOf<ComponentKey, Pair<Float, Float>>()
    if (fwdItems.isNotEmpty()) {
        var end = mfd
        fwdItems
            .sortedBy { it.authoredStartFromFwdMm }
            .forEach { item ->
                val start = end - item.lengthMm
                fwdOverrides[item.key] = start to end
                end = start
            }
    }

    fun overrideFor(key: ComponentKey): Pair<Float, Float>? = fwdOverrides[key]

    spec.bodies.forEach { b ->
        add(
            ResolvedBody(
                id = b.id,
                authoredSourceId = b.id,
                type = ResolvedComponentType.BODY,
                source = ResolvedComponentSource.EXPLICIT,
                startMmPhysical = b.startFromAftMm,
                endMmPhysical = b.startFromAftMm + b.lengthMm,
                diaMm = b.diaMm
            )
        )
    }
    spec.tapers.forEach { t ->
        val key = ComponentKey(t.id, ComponentKind.TAPER)
        val (start, end) = overrideFor(key) ?: run {
            val startMm = if (t.authoredReference == AuthoredReference.FWD) {
                mfd - t.authoredStartFromFwdMm - t.lengthMm
            } else {
                t.startFromAftMm
            }
            startMm to (startMm + t.lengthMm)
        }
        add(
            ResolvedTaper(
                id = t.id,
                authoredSourceId = t.id,
                startMmPhysical = start,
                endMmPhysical = end,
                startDiaMm = t.startDiaMm,
                endDiaMm = t.endDiaMm
            )
        )
    }
    spec.threads.forEach { th ->
        val key = ComponentKey(th.id, ComponentKind.THREAD)
        val (start, end) = overrideFor(key) ?: run {
            val startMm = if (th.authoredReference == AuthoredReference.FWD) {
                mfd - th.authoredStartFromFwdMm - th.lengthMm
            } else {
                th.startFromAftMm
            }
            startMm to (startMm + th.lengthMm)
        }
        add(
            ResolvedThread(
                id = th.id,
                authoredSourceId = th.id,
                startMmPhysical = start,
                endMmPhysical = end,
                majorDiaMm = th.majorDiaMm,
                pitchMm = th.pitchMm,
                excludeFromOal = th.excludeFromOAL
            )
        )
    }
    spec.liners.forEach { ln ->
        val key = ComponentKey(ln.id, ComponentKind.LINER)
        val (start, end) = overrideFor(key) ?: run {
            val startMm = if (ln.authoredReference == LinerAuthoredReference.FWD) {
                mfd - ln.authoredStartFromFwdMm - ln.lengthMm
            } else {
                ln.startFromAftMm
            }
            startMm to (startMm + ln.lengthMm)
        }
        add(
            ResolvedLiner(
                id = ln.id,
                authoredSourceId = ln.id,
                startMmPhysical = start,
                endMmPhysical = end,
                odMm = ln.odMm
            )
        )
    }
}

private fun resolveDraftComponent(draft: DraftComponent): ResolvedComponent = when (draft) {
    is DraftComponent.Body -> ResolvedBody(
        id = draft.id,
        authoredSourceId = draft.id,
        type = ResolvedComponentType.BODY,
        source = ResolvedComponentSource.DRAFT,
        // Draft components are already in physical space (no authored reference transforms).
        startMmPhysical = draft.startMmPhysical,
        endMmPhysical = draft.startMmPhysical + draft.lengthMm,
        diaMm = draft.diaMm,
        autoBodyKey = null
    )
    is DraftComponent.Taper -> ResolvedTaper(
        id = draft.id,
        authoredSourceId = draft.id,
        type = ResolvedComponentType.TAPER,
        source = ResolvedComponentSource.DRAFT,
        startMmPhysical = draft.startMmPhysical,
        endMmPhysical = draft.startMmPhysical + draft.lengthMm,
        startDiaMm = draft.startDiaMm,
        endDiaMm = draft.endDiaMm
    )
    is DraftComponent.Thread -> ResolvedThread(
        id = draft.id,
        authoredSourceId = draft.id,
        type = ResolvedComponentType.THREAD,
        source = ResolvedComponentSource.DRAFT,
        startMmPhysical = draft.startMmPhysical,
        endMmPhysical = draft.startMmPhysical + draft.lengthMm,
        majorDiaMm = draft.majorDiaMm,
        pitchMm = draft.pitchMm,
        excludeFromOal = draft.excludeFromOal
    )
    is DraftComponent.Liner -> ResolvedLiner(
        id = draft.id,
        authoredSourceId = draft.id,
        type = ResolvedComponentType.LINER,
        source = ResolvedComponentSource.DRAFT,
        startMmPhysical = draft.startMmPhysical,
        endMmPhysical = draft.startMmPhysical + draft.lengthMm,
        odMm = draft.odMm
    )
}

/**
 * Derive auto body segments from explicit component spans.
 */
fun deriveAutoBodies(
    overallLengthMm: Float,
    explicitComponents: List<ResolvedComponent>,
    overrides: Map<String, AutoBodyOverride> = emptyMap(),
): List<ResolvedComponent> {
    val explicit = explicitComponents
        .filter { it.source == ResolvedComponentSource.EXPLICIT || it.source == ResolvedComponentSource.DRAFT }
        .sortedBy { it.startMmPhysical }

    data class Span(val start: Float, val end: Float)

    if (explicit.isEmpty()) {
        if (overallLengthMm <= 0f) return emptyList()
        val key = AutoBodyKey(leftId = null, rightId = null)
        val override = overrides[key.stableId()]
        return listOf(
            ResolvedBody(
                id = autoBodyId(key),
                authoredSourceId = autoBodyStableId(key),
                type = ResolvedComponentType.BODY_AUTO,
                source = ResolvedComponentSource.AUTO,
                startMmPhysical = 0f,
                endMmPhysical = overallLengthMm,
                diaMm = override?.diaMm ?: resolveAutoBodyDia(0f, explicit),
                autoBodyKey = key
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
        val minStart = explicit.minOf { it.startMmPhysical }
        val maxEnd = explicit.maxOf { it.endMmPhysical }
        if (minStart > 0f) spans.add(Span(0f, minStart))
        if (overallLengthMm > maxEnd) spans.add(Span(maxEnd, overallLengthMm))
    }

    return spans.mapNotNull { span ->
        val length = span.end - span.start
        if (length <= 0f) return@mapNotNull null
        val leftNeighbor = explicit.lastOrNull { it.endMmPhysical <= span.start }
        val rightNeighbor = explicit.firstOrNull { it.startMmPhysical >= span.end }
        val key = AutoBodyKey(
            leftId = leftNeighbor?.authoredSourceId,
            rightId = rightNeighbor?.authoredSourceId
        )
        val override = overrides[key.stableId()]
        val dia = override?.diaMm ?: resolveAutoBodyDia(span.start, explicit)
        val id = autoBodyId(key)
        ResolvedBody(
            id = id,
            authoredSourceId = autoBodyStableId(key),
            type = ResolvedComponentType.BODY_AUTO,
            source = ResolvedComponentSource.AUTO,
            startMmPhysical = span.start,
            endMmPhysical = span.end,
            diaMm = dia,
            autoBodyKey = key
        )
    }
}

fun ResolvedComponent.maxDiaMm(): Float = when (this) {
    is ResolvedBody -> diaMm
    is ResolvedTaper -> max(startDiaMm, endDiaMm)
    is ResolvedThread -> majorDiaMm
    is ResolvedLiner -> odMm
}

private fun ResolvedComponent.aftDiaMm(): Float = when (this) {
    is ResolvedBody -> diaMm
    is ResolvedTaper -> startDiaMm
    is ResolvedThread -> majorDiaMm
    is ResolvedLiner -> odMm
}

private fun ResolvedComponent.fwdDiaMm(): Float = when (this) {
    is ResolvedBody -> diaMm
    is ResolvedTaper -> endDiaMm
    is ResolvedThread -> majorDiaMm
    is ResolvedLiner -> odMm
}

private fun resolveAutoBodyDia(startMm: Float, explicit: List<ResolvedComponent>): Float {
    val upstreamBody = explicit
        .filterIsInstance<ResolvedBody>()
        .filter { it.source != ResolvedComponentSource.AUTO && it.endMmPhysical <= startMm }
        .maxByOrNull { it.endMmPhysical }
    if (upstreamBody != null) return upstreamBody.diaMm

    val upstream = explicit
        .filter { it.endMmPhysical <= startMm }
        .maxByOrNull { it.endMmPhysical }
    if (upstream != null) return upstream.fwdDiaMm()

    val downstreamBody = explicit
        .filterIsInstance<ResolvedBody>()
        .filter { it.source != ResolvedComponentSource.AUTO }
        .minByOrNull { it.startMmPhysical }
    if (downstreamBody != null) return downstreamBody.diaMm

    val downstream = explicit.minByOrNull { it.startMmPhysical }
    if (downstream != null) return downstream.aftDiaMm()

    return AddDefaultsConfig.BODY_DIA_MM
}

private fun autoBodyStableId(key: AutoBodyKey): String = "auto_body_${key.stableId()}"

private fun autoBodyId(key: AutoBodyKey): String = autoBodyStableId(key)

private fun ResolvedComponent.typeSortKey(): Int = when (type) {
    ResolvedComponentType.BODY -> 0
    ResolvedComponentType.BODY_AUTO -> 1
    ResolvedComponentType.TAPER -> 2
    ResolvedComponentType.THREAD -> 3
    ResolvedComponentType.LINER -> 4
}

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

        val kept = fragments.filter { it.end - it.start > eps }
        val split = kept.size > 1 || kept.any { it.start != body.startMmPhysical || it.end != body.endMmPhysical }
        kept.mapIndexed { index, span ->
            val segId = if (split) "${body.id}::seg:$index" else body.id
            body.copy(
                id = segId,
                startMmPhysical = span.start,
                endMmPhysical = span.end
            )
        }
    }

    return (nonBodies + subtractedBodies).sortedWith(
        compareBy<ResolvedComponent>({ it.startMmPhysical }, { it.typeSortKey() })
    )
}

private fun normalizeBodies(components: List<ResolvedComponent>): List<ResolvedComponent> {
    if (components.isEmpty()) return components

    data class BodyAccum(
        var start: Float,
        var end: Float,
        var diaMm: Float,
        var hasExplicit: Boolean,
        var explicitId: String?,
        var authoredSourceId: String,
        var autoBodyKey: AutoBodyKey?,
    ) {
        fun toResolved(): ResolvedBody = ResolvedBody(
            id = explicitId ?: autoBodyId(autoBodyKey ?: AutoBodyKey()),
            authoredSourceId = authoredSourceId,
            type = if (hasExplicit) ResolvedComponentType.BODY else ResolvedComponentType.BODY_AUTO,
            source = if (hasExplicit) ResolvedComponentSource.EXPLICIT else ResolvedComponentSource.AUTO,
            startMmPhysical = start,
            endMmPhysical = end,
            diaMm = diaMm,
            autoBodyKey = autoBodyKey
        )
    }

    val result = mutableListOf<ResolvedComponent>()
    var current: BodyAccum? = null
    var lastMergedDia: Float? = null
    val eps = 1e-3f

    fun startAccum(comp: ResolvedBody): BodyAccum {
        val isExplicit = comp.source == ResolvedComponentSource.EXPLICIT
        val dia = if (isExplicit) comp.diaMm else (lastMergedDia ?: comp.diaMm)
        return BodyAccum(
            start = comp.startMmPhysical,
            end = comp.endMmPhysical,
            diaMm = dia,
            hasExplicit = isExplicit,
            explicitId = if (isExplicit) comp.id else null
            ,
            authoredSourceId = comp.authoredSourceId,
            autoBodyKey = comp.autoBodyKey
        )
    }

    fun flush() {
        current?.let {
            result.add(it.toResolved())
            lastMergedDia = it.diaMm
            current = null
        }
    }

    components.forEach { comp ->
        when (comp) {
            is ResolvedBody -> {
                if (current == null) {
                    current = startAccum(comp)
                } else if (comp.startMmPhysical <= current!!.end + eps) {
                    val compIsExplicit = comp.source == ResolvedComponentSource.EXPLICIT
                    if (current!!.hasExplicit || compIsExplicit) {
                        // Keep explicit and auto bodies separate; do not merge across sources.
                        flush()
                        current = startAccum(comp)
                    } else {
                        // Both auto bodies; merge to avoid fragmentation.
                        current!!.start = kotlin.math.min(current!!.start, comp.startMmPhysical)
                        current!!.end = kotlin.math.max(current!!.end, comp.endMmPhysical)
                    }
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
