package com.android.shaftschematic.data

/**
 * File: DraftRing.kt
 * Layer: data (pure, Android-free)
 *
 * Pure ring/eviction logic for the autosave draft history plus the dirty-gate predicate.
 * Kept out of [AutosaveManager] so it is unit-testable without a `Context`/DataStore.
 *
 * A draft ring is a newest-first list of [DraftEntry] capped at [DEFAULT_DRAFT_RING_MAX].
 * See docs/Autosave_Incident_2026-07-25.md.
 */

/** Default maximum number of distinct unsaved-session drafts retained. */
const val DEFAULT_DRAFT_RING_MAX = 3

/**
 * Upsert [entry] into [list] (assumed newest-first) and return the new newest-first list.
 *
 * Rules:
 *  - If an entry with the same [DraftEntry.draftId] exists it is *replaced* by [entry] and
 *    moved to the front (a session upserting its own draft never grows the ring).
 *  - Otherwise [entry] is inserted at the front.
 *  - If the result exceeds [max], the strictly **oldest** entries by
 *    [DraftEntry.updatedAtEpochMs] are evicted (timestamp order, not list position) until the
 *    ring is back within [max].
 */
fun upsertDraft(
    list: List<AutosaveManager.DraftEntry>,
    entry: AutosaveManager.DraftEntry,
    max: Int = DEFAULT_DRAFT_RING_MAX,
): List<AutosaveManager.DraftEntry> {
    val without = list.filterNot { it.draftId == entry.draftId }
    val combined = listOf(entry) + without
    if (combined.size <= max) return combined

    val toEvict = combined.size - max
    // Evict the oldest-by-timestamp entries regardless of their list position.
    val evictIds = combined
        .sortedBy { it.updatedAtEpochMs }
        .take(toEvict)
        .map { it.draftId }
        .toSet()
    return combined.filterNot { it.draftId in evictIds }
}

/**
 * The autosave dirty gate: a draft should be written only when the live session [current]
 * differs from the last saved/loaded [saved] snapshot. A freshly-opened, untouched document
 * (whose [saved] was seeded on load) can never overwrite anything. `saved == null` means "no
 * baseline captured yet", which counts as dirty.
 */
fun shouldWriteDraft(
    current: AutosaveManager.SessionSnapshot,
    saved: AutosaveManager.SessionSnapshot?,
): Boolean = current != saved

/**
 * True when the snapshot is a factory-default session: empty spec and blank project
 * metadata. Deliberately ignores unit, unit-lock, OAL mode, and runout config — those can
 * differ from field defaults without any user-authored content (the async settings restore
 * flips the unit preference on every launch). A default session holds nothing worth a
 * draft, so the autosave observer must never persist one: with the prior gate
 * (dirty comparison alone), launching with an empty draft ring on an inches-preference
 * device seeded a mm baseline, the settings restore flipped the unit, and 1.5 s later a
 * phantom blank "Untitled draft" appeared on the StartScreen.
 */
fun AutosaveManager.SessionSnapshot.isDefaultSession(): Boolean {
    val specEmpty =
        shaftSpec.overallLengthMm == 0f &&
        shaftSpec.bodies.isEmpty() &&
        shaftSpec.tapers.isEmpty() &&
        shaftSpec.threads.isEmpty() &&
        shaftSpec.liners.isEmpty() &&
        shaftSpec.couplerBoltSlots.isEmpty()

    return specEmpty &&
        shaftPosition == com.android.shaftschematic.model.ShaftPosition.OTHER &&
        customer.isBlank() &&
        vessel.isBlank() &&
        jobNumber.isBlank() &&
        notes.isBlank()
}
