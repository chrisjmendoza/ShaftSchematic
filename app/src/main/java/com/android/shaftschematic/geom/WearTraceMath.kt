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
 * strip (PDF) — build their polyline from the SAME [buildWearTrace] output, smoothed by the SAME
 * [smoothWearTrace] call per band, each mapping it through its own scale, so the two render
 * identically.
 */

/**
 * Cap on the drawn (exaggerated) wear depth, as a fraction of the drawn radius — the record's
 * deepest reading draws at this depth; shallower readings scale proportionally. Same
 * display-exaggeration posture as the undercut notch: real wear is a hairline at drawing scale.
 *
 * The high end of the user-set range AND the shipped default ("25% should be our high end" —
 * on-device verdict): [WEAR_TRACE_MIN_DEPTH_FRAC]..[WEAR_TRACE_MAX_DEPTH_FRAC], picked per job
 * (`WearRecord.traceDepthFrac`) over a Settings → Drawing default (`PdfPrefs.wearTraceDepthFrac`)
 * and resolved by [effectiveWearTraceDepthFrac].
 */
const val WEAR_TRACE_MAX_DEPTH_FRAC = 0.25f

/**
 * Low end of the settable exaggeration range — the shallowest a trace may be *exaggerated* to.
 * It is not a floor on the drawn depth: `buildWearTrace` still never draws shallower than true
 * scale, so a monstrous wear keeps its true proportion at every setting.
 */
const val WEAR_TRACE_MIN_DEPTH_FRAC = 0.05f

/**
 * The exaggeration cap a document actually draws with — the ONE resolution of the per-job
 * override against the global default. [recordFrac] is `WearRecord.traceDepthFrac`: `null` means
 * "follow the Settings default", so a job that never touched its slider tracks later changes to
 * [globalFrac] while a touched job stays pinned.
 *
 * Coercing here is correct rather than a golden-rule violation: this is a display dial, not a
 * measurement (the `RunoutConfig.heightScale` posture). Every consumer — both draw sites and the
 * slider UIs — goes through this; no site re-derives it.
 */
fun effectiveWearTraceDepthFrac(recordFrac: Float?, globalFrac: Float): Float =
    (recordFrac ?: globalFrac).coerceIn(WEAR_TRACE_MIN_DEPTH_FRAC, WEAR_TRACE_MAX_DEPTH_FRAC)

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
 * Intermediate samples [smoothWearTrace] emits between each pair of input vertices.
 *
 * The smoother hands back a denser vertex run rather than curve control points on purpose: each
 * draw site maps a vertex through its OWN x function (strip scale, canvas scale), and only
 * sampled points survive that mapping — a Bézier control point would have to be mapped too, and
 * `geom` may not reach a `Path` API in any case. 16 samples per segment reads as a continuous
 * curve at both sheet and screen scale while keeping the vertex count trivial.
 */
const val WEAR_TRACE_SMOOTH_SAMPLES = 16

/**
 * Axial span below which two consecutive vertices count as sharing an x — a reading landing on a
 * band edge, say. Such a pair is a vertical jump in the trace: it is kept verbatim rather than
 * interpolated (there is no slope to fit, and the secant would divide by ~0).
 */
private const val WEAR_TRACE_SMOOTH_MIN_DX_MM = 1e-4f

/**
 * Smooth a trace vertex run into the flowing curve real wear has (a straight-segment dip reads as
 * a machined chamfer, not a worn surface — on-device request), as a denser run of the SAME
 * [WearTraceVertex] type: both draw sites keep walking it with their existing straight `lineTo`
 * loops, so everything built from the run — surface edges, band fill, the tint clip — curves with
 * it and no draw site learns about curves.
 *
 * **Monotone cubic Hermite (Fritsch–Carlson)** in `(localMm, depthFrac)` space, and that scheme
 * specifically: it is the one that cannot overshoot. Every sample between two stations stays
 * inside the depth range of that segment's own endpoints, so a deep station beside a shallow one
 * can never bulge the curve outside the metal or dig it deeper than measured. A Catmull-Rom (or
 * any unlimited cubic) spline would do exactly that.
 *
 * The output passes **exactly** through every input vertex — the originals are emitted, samples
 * only go between them — which is what preserves the load-bearing depth invariant: at each
 * station the drawn depth stays exactly `max(exaggerated, true-scale)` from [buildWearTrace],
 * never shallower than true and never deeper than the exaggeration says.
 *
 * Flat runs stay exactly flat (a zero secant forces zero tangents at both of its ends), so the
 * zero-depth stretch [sequenceWearTraces] leaves between two bands is still a dead-straight
 * surface. A run of fewer than three vertices, or a non-positive [samplesPerSegment], is returned
 * unchanged.
 */
