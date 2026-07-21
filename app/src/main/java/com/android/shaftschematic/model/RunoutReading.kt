// app/src/main/java/com/android/shaftschematic/model/RunoutReading.kt
package com.android.shaftschematic.model

import kotlinx.serialization.Serializable

/**
 * A recorded runout (TIR) reading for one measurement station (bubble) on the runout sheet.
 *
 * **Pure reference feature** — same contract class as [WearSpot]/[CouplerBoltSlot] (see
 * `CLAUDE.md`): it never affects `coverageEndMm`/OAL, body resolution, collision/overlap
 * validation, or the Free-to-End badge. It lives outside [ShaftSpec] entirely, in
 * [RunoutReadings], stored beside `RunoutConfig`/`WearRecord` in the document envelope so
 * geometry resolution never has to know about it.
 *
 * Both data fields are optional: a bubble may carry a value, a high-spot marker, both, or
 * neither. An entry with neither is meaningless and is never stored (see
 * [RunoutReadings.withReading]).
 *
 * ## Identity & orphan policy
 * A reading is keyed by [componentId] + [stationIndex] — the ordinal of the station among its
 * component's stations, as produced by `collectRunoutStations`. Station positions shift when the
 * user changes a component's bubble count, so a reading whose [stationIndex] no longer maps to a
 * live station is an orphan: it is simply not drawn (the render-time lookup misses) and can be
 * pruned on the next edit. This mirrors the [WearSpot] orphan-drop policy, but resolved at the
 * render layer rather than at decode (station identity depends on resolved components + count
 * overrides, which the codec does not have).
 *
 * @property componentId Resolved-component id the station belongs to.
 * @property stationIndex 0-based ordinal of the station within its component (AFT→FWD order).
 * @property valueMm TIR reading in canonical mm, or null if not entered. Displayed/entered in the
 *   app's active unit at the UI edge (never converted in the model).
 * @property highSpotHalfHours Clock position of the high-spot marker in half-hour ticks
 *   `[0, 23]`, `0` = 12 o'clock, increasing clockwise (angle = `n × 15°`), or null if not placed.
 */
@Serializable
data class RunoutReading(
    val componentId: String = "",
    val stationIndex: Int = 0,
    val valueMm: Float? = null,
    val highSpotHalfHours: Int? = null,
) {
    /** True when neither a value nor a high-spot marker is present — nothing worth storing. */
    val isEmpty: Boolean get() = valueMm == null && highSpotHalfHours == null
}

/**
 * Per-document set of runout readings. Lives beside `RunoutConfig` in the document envelope
 * (`ShaftDocCodec.ShaftDocV1`) — NOT inside [ShaftSpec] — so runout data never touches geometry
 * resolution, collision, or coverage math. See [RunoutReading] for the reference-only contract.
 */
@Serializable
data class RunoutReadings(
    val readings: List<RunoutReading> = emptyList(),
) {
    /** The reading for a station, or null if none recorded. */
    fun find(componentId: String, stationIndex: Int): RunoutReading? =
        readings.firstOrNull { it.componentId == componentId && it.stationIndex == stationIndex }

    /**
     * Return a copy with [reading] inserted or replacing the existing entry for its
     * `(componentId, stationIndex)`. An [RunoutReading.isEmpty] reading is removed instead of
     * stored, so clearing a bubble leaves no empty cruft behind.
     */
    fun withReading(reading: RunoutReading): RunoutReadings {
        val rest = readings.filterNot {
            it.componentId == reading.componentId && it.stationIndex == reading.stationIndex
        }
        return RunoutReadings(if (reading.isEmpty) rest else rest + reading)
    }

    /** Return a copy with any reading for `(componentId, stationIndex)` removed. */
    fun without(componentId: String, stationIndex: Int): RunoutReadings =
        RunoutReadings(
            readings.filterNot {
                it.componentId == componentId && it.stationIndex == stationIndex
            }
        )
}
