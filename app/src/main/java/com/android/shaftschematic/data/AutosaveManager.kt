package com.android.shaftschematic.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.util.AppLog
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The draft ring's own store, separate from `settingsDataStore` so a preferences problem and a
 * draft problem cannot take each other down.
 *
 * The corruption handler matters more here than anywhere else in the app: this file is rewritten
 * every 1.5 s of editing, so it is the one most likely to be mid-write when a tablet loses power,
 * and [loadDrafts] runs from the ViewModel's `init` — an unhandled `CorruptionException` there
 * escapes `viewModelScope` with no handler above it and crashes the app on every launch. Losing
 * the drafts is the price; the saved documents are in `filesDir/shafts` and are untouched.
 */
private val Context.dataStore by preferencesDataStore(
    name = "autosave_datastore",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * AutosaveManager — draft history (v2).
 *
 * Drafts live in a ring of up to [DEFAULT_DRAFT_RING_MAX] [DraftEntry] records under
 * [DRAFTS_KEY]. Each editing session owns a stable `draftId`, so opening/creating a document
 * upserts only *its* entry and can never clobber another document's draft. Legacy
 * single-slot drafts (`autosave_last_session`) migrate into the ring on first [loadDrafts].
 * See docs/archive/Autosave_Incident_2026-07-25.md.
 */
object AutosaveManager {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Legacy single-slot key. Retained only so [loadDrafts] can migrate then delete it.
    private val AUTOSAVE_KEY = stringPreferencesKey("autosave_last_session")
    // New key: a JSON list of DraftEntry, newest-first.
    private val DRAFTS_KEY = stringPreferencesKey("autosave_drafts")

    /** draftId used when wrapping a migrated legacy single-slot snapshot. */
    const val LEGACY_DRAFT_ID = "legacy-migrated"

    @Serializable
    data class SessionSnapshot(
        val shaftSpec: ShaftSpec,
        val unitSystem: UnitSystem,
        val shaftPosition: ShaftPosition,
        val customer: String,
        val vessel: String,
        val jobNumber: String,
        val notes: String,
        // Absent in older drafts; defaults keep them decodable.
        val runoutConfig: RunoutConfig = RunoutConfig(),
        val unitLocked: Boolean = false,
        // Added (liner wear areas Phase 1): absent in older drafts; default
        // empty record keeps them decodable.
        val wearRecord: WearRecord = WearRecord(),
        // Added (runout bubble editor): per-station TIR value + high-spot marker.
        // Absent in older drafts; default empty set keeps them decodable.
        val runoutReadings: RunoutReadings = RunoutReadings(),
        // Added (undercut drawing Phase 1): recorded undercut sections. Absent in older
        // drafts; default empty record keeps them decodable.
        val undercutRecord: UndercutRecord = UndercutRecord(),
        // Added (draggable runout bubbles): authored station positions. Absent in older
        // drafts; default empty set derives every station as before.
        val runoutStationPlacements: RunoutStationPlacements = RunoutStationPlacements(),
        // Added (mixed units + dual display): per-component display-unit overrides and the
        // sheet-wide dual flag. Absent in older drafts; defaults reproduce single-unit output.
        val unitOverrides: Map<String, UnitSystem> = emptyMap(),
        val dualUnits: Boolean = false,
        // Added (Item project field): optional shaft designation. Absent in older drafts;
        // blank prints nothing anywhere.
        val item: String = "",
    )

    /**
     * One unsaved editing session's autosaved state, keyed by [draftId] (a per-session
     * identity minted on new/open). [documentName] is the file name when the session was
     * opened from a saved doc (null for a brand-new drawing). [updatedAtEpochMs] drives ring
     * eviction (oldest-first). The [snapshot] is the exact session state.
     */
    @Serializable
    data class DraftEntry(
        val draftId: String,
        val documentName: String? = null,
        val updatedAtEpochMs: Long,
        val snapshot: SessionSnapshot,
    )

    private fun decodeDrafts(raw: String?): List<DraftEntry> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<DraftEntry>>(raw) }.getOrElse { emptyList() }

    /**
     * Read the draft ring (newest-first). Decode failures yield an empty list rather than
     * throwing. On the first read after upgrading from the single-slot format, any decodable
     * legacy snapshot is wrapped as a [DraftEntry] ([LEGACY_DRAFT_ID]), merged into the ring,
     * persisted, and the legacy key removed.
     */
    suspend fun loadDrafts(context: Context): List<DraftEntry> = guarded("load", emptyList()) {
        val prefs = context.dataStore.data.first()
        var drafts = decodeDrafts(prefs[DRAFTS_KEY])

        val legacyRaw = prefs[AUTOSAVE_KEY]
        if (!legacyRaw.isNullOrBlank()) {
            val legacySnap = runCatching { json.decodeFromString<SessionSnapshot>(legacyRaw) }.getOrNull()
            if (legacySnap != null) {
                if (drafts.none { it.draftId == LEGACY_DRAFT_ID }) {
                    drafts = upsertDraft(
                        drafts,
                        DraftEntry(
                            draftId = LEGACY_DRAFT_ID,
                            documentName = null,
                            updatedAtEpochMs = System.currentTimeMillis(),
                            snapshot = legacySnap,
                        ),
                    )
                }
                val persisted = drafts
                context.dataStore.edit { p ->
                    p[DRAFTS_KEY] = json.encodeToString(persisted)
                    p.remove(AUTOSAVE_KEY)
                }
            }
        }
        return drafts
    }

    /** Read-modify-write upsert of [entry] into the ring (newest-first, capped). */
    suspend fun saveDraft(context: Context, entry: DraftEntry) = guarded("save", Unit) {
        context.dataStore.edit { prefs ->
            val next = upsertDraft(decodeDrafts(prefs[DRAFTS_KEY]), entry)
            prefs[DRAFTS_KEY] = json.encodeToString(next)
        }
        Unit
    }

    /** Remove exactly the entry with [draftId]. No-op if absent. */
    suspend fun removeDraft(context: Context, draftId: String) = guarded("remove", Unit) {
        context.dataStore.edit { prefs ->
            val next = decodeDrafts(prefs[DRAFTS_KEY]).filterNot { it.draftId == draftId }
            prefs[DRAFTS_KEY] = json.encodeToString(next)
        }
        Unit
    }

    /** Wipe the whole ring (and any lingering legacy slot). */
    suspend fun clearAllDrafts(context: Context) = guarded("clear", Unit) {
        context.dataStore.edit { prefs ->
            prefs.remove(DRAFTS_KEY)
            prefs.remove(AUTOSAVE_KEY)
        }
        Unit
    }

    /**
     * Every store access goes through here: a draft is a safety net, and a safety net that
     * crashes the app is worse than no safety net at all — [AppLog]'s posture, for the same
     * reason. Callers run from `viewModelScope` with no handler above them, so a throw escaping
     * this object reaches the process's crash handler; the ring going empty is recoverable and
     * that is not.
     *
     * `CancellationException` is rethrown, never swallowed: the autosave observer is a
     * `collectLatest` that cancels the in-flight write on every newer snapshot, and eating that
     * would break the structured concurrency the debounce depends on.
     */
    private inline fun <T> guarded(what: String, fallback: T, block: () -> T): T =
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            AppLog.e("Autosave", "draft $what failed", t)
            fallback
        }
}
