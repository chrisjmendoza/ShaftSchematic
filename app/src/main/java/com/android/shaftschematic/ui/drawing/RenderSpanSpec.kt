package com.android.shaftschematic.ui.drawing

import com.android.shaftschematic.model.ShaftSpec

/**
 * The spec the on-screen canvases lay out from: this spec, or — when no overall length has
 * been authored yet — a copy stretched to the last occupied end so a part-built shaft still
 * draws instead of collapsing onto a zero-width span.
 *
 * A **fallback only**: a spec that carries a positive [ShaftSpec.overallLengthMm] is returned
 * unchanged, so an authored length is never rewritten by the renderer (golden rule). An
 * oversized shaft keeps its authored length and simply draws past it.
 *
 * The fold deliberately includes **excluded threads**, unlike
 * [com.android.shaftschematic.model.coverageEndMm] and
 * [com.android.shaftschematic.model.lastOccupiedEndMm]: an excluded thread is drawn outside the
 * shaft envelope, and the drawn span is what the canvas has to hold. Dropping it would leave a
 * FWD end thread hanging off the edge of an OAL-less preview.
 *
 * ONE implementation, shared by the editor preview (`ShaftDrawing`) and the Open-screen
 * thumbnail (`ShaftThumbnail`) so the two cannot disagree on what a not-yet-measured shaft
 * spans.
 */
fun ShaftSpec.renderSpanSpec(): ShaftSpec {
    if (overallLengthMm > 0f) return this
    val lastEnd = buildList {
        bodies.maxOfOrNull { it.startFromAftMm + it.lengthMm }?.let(::add)
        tapers.maxOfOrNull { it.startFromAftMm + it.lengthMm }?.let(::add)
        liners.maxOfOrNull { it.startFromAftMm + it.lengthMm }?.let(::add)
        threads.maxOfOrNull { it.startFromAftMm + it.lengthMm }?.let(::add)
    }.maxOrNull() ?: 0f
    return if (lastEnd > 0f) copy(overallLengthMm = lastEnd) else this
}
