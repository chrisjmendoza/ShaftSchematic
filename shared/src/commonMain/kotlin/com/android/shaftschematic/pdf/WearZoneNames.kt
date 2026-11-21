package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.BelowShaftLabelLayout
import com.android.shaftschematic.geom.WearAreaSpan
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.model.isNamedZone

/**
 * Where the wear sheet prints a wear zone's name (`WearSpot.label` — on-device request: wear on
 * packing or a grounding strap, where there is no liner). Two places, both on the WEAR sheet only:
 *  - after its host's name in the detail strip title — "Bare shaft (Packing)" — through
 *    [withWearZoneNames], so the strip says what the zone is;
 *  - under its band on the main profile, in the liner-names row — [placeWearZoneNames], through
 *    the schematic's own below-shaft collision engine ([BelowShaftLabelLayout]) so a zone name
 *    slides clear of a liner name rather than printing over it.
 * An unnamed area prints exactly as before, and a sheet with no named area is byte-identical.
 */

/**
 * [titles] with each host's zone names appended in parentheses — "Bare shaft (Packing)", or
 * "AFT Liner (Seal, Grounding strap)" for several — in record order, each distinct name once. A
 * host with no named area keeps its title unchanged.
 */
fun withWearZoneNames(titles: Map<String, String>, spots: List<WearSpot>): Map<String, String> {
    val namesByHost = spots.filter { it.isNamedZone }
        .groupBy { it.linerId }
        .mapValues { (_, s) -> s.map { it.label.trim() }.distinct() }
    if (namesByHost.isEmpty()) return titles
    return titles.mapValues { (id, title) ->
        val names = namesByHost[id] ?: return@mapValues title
        "$title (${names.joinToString(", ")})"
    }
}

/** One zone name to place under the main profile: its text, measured width and drawn band x. */
data class WearZoneNameRequest(val text: String, val width: Float, val bandLeftX: Float, val bandRightX: Float)

/** Where a zone name landed: its left edge; [fitted] false when it overlaps what it could not clear. */
data class WearZoneNamePlacement(val text: String, val left: Float, val fitted: Boolean)

/**
 * The zone names' left edges in the ONE names row: each centred under its band, free to slide
 * along the band (widened to hold the centred name) to clear [occupiedX] — the liner names already
 * in the row — and each other, held inside `[loX, hiX]` (clear of the "← AFT" / "FWD →" marks).
 * A name that fits nowhere keeps its centred spot (the engine's least-bad rule) — never dropped.
 */
fun placeWearZoneNames(
    requests: List<WearZoneNameRequest>,
    occupiedX: List<ClosedFloatingPointRange<Float>>,
    loX: Float,
    hiX: Float,
): List<WearZoneNamePlacement> {
    if (requests.isEmpty()) return emptyList()
    val row = BelowShaftLabelLayout.RowBand(top = 0f, bottom = 1f)
    val engineRequests = requests.map { r ->
        val centredLeft = wearProfileNameCenterX((r.bandLeftX + r.bandRightX) * 0.5f, r.width, loX, hiX) - r.width * 0.5f
        BelowShaftLabelLayout.Request(
            width = r.width,
            preferredLeft = centredLeft,
            minLeft = minOf(r.bandLeftX, centredLeft).coerceAtLeast(loX),
            maxRight = maxOf(r.bandRightX, centredLeft + r.width).coerceAtMost(hiX),
        )
    }
    val obstacles = occupiedX.map { BelowShaftLabelLayout.Box(it.start, it.endInclusive, row.top, row.bottom) }
    val placed = BelowShaftLabelLayout.place(engineRequests, obstacles, listOf(row))
    return requests.indices.map { i -> WearZoneNamePlacement(requests[i].text, placed[i].left, placed[i].fitted) }
}

/** The named [spans] as requests, in record order ([spots] holds each span's area). */
fun wearZoneNameRequests(
    spans: List<WearAreaSpan>,
    spots: List<WearSpot>,
    xAt: (Float) -> Float,
    measure: (String) -> Float,
): List<WearZoneNameRequest> {
    val byId = spots.associateBy { it.id }
    return spans.mapNotNull { span ->
        val spot = byId[span.spotId] ?: return@mapNotNull null
        if (!spot.isNamedZone) return@mapNotNull null
        val text = spot.label.trim()
        WearZoneNameRequest(text, measure(text), xAt(span.startMm), xAt(span.endMm))
    }
}
