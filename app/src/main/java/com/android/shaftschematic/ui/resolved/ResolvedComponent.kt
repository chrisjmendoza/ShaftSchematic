package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.config.AddDefaultsConfig
import java.util.Locale
import kotlin.math.max

/**
 * Resolved component model used for layout/rendering and UI visibility.
 * Canonical units: millimeters (mm).
 */
enum class ResolvedComponentType { BODY, BODY_AUTO, TAPER, THREAD, LINER }

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

data class ResolvedLiner(
    override val id: String,
    override val type: ResolvedComponentType = ResolvedComponentType.LINER,
    override val source: ResolvedComponentSource = ResolvedComponentSource.EXPLICIT,
    override val startMmPhysical: Float,
    override val endMmPhysical: Float,
    val odMm: Float,
) : ResolvedComponent()

fun resolveComponents(spec: ShaftSpec, overallIsManual: Boolean): List<ResolvedComponent> {
    val explicit = resolveExplicitComponents(spec)
    val autoBodies = deriveAutoBodies(
        overallLengthMm = if (overallIsManual) spec.overallLengthMm else 0f,
        explicitComponents = explicit
    )
    val merged = (explicit + autoBodies).sortedWith(
        compareBy<ResolvedComponent>({ it.startMmPhysical }, { it.typeSortKey() })
    )
    val subtracted = subtractBodiesAgainstNonBodies(merged)
    return normalizeBodies(subtracted)
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
                odMm = ln.odMm
            )
        )
    }
}

/**
 * Derive auto body segments from explicit component spans.
 */
fun deriveAutoBodies(
    overallLengthMm: Float,
    explicitComponents: List<ResolvedComponent>
): List<ResolvedComponent> {
    val explicit = explicitComponents
        .filter { it.source == ResolvedComponentSource.EXPLICIT }
        .sortedBy { it.startMmPhysical }

    data class Span(val start: Float, val end: Float)

    if (explicit.isEmpty()) {
        if (overallLengthMm <= 0f) return emptyList()
        return listOf(
            ResolvedBody(
                id = autoBodyId(0f, overallLengthMm),
                type = ResolvedComponentType.BODY_AUTO,
                source = ResolvedComponentSource.AUTO,
                startMmPhysical = 0f,
                endMmPhysical = overallLengthMm,
                diaMm = resolveAutoBodyDia(0f, explicit)
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

        val dia = resolveAutoBodyDia(span.start, explicit)
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

        fragments
            .filter { it.end - it.start > eps }
            .map { span ->
                body.copy(
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
        val dia = if (isExplicit) comp.diaMm else (lastMergedDia ?: comp.diaMm)
        return BodyAccum(
            start = comp.startMmPhysical,
            end = comp.endMmPhysical,
            diaMm = dia,
            hasExplicit = isExplicit,
            explicitId = if (isExplicit) comp.id else null
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
                    current!!.start = kotlin.math.min(current!!.start, comp.startMmPhysical)
                    current!!.end = kotlin.math.max(current!!.end, comp.endMmPhysical)
                    if (comp.source == ResolvedComponentSource.EXPLICIT && !current!!.hasExplicit) {
                        current!!.hasExplicit = true
                        current!!.explicitId = comp.id
                        current!!.diaMm = comp.diaMm
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
