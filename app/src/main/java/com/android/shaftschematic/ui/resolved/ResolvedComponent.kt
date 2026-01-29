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
    return (explicit + autoBodies).sortedWith(
        compareBy<ResolvedComponent>({ it.startMmPhysical }, { it.typeSortKey() })
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
