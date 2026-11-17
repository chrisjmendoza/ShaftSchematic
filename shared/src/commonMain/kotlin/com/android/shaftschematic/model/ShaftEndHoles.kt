package com.android.shaftschematic.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The tapped holes in ONE end face of the shaft — the draw-plate bolt pattern (on-device request: the foreman hand-wrote "4 holes 1 1/4 NC on 4 1/2 B.C." beside
 * the FWD end of a printed schematic, and the app had nowhere to put it).
 *
 * A **reference feature**, the coupler-bolt-slot posture: it never affects OAL, coverage, body
 * resolution or collision, and nothing draws it on the shaft — it prints only as text lines in that
 * end's footer column ([com.android.shaftschematic.pdf.endHoleFooterLines]). It belongs to the
 * shaft END, not to whatever component sits there, so it is entered on the start card and survives
 * any edit of the end taper or body.
 *
 * Puller holes are NOT here: they are in the propeller, not the shaft (on-device ruling).
 *
 * Every field is typed and stored verbatim (golden rule). The thread size is free text printed
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
    /**
     * The draw-plate holes' thread series, chosen on the toggle at the end of the thread-size field
     * (on-device request: the size is typed bare, `1 1/4`, and the sheet reads `1 1/4" NC`).
     * Default [ThreadSeries.NC]. See [com.android.shaftschematic.pdf.drawPlateThreadText].
     */
    val drawPlateSeries: ThreadSeries = ThreadSeries.NC,
) {
    /** True when any draw-plate value is given — the line prints, and the start card section opens. */
    val hasDrawPlate: Boolean
        get() = drawPlateCount > 0 || drawPlateSize.isNotBlank() || drawPlateBoltCircleMm > 0f

    /** The draw-plate values cleared. */
    fun withoutDrawPlate(): ShaftEndHoles =
        copy(drawPlateCount = 0, drawPlateSize = "", drawPlateBoltCircleMm = 0f)
}

/**
 * Unified inch thread series of a tapped hole: coarse (NC) or fine (NF). Serialized by NAME through
 * [ThreadSeriesSerializer], which decodes an unknown name to [NC].
 */
@Serializable(with = ThreadSeriesSerializer::class)
enum class ThreadSeries {
    NC, NF;

    companion object {
        /** Tolerant decode for a persisted name; an unknown or blank value is [NC]. */
        fun fromName(raw: String?): ThreadSeries =
            if (raw.isNullOrBlank()) NC else runCatching { valueOf(raw) }.getOrDefault(NC)
    }
}

/** Name-string serializer for [ThreadSeries] with [ThreadSeries.fromName]'s fallback. */
object ThreadSeriesSerializer : KSerializer<ThreadSeries> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.android.shaftschematic.model.ThreadSeries", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ThreadSeries) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): ThreadSeries = ThreadSeries.fromName(decoder.decodeString())
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
