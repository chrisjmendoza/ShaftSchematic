// app/src/main/java/com/android/shaftschematic/model/RunoutStationPlacement.kt
package com.android.shaftschematic.model

import kotlinx.serialization.Serializable

/**
 * A user-placed axial position for one runout measurement station (bubble).
 *
 * Station positions are normally **derived** — `collectRunoutStations` spreads a component's
 * count across its drawn runs. Dragging a bubble on the runout preview authors a position
 * instead, and an authored value is sacred: no re-derivation may move it (`CLAUDE.md`, golden
 * rule). A placement is the stored form of that drag.
 *
 * **Pure reference feature** — same contract class as [RunoutReading]/[WearSpot]: it never
 * affects `coverageEndMm`/OAL, body resolution, or collision/overlap validation.
 * It lives outside [ShaftSpec], in [RunoutStationPlacements], beside
 * `RunoutConfig`/[RunoutReadings] in the document envelope.
 *
 * ## Storage space
 * [axialMm] is **component-local**, measured from the AFT edge of the component's aft-most
 * drawn run — the [WearPit.axialMm] convention. Not px and not drawn-x: the live canvas maps
 * mm linearly while the PDF maps them through the compressed hand-sheet profile, so a
 * position stored in either output space would land somewhere else on the printed sheet.
 * Local rather than shaft-space so a station rides its component when the shaft around it
 * grows or the component moves.
 *
 * A component split into several runs (a body cut by liners) measures local distance in
 * shaft space **across** the gaps, so the value stays a single scalar. A position that a
 * later edit strands inside a gap is pulled onto the nearest run at the render layer
 * (`resolveStationShaftMm`) — never rewritten in storage.
 *
 * ## Identity & orphan policy
 * Keyed by [componentId] + [stationIndex], exactly like the [RunoutReading] it sits under.
 * A placement whose station no longer exists (the count dropped, the component was deleted)
 * is simply not read — the render-layer orphan rule, never a decode-time prune, because
 * station identity needs resolved components and count overrides the codec does not have.
 *
 * @property componentId Resolved-component id (BASE id for bodies — every fragment of one
 *   stored body shares a single station run).
 * @property stationIndex 0-based ordinal of the station within its component (AFT→FWD).
 * @property axialMm Distance from the component's AFT edge, canonical mm.
 */
@Serializable
data class RunoutStationPlacement(
    val componentId: String = "",
    val stationIndex: Int = 0,
    val axialMm: Float = 0f,
)

/**
 * Per-document set of authored station positions. Lives beside `RunoutConfig` in the document
 * envelope (`ShaftDocCodec.ShaftDocV1`) — NOT inside [ShaftSpec]. See [RunoutStationPlacement]
 * for the reference-only contract.
 *
 * **Partial sets are the norm.** A drag pins exactly the station it moved ([withPosition]);
 * untouched siblings stay derived, so they keep following geometry edits and — for bodies —
 * the sheet's drawn-even placement over the compressed profile. Derived positions never depend
 * on pinned ones, so pinning one station moves nothing else; where a pin and a re-derived
 * sibling would swap order on some output map, the render layer clamps the DERIVED one
 * (`collectRunoutStations`' order repair), never the pin. Count edits (+/−) on a component
 * with any pin are the one action that stores the **whole** current set ([withComponent]) —
 * inserting or removing a station renumbers its neighbours, and freezing them at that moment
 * is what keeps every bubble planted through the renumber.
 */
@Serializable
data class RunoutStationPlacements(
    val placements: List<RunoutStationPlacement> = emptyList(),
) {
    /** True when this component's stations are authored (dragged) rather than derived. */
    fun isAuthored(componentId: String): Boolean =
        placements.any { it.componentId == componentId }

    /**
     * Authored positions for one component keyed by station index — the form the render path
     * overlays onto the derived list. Missing indices keep their derived position; that is the
     * ordinary state after a drag, not a degenerate one.
     */
    fun positionsFor(componentId: String): Map<Int, Float> =
        placements.filter { it.componentId == componentId }
            .associate { it.stationIndex to it.axialMm }

    /** The authored position for one station, or null when that station is derived. */
    fun position(componentId: String, stationIndex: Int): Float? =
        placements.firstOrNull {
            it.componentId == componentId && it.stationIndex == stationIndex
        }?.axialMm

    /**
     * Pin (or re-pin) a single station at [axialMm], leaving every other station — pinned or
     * derived — untouched. The storage form of one drag.
     */
    fun withPosition(componentId: String, stationIndex: Int, axialMm: Float): RunoutStationPlacements {
        val rest = placements.filterNot {
            it.componentId == componentId && it.stationIndex == stationIndex
        }
        return RunoutStationPlacements(
            rest + RunoutStationPlacement(componentId, stationIndex, axialMm)
        )
    }

    /**
     * Un-pin a single station, returning it to derived placement. A component whose last pin
     * goes is fully derived again ([isAuthored] false) — what "Undo move" relies on to undo a
     * first drag all the way back to automatic placement.
     */
    fun withoutPosition(componentId: String, stationIndex: Int): RunoutStationPlacements =
        RunoutStationPlacements(
            placements.filterNot {
                it.componentId == componentId && it.stationIndex == stationIndex
            }
        )

    /**
     * Authored positions for one component in stored station-index order. Empty when the
     * component is fully derived. NOTE: after a drag this is typically a PARTIAL list — the
     * count-edit math works on the full merged set (`currentLocalStationPositions`), not this.
     */
    fun orderedFor(componentId: String): List<Float> =
        placements.filter { it.componentId == componentId }
            .sortedBy { it.stationIndex }
            .map { it.axialMm }

    /**
     * Replace this component's authored positions wholesale, renumbering them 0..n-1 in list
     * order so stored indices are always canonical and contiguous. An empty [positionsMm]
     * removes the component's entries entirely, returning it to derived placement.
     */
    fun withComponent(componentId: String, positionsMm: List<Float>): RunoutStationPlacements {
        val rest = placements.filterNot { it.componentId == componentId }
        val added = positionsMm.mapIndexed { i, mm ->
            RunoutStationPlacement(componentId = componentId, stationIndex = i, axialMm = mm)
        }
        return RunoutStationPlacements(rest + added)
    }

    /** Return this component to derived placement, dropping every authored position it has. */
    fun withoutComponent(componentId: String): RunoutStationPlacements =
        RunoutStationPlacements(placements.filterNot { it.componentId == componentId })
}
