package com.android.shaftschematic.model

import kotlinx.serialization.Serializable

/**
 * The tapped holes in ONE end face of the shaft — the draw-plate bolt pattern and the puller's
 * center hole (on-device request: the foreman hand-wrote "4 holes 1 1/4 NC on 4 1/2 B.C." beside
 * the FWD end of a printed schematic, and the app had nowhere to put it).
 *
 * A **reference feature**, the coupler-bolt-slot posture: it never affects OAL, coverage, body
 * resolution or collision, and nothing draws it on the shaft — it prints only as text lines in that
 * end's footer column ([com.android.shaftschematic.pdf.endHoleFooterLines]). It belongs to the
 * shaft END, not to whatever component sits there, so it is entered on the start card and survives
 * any edit of the end taper or body.
 *
 * Every field is typed and stored verbatim (golden rule). The thread sizes are free text printed
 * exactly as typed (`1 1/4" NC`, `1 1/4-7 UNC`, `M30`) — a thread callout converted to another
 * unit stops meaning anything, the metric-designation rule. The bolt circle is canonical mm and
 * prints in the document unit (dual when the document is dual). There is no depth: the shop
 * records a hole's size, count and bolt circle, never how deep it is tapped (on-device ruling).
 * Zero / blank means "not given" and prints nothing; a record with nothing set prints no line.
 */
@Serializable
data class ShaftEndHoles(
    /** Number of draw-plate holes; 0 = not given. */
    val drawPlateCount: Int = 0,
    /** Draw-plate hole thread size, verbatim. */
    val drawPlateSize: String = "",
    /** Draw-plate bolt circle Ø (mm); 0 = not given. */
    val drawPlateBoltCircleMm: Float = 0f,
    /** Puller (center) hole thread size, verbatim. */
    val pullerSize: String = "",
) {
    /** True when any draw-plate value is given — the line prints, and the start card section opens. */
    val hasDrawPlate: Boolean
        get() = drawPlateCount > 0 || drawPlateSize.isNotBlank() || drawPlateBoltCircleMm > 0f

    /** True when any puller-hole value is given. */
    val hasPuller: Boolean
        get() = pullerSize.isNotBlank()

    /** The draw-plate values cleared, the puller hole kept. */
    fun withoutDrawPlate(): ShaftEndHoles =
        copy(drawPlateCount = 0, drawPlateSize = "", drawPlateBoltCircleMm = 0f)

    /** The puller-hole values cleared, the draw plate kept. */
    fun withoutPuller(): ShaftEndHoles = copy(pullerSize = "")
}

/** Which end face of the shaft an [ShaftEndHoles] record belongs to. */
enum class ShaftEnd { AFT, FWD }

/** The end-hole record of [end]. */
fun ShaftSpec.endHoles(end: ShaftEnd): ShaftEndHoles = when (end) {
    ShaftEnd.AFT -> aftEndHoles
    ShaftEnd.FWD -> fwdEndHoles
}

/** A copy with [end]'s end-hole record replaced; nothing else changes. */
fun ShaftSpec.withEndHoles(end: ShaftEnd, holes: ShaftEndHoles): ShaftSpec = when (end) {
    ShaftEnd.AFT -> copy(aftEndHoles = holes)
    ShaftEnd.FWD -> copy(fwdEndHoles = holes)
}