fun smoothWearTrace(
    verts: List<WearTraceVertex>,
    samplesPerSegment: Int = WEAR_TRACE_SMOOTH_SAMPLES,
): List<WearTraceVertex> {
    if (verts.size < 3 || samplesPerSegment <= 0) return verts
    // Split at vertical jumps: each maximal strictly-advancing sub-run is fitted on its own, and
    // the pair straddling the jump is emitted verbatim (both vertices survive, so the jump reads
    // exactly as authored).
    val out = ArrayList<WearTraceVertex>(verts.size + (verts.size - 1) * samplesPerSegment)
    var runStart = 0
    for (i in 0 until verts.size - 1) {
        if (verts[i + 1].localMm - verts[i].localMm <= WEAR_TRACE_SMOOTH_MIN_DX_MM) {
            out += smoothMonotoneRun(verts.subList(runStart, i + 1), samplesPerSegment)
            runStart = i + 1
        }
    }
    out += smoothMonotoneRun(verts.subList(runStart, verts.size), samplesPerSegment)
    return out
}

/**
 * One strictly-advancing vertex run, Fritsch–Carlson fitted and sampled. Fewer than three
 * vertices is already a straight line the interpolation would only reproduce, so it passes
 * through untouched.
 */
private fun smoothMonotoneRun(
    run: List<WearTraceVertex>,
    samplesPerSegment: Int,
): List<WearTraceVertex> {
    val n = run.size
    if (n < 3) return run

    val h = FloatArray(n - 1) { run[it + 1].localMm - run[it].localMm }
    val secant = FloatArray(n - 1) { (run[it + 1].depthFrac - run[it].depthFrac) / h[it] }

    // Tangents: the one-sided secant at each end, the average of the two neighbouring secants
    // inside, and zero at a local extremum (secants of opposite sign) so the curve turns over
    // through the station instead of past it.
    val m = FloatArray(n)
    m[0] = secant[0]
    m[n - 1] = secant[n - 2]
    for (i in 1 until n - 1) {
        m[i] = if (secant[i - 1] * secant[i] <= 0f) 0f else (secant[i - 1] + secant[i]) * 0.5f
    }
    // Fritsch–Carlson limiter: a tangent pointing against its segment's secant is flattened, and
    // a pair outside the radius-3 monotonicity circle is scaled back onto it. Both only ever
    // shrink a tangent, so a segment already fitted stays fitted.
    for (i in 0 until n - 1) {
        if (secant[i] == 0f) {
            m[i] = 0f
            m[i + 1] = 0f
            continue
        }
        var a = m[i] / secant[i]
        var b = m[i + 1] / secant[i]
        if (a < 0f) { m[i] = 0f; a = 0f }
        if (b < 0f) { m[i + 1] = 0f; b = 0f }
        val sq = a * a + b * b
        if (sq > 9f) {
            val tau = 3f / kotlin.math.sqrt(sq)
            m[i] = tau * a * secant[i]
            m[i + 1] = tau * b * secant[i]
        }
    }

    val out = ArrayList<WearTraceVertex>(n + (n - 1) * samplesPerSegment)
    out += run[0]
    for (i in 0 until n - 1) {
        val x0 = run[i].localMm
        val y0 = run[i].depthFrac
        val y1 = run[i + 1].depthFrac
        val lo = minOf(y0, y1)
        val hi = maxOf(y0, y1)
        for (k in 1..samplesPerSegment) {
            val t = k.toFloat() / (samplesPerSegment + 1).toFloat()
            val y = if (secant[i] == 0f) y0 else {
                val t2 = t * t
                val t3 = t2 * t
                val h00 = 2f * t3 - 3f * t2 + 1f
                val h10 = t3 - 2f * t2 + t
                val h01 = -2f * t3 + 3f * t2
                val h11 = t3 - t2
                // Clamped to the segment's own endpoints: Fritsch–Carlson already guarantees the
                // cubic stays inside them, so this only sheds float dust — never a real bulge.
                (h00 * y0 + h10 * h[i] * m[i] + h01 * y1 + h11 * h[i] * m[i + 1]).coerceIn(lo, hi)
            }
            out += WearTraceVertex(x0 + h[i] * t, y)
        }
        out += run[i + 1]
    }
    return out
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
