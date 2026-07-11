// app/src/main/java/com/android/shaftschematic/model/CouplerBoltSlot.kt
package com.android.shaftschematic.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.util.UUID

/** Authoring reference for a coupler bolt slot's axial position (UI display only). */
enum class SlotAuthoredReference { AFT, FWD }

/**
 * Muff-coupler bolt slot(s): one axial **row** of radial bolt cutouts carved into the shaft
 * at a coupler location. The physical hole sits on the shaft's outer surface — half in the
 * shaft, half in the coupling sleeve — so on the side-view schematic each cutout renders as a
 * circle straddling the shaft outline.
 *
 * Units: **mm** (millimeters). Geometry is measured AFT → FWD.
 *
 * This is a **pure reference feature**:
 * - It never contributes to overall length / coverage (`coverageEndMm` ignores it).
 * - It never splits bodies and never collides with other components.
 * - It is rendered wherever the shaft is drawn, like any other component.
 *
 * @property id Stable identifier.
 * @property startFromAftMm Physical position (from AFT) of the **first** cutout's center.
 * @property holeDiaMm Diameter of each cutout.
 * @property count Number of cutouts in the row (≥ 1, user-defined per custom build).
 * @property spacingMm Axial center-to-center pitch between adjacent cutouts (used when count > 1).
 * @property through True = through-hole; false = blind (see [depthMm]).
 * @property depthMm Blind depth; ignored when [through] is true.
 * @property authoredReference AFT or FWD reference used for authoring display (defaults FWD).
 * @property showDimensionRail Opt-in per-card dimension rail (deferred; off by default).
 * @property label Optional user-defined label (not used for geometry).
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class CouplerBoltSlot(
    override val id: String = UUID.randomUUID().toString(),
    override val startFromAftMm: Float = 0f,
    val holeDiaMm: Float = 0f,
    val count: Int = 1,
    val spacingMm: Float = 0f,
    val through: Boolean = true,
    val depthMm: Float = 0f,
    val authoredReference: SlotAuthoredReference = SlotAuthoredReference.FWD,
    @JsonNames("showDimensionRail", "showRail")
    val showDimensionRail: Boolean = false,
    val label: String? = null,
) : Segment {

    /**
     * Axial footprint of the whole cutout row: from the first cutout center to the last,
     * padded by one hole radius each side. Used only for layout-window sizing and list
     * ordering — **never** for OAL/coverage (this feature is excluded from those).
     */
    override val lengthMm: Float
        get() = (count - 1).coerceAtLeast(0) * spacingMm + holeDiaMm

    /** Center position (from AFT) of cutout [i] (0-based). */
    fun centerMmAt(i: Int): Float = startFromAftMm + i.coerceAtLeast(0) * spacingMm
}

/** Basic invariants: non-negative fields, at least one cutout, all centers within the shaft. */
fun CouplerBoltSlot.isValid(overallLengthMm: Float): Boolean {
    if (startFromAftMm < 0f || holeDiaMm < 0f || spacingMm < 0f || count < 1) return false
    val lastCenter = centerMmAt(count - 1)
    return startFromAftMm - holeDiaMm * 0.5f >= -1e-3f &&
        lastCenter + holeDiaMm * 0.5f <= overallLengthMm + 1e-3f
}
