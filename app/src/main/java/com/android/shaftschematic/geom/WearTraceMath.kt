// file: app/src/main/java/com/android/shaftschematic/geom/WearTraceMath.kt
package com.android.shaftschematic.geom

/**
 * Worn-profile trace pure math — the drawn liner surface follows the measured diameters
 * inside a wear band instead of staying a perfect cylinder (a shaft measured almost half an
 * inch down still drew full-size, on-device report).
 *
 * Draw-only, the wear record's reference-feature posture: nothing here touches the model,
 * resolve, OAL, collision, or the codec. No Compose/Android imports, so it is directly
 * unit-testable on the JVM and carries no `pdf → ui` dependency — same posture as
 * `WearPitMath.kt` / `WearDiaMath.kt`.
 *
 * Both draw sites — `ComponentWearDetailOverlay` (canvas) and `WearPdfComposer`'s liner detail
 * strip (PDF) — build their polyline from the SAME [buildWearTrace] output, each mapping it
 * through its own scale, so the two render identically.
 */

/**
 * Cap on the drawn (exaggerated) wear depth, as a fraction of the drawn radius — the record's
 * deepest reading draws at this depth; shallower readings scale proportionally. Same
 * display-exaggeration posture as the undercut notch: real wear is a hairline at drawing scale.
 */
const val WEAR_TRACE_MAX_DEPTH_FRAC = 0.25f

/**
 * One vertex of a wear trace: component-local axial mm + drawn depth as a fraction of the drawn
 * radius (0 = surface).
 */
data class WearTraceVertex(val localMm: Float, val depthFrac: Float)

/** A reading participating in a trace: component-local axial mm + measured Ø mm. */
data class WearTraceReading(val localMm: Float, val diaMm: Float)

/**
 * Radial wear depth (mm) a measured diameter implies against its component's nominal — 0 when
 * the reading is at or above nominal (a measurement above nominal is a reading, not material
 * added: it simply draws at the surface).
 */
fun wearDepthMm(nominalOdMm: Float, measuredDiaMm: Float): Float {
    if (nominalOdMm <= 0f || measuredDiaMm <= 0f) return 0f
    return ((nominalOdMm - measuredDiaMm) * 0.5f).coerceAtLeast(0f)
}

/**
 * Deepest wear depth (mm) across a record's valued liner readings — the exaggeration
 * normalization baseline, computed ONCE per sheet so every band on the drawing is scaled
 * against the same worst reading. Input pairs are `(nominalOdMm, measuredDiaMm)`; a pair with
 * `measuredDiaMm <= 0` (placed-but-empty station) is ignored.
 */
fun deepestWearDepthMm(readings: List<Pair<Float, Float>>): Float {
    var deepest = 0f
    readings.forEach { (nominalOdMm, measuredDiaMm) ->
        if (measuredDiaMm > 0f) {
            val d = wearDepthMm(nominalOdMm, measuredDiaMm)
            if (d > deepest) deepest = d
        }
    }
    return deepest
}

/**
 * The traced surface polyline for ONE wear band: `(bandStart, 0)` → each valued reading inside
 * the band, sorted by [WearTraceReading.localMm], at its drawn depth fraction →
 * `(bandEnd, 0)`. Returns an empty list when no reading inside the band has positive wear
 * depth — the band then draws exactly as before (no trace).
 *
 * The depth fraction per station is `max(exaggerated, true-scale)`, where
 * ```
 * exaggerated = depthMm / deepestDepthMm * maxDepthFrac   (0 when deepestDepthMm <= 0)
 * true-scale  = depthMm / (nominalOdMm / 2)
 * ```
 * — the trace is normalized to the record's deepest reading ([deepestDepthMm], from
 * [deepestWearDepthMm]) so a hairline cut still reads, but it may NEVER draw shallower than
 * true scale: a monstrous wear deeper than [maxDepthFrac] keeps its true proportion.
 *
 * Readings with `diaMm <= 0` (placed-but-empty) or outside
 * `[bandStartMm, bandStartMm + bandLengthMm]` are excluded; a reading at or above nominal
 * contributes a surface (0-depth) vertex. A degenerate band (length ≤ 0) traces nothing.
 */
fun buildWearTrace(
    bandStartMm: Float,
    bandLengthMm: Float,
    readings: List<WearTraceReading>,
    nominalOdMm: Float,
    deepestDepthMm: Float,
    maxDepthFrac: Float = WEAR_TRACE_MAX_DEPTH_FRAC,
): List<WearTraceVertex> {
    if (bandLengthMm <= 0f || nominalOdMm <= 0f) return emptyList()
    val bandEndMm = bandStartMm + bandLengthMm
    val radiusMm = nominalOdMm * 0.5f
    val inside = readings
        .filter { it.diaMm > 0f && it.localMm >= bandStartMm && it.localMm <= bandEndMm }
        .sortedBy { it.localMm }
    if (inside.isEmpty()) return emptyList()

    val stations = inside.map { r ->
        val depthMm = wearDepthMm(nominalOdMm, r.diaMm)
        val exaggerated = if (deepestDepthMm > 0f) depthMm / deepestDepthMm * maxDepthFrac else 0f
        val trueScale = depthMm / radiusMm
        WearTraceVertex(
            localMm = r.localMm.coerceIn(bandStartMm, bandEndMm),
            depthFrac = maxOf(exaggerated, trueScale).coerceIn(0f, 1f),
        )
    }
    // Every reading in the band sat at or above nominal: nothing was worn away, so the band
    // keeps the plain cylinder rather than drawing a flat "trace" over its own surface.
    if (stations.none { it.depthFrac > 0f }) return emptyList()

    return buildList {
        add(WearTraceVertex(bandStartMm, 0f))
        addAll(stations)
        add(WearTraceVertex(bandEndMm, 0f))
    }
}

/**
 * Flatten per-band traces into ONE left-to-right vertex run, the form both draw sites walk to
 * build their surface polyline (each trace opens and closes at depth 0, so the edge returns to
 * the surface between bands on its own).
 *
 * A band whose trace would run backwards over the one before it — overlapping wear spots, which
 * the model permits — is skipped rather than folded in: a polyline that reverses direction
 * would draw the surface as a zigzag through itself.
 */
fun sequenceWearTraces(traces: List<List<WearTraceVertex>>): List<WearTraceVertex> {
    val out = mutableListOf<WearTraceVertex>()
    var cursorMm = Float.NEGATIVE_INFINITY
    traces.forEach { trace ->
        if (trace.isEmpty()) return@forEach
        if (trace.first().localMm < cursorMm) return@forEach
        out += trace
        cursorMm = trace.last().localMm
    }
    return out
}
