package com.android.shaftschematic.geom

import kotlin.math.max

/**
 * Where one cut's number prints on an undercut drawing (`PdfPrefs.undercutCutNumbers`).
 *
 * [xMm] is the label's horizontal CENTRE in shaft space. [baselineDiaMm] is the baseline's
 * distance from the centreline, in the draw site's own Ø→radius map. An [inside] label is
 * centred VERTICALLY on the centreline — its baseline half a glyph BELOW it, at
 * `cy + rPx(baselineDiaMm)`, clear of the Ø callout leaders leaving the floor lines; an outside
 * label's baseline is ABOVE the shaft at `cy − rPx(baselineDiaMm)`, a pad over the outer surface.
 * Both draw sites build the baseline from these two fields alone; every decision is here.
 *
 * An [id] may repeat: a parent cut split into separate visible pieces by its nested cuts carries
 * one placement per piece that fits a label, so draw sites draw every placement as given and never
 * de-duplicate by id.
 */
internal data class UndercutCutNumberPlacement(
    val id: String,
    val label: String,
    val xMm: Float,
    val baselineDiaMm: Float,
    val inside: Boolean,
)

/**
 * Places each cut's number — the ONE sheet numbering, [undercutSheetNumbers], handed in as
 * [numbers] — on the notches a draw site already built (`buildUndercutNotches`), so the PDF and
 * both canvases print the same number in the same place relative to the cut.
 *
 * - **Inside** by default, vertically on the shaft centreline (the section core runs floor to
 *   floor through it, so the centreline always has room) and horizontally centred on an UNCUT
 *   stretch of the cut's own floor. A plain cut has one stretch, its whole span, and one label.
 *   A parent's floor is split into pieces by the cuts nested inside it (a child's void and core
 *   paint over the parent's core there): every piece — the gaps between its ends and its
 *   children, and between consecutive children — wide enough for the label plus a [padMm] each
 *   side carries the parent's number, so the reader sees the pieces are ONE section (on-device
 *   request); a piece too narrow gets none. A child's number sits inside the child, never on top
 *   of its parent's.
 * - **Outside** when no stretch fits — width alone decides: ONE label centred on the cut, a pad
 *   above the highest outer
 *   surface over its top-level cut. Outside labels that would overlap are spread apart by the
 *   least total movement that clears them, keeping their AFT→FWD order.
 *
 * Horizontal quantities ([glyphWidthMm], [padMm]) are axial shaft mm; vertical ones
 * ([glyphHeightMm], [padDiaMm]) are in the draw site's Ø units — a value `d` whose `rPx(d)` is
 * that height — because a sheet's two scales differ. A notch with no entry in [numbers] (a draft
 * not yet in the record) or no length gets no label.
 */
internal fun undercutCutNumberPlacements(
    notches: List<UndercutNotch>,
    numbers: Map<String, Int>,
    glyphWidthMm: (String) -> Float,
    glyphHeightMm: Float,
    padMm: Float,
    padDiaMm: Float = padMm,
): List<UndercutCutNumberPlacement> {
    val eps = UNDERCUT_SPAN_EPS_MM
    val live = notches.filter { numbers[it.id] != null && it.endMm - it.startMm > eps }
    if (live.isEmpty()) return emptyList()
    val spanOf = live.associate { it.id to UndercutSpanMm(it.id, it.startMm, it.endMm) }

    val placed = live.flatMap { n ->
        val label = numbers.getValue(n.id).toString()
        val span = spanOf.getValue(n.id)
        val inner = live.filter { m -> m.id != n.id && undercutSpanContains(span, spanOf.getValue(m.id)) }
        val needW = glyphWidthMm(label) + 2f * padMm
        val pieces = freeStretches(n.startMm, n.endMm, inner.map { it.startMm to it.endMm })
            .filter { (a, b) -> b - a >= needW }
        if (pieces.isNotEmpty()) {
            pieces.map { (a, b) ->
                UndercutCutNumberPlacement(n.id, label, (a + b) / 2f, glyphHeightMm / 2f, inside = true)
            }
        } else {
            // The highest outer surface over the cut's top-level ancestor: a nested cut's own
            // surface points sit on its parent's floor, not on the shaft's outside.
            val root = live
                .filter { m -> m.id != n.id && undercutSpanContains(spanOf.getValue(m.id), span) }
                .maxByOrNull { it.endMm - it.startMm } ?: n
            val surfaceDia = root.profiles.flatMap { it.surface }.maxOfOrNull { it.diaMm } ?: n.floorDiaMm
            listOf(UndercutCutNumberPlacement(n.id, label, (n.startMm + n.endMm) / 2f, surfaceDia + padDiaMm, inside = false))
        }
    }

    // Spread the outside row: isotonic fit (pool adjacent violators) of the wanted centres
    // shifted by the cumulative minimum spacing — the smallest squared movement that leaves
    // every neighbour pair at least half-width + half-width + a pad apart, order kept.
    val outside = placed.withIndex().filter { !it.value.inside }.sortedBy { it.value.xMm }
    if (outside.size < 2) return placed
    val widths = outside.map { glyphWidthMm(it.value.label) }
    val offsets = FloatArray(outside.size)
    for (i in 1 until outside.size) {
        offsets[i] = offsets[i - 1] + (widths[i - 1] + widths[i]) / 2f + padMm
    }
    val fitted = isotonicFit(FloatArray(outside.size) { outside[it].value.xMm - offsets[it] })
    val result = placed.toMutableList()
    outside.forEachIndexed { k, iv -> result[iv.index] = iv.value.copy(xMm = fitted[k] + offsets[k]) }
    return result
}

/**
 * The stretches of `[startMm, endMm]` not covered by [blocked], AFT → FWD, each of positive
 * length. With nothing blocked it is the whole span.
 */
private fun freeStretches(
    startMm: Float,
    endMm: Float,
    blocked: List<Pair<Float, Float>>,
): List<Pair<Float, Float>> {
    val eps = UNDERCUT_SPAN_EPS_MM
    val out = mutableListOf<Pair<Float, Float>>()
    var cursor = startMm
    blocked.sortedBy { it.first }.forEach { (a, b) ->
        if (a - cursor > eps) out += cursor to a
        cursor = max(cursor, b)
    }
    if (endMm - cursor > eps) out += cursor to endMm
    return out
}

/** Least-squares non-decreasing fit of [values] (pool adjacent violators). */
private fun isotonicFit(values: FloatArray): FloatArray {
    val means = ArrayList<Float>()
    val counts = ArrayList<Int>()
    values.forEach { v ->
        means.add(v); counts.add(1)
        while (means.size >= 2 && means[means.size - 2] > means[means.size - 1]) {
            val n2 = counts.removeAt(counts.size - 1)
            val m2 = means.removeAt(means.size - 1)
            val n1 = counts[counts.size - 1]
            val m1 = means[means.size - 1]
            means[means.size - 1] = (m1 * n1 + m2 * n2) / (n1 + n2)
            counts[counts.size - 1] = n1 + n2
        }
    }
    val out = FloatArray(values.size)
    var i = 0
    means.forEachIndexed { b, m -> repeat(counts[b]) { out[i++] = m } }
    return out
}
