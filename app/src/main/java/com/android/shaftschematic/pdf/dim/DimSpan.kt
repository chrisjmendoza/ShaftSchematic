package com.android.shaftschematic.pdf.dim

/**
 * A single dimension line from x1→x2 with labels.
 * x values are in measurement-space millimeters.
 */
data class DimSpan(
    val x1Mm: Double,
    val x2Mm: Double,
    val labelTop: String,
    val labelBottom: String? = null
)

data class RailSpan(val rail: Int, val span: DimSpan)

/**
 * Packs dimension spans into stacked rails so that spans on the same rail don't overlap.
 */
class RailPlanner {
    private val rails = mutableListOf<MutableList<DimSpan>>()

    fun assign(span: DimSpan): RailSpan {
        val a = minOf(span.x1Mm, span.x2Mm)
        val b = maxOf(span.x1Mm, span.x2Mm)
        for ((r, line) in rails.withIndex()) {
            if (line.none { overlaps(a, b, it) }) {
                line.add(span)
                return RailSpan(r, span)
            }
        }
        rails.add(mutableListOf(span))
        return RailSpan(rails.lastIndex, span)
    }

    private fun overlaps(a1: Double, a2: Double, other: DimSpan): Boolean {
        val b1 = minOf(other.x1Mm, other.x2Mm)
        val b2 = maxOf(other.x1Mm, other.x2Mm)
        return !(a2 <= b1 || a1 >= b2) // endpoints may touch
    }
}
