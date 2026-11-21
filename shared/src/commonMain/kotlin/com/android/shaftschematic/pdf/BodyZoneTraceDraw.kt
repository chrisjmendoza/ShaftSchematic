package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.WearTraceVertex
import com.android.shaftschematic.gfx.Canvas
import com.android.shaftschematic.gfx.Paint
import com.android.shaftschematic.gfx.Path
import com.android.shaftschematic.gfx.strokeWidth

/**
 * The ONE draw of a body's worn-profile trace inside its named wear zones (`wearBodyZoneTraces`,
 * `geom/WearTraceMath.kt`), behind every wear draw site that traces — the wear sheet's main
 * profile and detail strips, the Wear tab's shaft canvas and the wear detail canvas.
 *
 * The body has already been drawn plain (fill and straight outline). Over the span the trace
 * covers, this clears the metal measured away to [paper] — between the nominal surface (and the
 * outline stroke on it) and the traced edge, top and bottom — and strokes the traced edges with
 * [outline]. Each zone's trace opens and closes on the surface, so outside the zones the body
 * keeps the edge it was drawn with. [xAtLocal] maps body-local mm to the site's x; [radius] is
 * the body's drawn radius about [cy].
 *
 * [visibleX], when given, are the x stretches the body is drawn over (a broken run's two stubs):
 * the trace draws inside them only, since the S-break gap between them is bare paper.
 */
fun drawBodyZoneTrace(
    c: Canvas,
    verts: List<WearTraceVertex>,
    xAtLocal: (Float) -> Float,
    cy: Float,
    radius: Float,
    outline: Paint,
    paper: Paint,
    visibleX: List<ClosedFloatingPointRange<Float>>? = null,
) {
    if (verts.size < 2 || radius <= 0f) return
    val top = cy - radius
    val bot = cy + radius
    // Clear the full outline stroke above (below) the nominal surface, so no sliver of the
    // straight edge shows inside the dip.
    val clearance = outline.strokeWidth

    fun draw() {
        c.drawPath(bodyZoneClearPath(verts, xAtLocal, top, 1f, radius, clearance), paper)
        c.drawPath(bodyZoneClearPath(verts, xAtLocal, bot, -1f, radius, clearance), paper)
        c.drawPath(bodyZoneEdgePath(verts, xAtLocal, top, 1f, radius), outline)
        c.drawPath(bodyZoneEdgePath(verts, xAtLocal, bot, -1f, radius), outline)
    }

    if (visibleX == null) {
        draw()
        return
    }
    val reach = radius + clearance * 2f
    visibleX.forEach { range ->
        val saved = c.save()
        c.clipRect(range.start, top - reach, range.endInclusive, bot + reach)
        draw()
        c.restoreToCount(saved)
    }
}

/** A traced edge: [edgeY] moved [dir] × depth × radius at every vertex (dir 1 = down, the top edge). */
internal fun bodyZoneEdgePath(
    verts: List<WearTraceVertex>,
    xAtLocal: (Float) -> Float,
    edgeY: Float,
    dir: Float,
    radius: Float,
): Path = Path().apply {
    verts.forEachIndexed { i, v ->
        val x = xAtLocal(v.localMm)
        val y = edgeY + dir * v.depthFrac * radius
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
}

/**
 * The region the wear removed from one edge: from the trace's first x to its last, the band
 * between [clearance] OUTSIDE the nominal surface and the traced edge.
 */
internal fun bodyZoneClearPath(
    verts: List<WearTraceVertex>,
    xAtLocal: (Float) -> Float,
    edgeY: Float,
    dir: Float,
    radius: Float,
    clearance: Float,
): Path = Path().apply {
    val outside = edgeY - dir * clearance
    moveTo(xAtLocal(verts.first().localMm), outside)
    lineTo(xAtLocal(verts.last().localMm), outside)
    for (i in verts.indices.reversed()) {
        val v = verts[i]
        lineTo(xAtLocal(v.localMm), edgeY + dir * v.depthFrac * radius)
    }
    close()
}
