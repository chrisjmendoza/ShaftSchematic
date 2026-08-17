package com.android.shaftschematic.doc

import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.normalized
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.util.UnitSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ShaftDocCodec
 *
 * Single source of truth for encoding/decoding shaft documents.
 *
 * Contract:
 * - File contents remain JSON.
 * - Current format is a versioned envelope that includes UI metadata + mm-based [ShaftSpec].
 * - Legacy format (spec-only JSON) remains readable.
 * - All new fields must carry a default value so older files deserialize cleanly.
 *   `ignoreUnknownKeys = true` ensures forward-compat when newer files are opened by
 *   an older app version.
 */
object ShaftDocCodec {

    /** Highest envelope version this build can read/write safely. */
    const val CURRENT_VERSION = 1

    /**
     * Marks a document as authored with **length-driven** runout station counts.
     *
     * 0 (absent) = written before station counts followed length, so its readings were taken
     * against the old flat defaults and [freezeLegacyStationCounts] must pin them. 1 = written
     * by this build or later; its counts are already the interval's, and freezing them would
     * be the very corruption the freeze exists to prevent.
     *
     * Not the envelope [CURRENT_VERSION]: this is additive and defaulted, readable by any
     * older build, and says nothing about the file's shape.
     */
    const val CURRENT_STATION_INTERVAL_VERSION = 1

    /**
     * Thrown when a document was written by a newer app version (envelope version above
     * [CURRENT_VERSION]). Decoding it with `ignoreUnknownKeys` would silently drop the newer
     * fields, and re-saving it would destroy them — so we refuse instead.
     */
    class UnsupportedDocVersionException(val docVersion: Int) : Exception(
        "This file was saved by a newer version of ShaftSchematic (format v$docVersion). " +
            "Update the app to open it."
    )

    /**
     * Versioned document envelope. Add new optional fields here with default values —
     * they round-trip silently with files that were saved before the field existed.
     */
    @Serializable
    data class ShaftDocV1(
        val version: Int = 1,
        @SerialName("preferred_unit")
        val preferredUnit: UnitSystem = UnitSystem.INCHES,
        @SerialName("unit_locked")
        val unitLocked: Boolean = true,
        @SerialName("job_number")
        val jobNumber: String = "",
        val customer: String = "",
        val vessel: String = "",
        @SerialName("shaft_position")
        val shaftPosition: ShaftPosition = ShaftPosition.OTHER,
        val notes: String = "",
        val spec: ShaftSpec,
        /** Runout-sheet preferences. Absent in older files → default empty config. */
        @SerialName("runout_config")
        val runoutConfig: RunoutConfig = RunoutConfig(),
        /**
         * Liner wear-inspection record. Absent in older files → default empty record.
         * Additive + defaulted: no version bump needed. See `docs/archive/LinerWearAreas_Proposal.md`.
         */
        @SerialName("wear_record")
        val wearRecord: WearRecord = WearRecord(),
        /**
         * Per-station runout (TIR) readings — value + high-spot marker per bubble. Absent in
         * older files → default empty set. Additive + defaulted: no version bump needed. Orphan
         * readings (station no longer exists) are dropped at the render layer, not here — station
         * identity depends on resolved components + count overrides the codec doesn't have. See
         * `docs/archive/RunoutBubbleEditor_PLAN.md` and `model/RunoutReading.kt`.
         */
        @SerialName("runout_readings")
        val runoutReadings: RunoutReadings = RunoutReadings(),
        /**
         * Dragged runout station positions — component-local mm per bubble the user moved.
         * Absent in older files → default empty set, i.e. every station derives its position
         * as before. Additive + defaulted: no version bump needed. Orphans (a placement whose
         * station no longer exists) are ignored at the render layer, not pruned here — the same
         * rule as [runoutReadings], and for the same reason. See `model/RunoutStationPlacement.kt`.
         */
        @SerialName("runout_stations")
        val runoutStationPlacements: RunoutStationPlacements = RunoutStationPlacements(),
        /**
         * Undercut drawing record — recorded weld/cleanup undercut sections (distance from a
         * S.E.T., length, measured Ø). Absent in older files → default empty record. Additive
         * + defaulted: no version bump needed. Undercuts have no component key, so there is no
         * decode-time pruning — see `docs/archive/UndercutDrawing_PLAN.md` §2, §7.
         */
        @SerialName("undercut_record")
        val undercutRecord: UndercutRecord = UndercutRecord(),
        /**
         * See [CURRENT_STATION_INTERVAL_VERSION]. Defaults 0 so every pre-existing file reads
         * as "authored against the old flat station counts"; [encodeV1] stamps the current
         * value on every write, so no caller can forget it.
         */
        @SerialName("station_interval_version")
        val stationIntervalVersion: Int = 0,
    )

    enum class Format { ENVELOPE_V1, LEGACY_SPEC }

    data class Decoded(
        val format: Format,
        val preferredUnit: UnitSystem?,
        val unitLocked: Boolean,
        val jobNumber: String,
        val customer: String,
        val vessel: String,
        val shaftPosition: ShaftPosition,
        val notes: String,
        val spec: ShaftSpec,
        val runoutConfig: RunoutConfig,
        val wearRecord: WearRecord,
        val runoutReadings: RunoutReadings,
        val runoutStationPlacements: RunoutStationPlacements,
        val undercutRecord: UndercutRecord,
    )

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Encodes a document for storage. [ShaftDocV1.stationIntervalVersion] is stamped HERE,
     * unconditionally — not left to callers. The stamp is what keeps
     * [freezeLegacyStationCounts] away from documents authored under length-driven station
     * counts, and the field's decode default is 0, so a writer that forgot to pass it would
     * compile cleanly while producing files that reopen with their counts pinned to the old
     * defaults and their trailing readings orphaned. No writer legitimately wants the legacy
     * value, so the encoder owns it.
     */
    fun encodeV1(doc: ShaftDocV1): String = json.encodeToString(
        ShaftDocV1.serializer(),
        doc.copy(stationIntervalVersion = CURRENT_STATION_INTERVAL_VERSION),
    )

    /**
     * Protects documents authored before station counts became length-driven.
     *
     * A runout reading is keyed `(componentId, stationIndex)`, so a component's station count
     * decides where its readings sit. Deriving counts from length (one station per 20") changes
     * that count for most components — which would slide values the user measured and wrote down
     * onto different physical spots, or orphan them. A typed value is sacred; a system change
     * must not invalidate it.
     *
     * So: every component that **already carries at least one reading** and has **no explicit
     * override** gets its pre-interval default frozen into [RunoutConfig.componentOverrides].
     * The freeze is visible and editable in the station editor — not hidden state — and a
     * document with no readings is left completely alone so it picks up the new defaults.
     *
     * **Only legacy documents.** [stationIntervalVersion] gates this: a file stamped with the
     * current version already has interval-derived counts, and pinning it to the OLD defaults
     * would be exactly the corruption this exists to prevent — a 100" body authored today at
     * 5 stations would reopen at 3 with its last two readings orphaned. Without the stamp
     * there is no way to tell the two cases apart, since neither carries an override.
     *
     * Ids are classified without resolving the spec: a taper or liner id matches its list;
     * anything else is a body (stored or auto — auto spans are bodies).
     *
     * Fidelity note: for an UNFRAGMENTED component the frozen count reproduces the old sheet
     * exactly. For a body a liner had split, the old sheet gave every run its own full count
     * with indices restarting at 0 per run — so one reading drew on several bubbles and its key
     * was genuinely ambiguous. The freeze keeps the authored count for the component as a whole,
     * which is what that body's station-editor row always claimed it had.
     */
    internal fun freezeLegacyStationCounts(
        spec: ShaftSpec,
        config: RunoutConfig,
        readings: RunoutReadings,
        stationIntervalVersion: Int,
    ): RunoutConfig {
        if (stationIntervalVersion >= CURRENT_STATION_INTERVAL_VERSION) return config
        if (readings.readings.isEmpty()) return config

        val taperIds = spec.tapers.map { it.id }.toSet()
        val linerIds = spec.liners.map { it.id }.toSet()

        val frozen = readings.readings
            .map { it.componentId }
            .distinct()
            .filter { it.isNotBlank() && it !in config.componentOverrides }
            .associateWith { id ->
                when (id) {
                    in taperIds -> RunoutConfig.LEGACY_TAPER_DEFAULT_COUNT
                    in linerIds -> RunoutConfig.LEGACY_LINER_DEFAULT_COUNT
                    else -> RunoutConfig.LEGACY_BODY_DEFAULT_COUNT
                }
            }

        if (frozen.isEmpty()) return config
        return config.copy(componentOverrides = config.componentOverrides + frozen)
    }

    fun decode(raw: String): Decoded {
        // Try envelope first.
        runCatching { json.decodeFromString(ShaftDocV1.serializer(), raw) }
            .onSuccess { doc ->
                // Refuse files from a newer format rather than silently dropping their data.
                if (doc.version > CURRENT_VERSION) throw UnsupportedDocVersionException(doc.version)
                // Back-compat thread normalization (pitch/tpi)
                val normalizedSpec = doc.spec.normalized()
                return Decoded(
                    format = Format.ENVELOPE_V1,
                    preferredUnit = doc.preferredUnit,
                    unitLocked = doc.unitLocked,
                    jobNumber = doc.jobNumber,
                    customer = doc.customer,
                    vessel = doc.vessel,
                    shaftPosition = doc.shaftPosition,
                    notes = doc.notes,
                    spec = normalizedSpec,
                    runoutConfig = freezeLegacyStationCounts(
                        spec = normalizedSpec,
                        config = doc.runoutConfig,
                        readings = doc.runoutReadings,
                        stationIntervalVersion = doc.stationIntervalVersion,
                    ),
                    // Orphan policy: spots whose linerId no longer matches a liner in this
                    // spec are dropped here, at decode time — the same layer that already
                    // normalizes the spec above. Every decode() caller (VM import/open,
                    // bundled-sample seeding, backup restore validation) gets clean data
                    // without having to remember to filter it themselves.
                    wearRecord = doc.wearRecord.copy(
                        spots = doc.wearRecord.spots.filter { spot ->
                            normalizedSpec.liners.any { it.id == spot.linerId }
                        }
                    ),
                    // Runout readings pass through untouched: orphan pruning happens at the
                    // render layer (station identity needs resolved components + count overrides,
                    // which we don't have here). Stale entries are harmless — the bubble lookup
                    // simply misses them. See model/RunoutReading.kt.
                    runoutReadings = doc.runoutReadings,
                    // Station placements pass through for the same reason as the readings above:
                    // whether a placement still has a station depends on resolved components and
                    // count overrides, which the codec cannot see.
                    runoutStationPlacements = doc.runoutStationPlacements,
                    // Undercuts have no component key at all, so there is nothing to orphan-check
                    // here — pass through untouched. See model/Undercut.kt.
                    undercutRecord = doc.undercutRecord,
                )
            }

        // Back-compat: older files were just the spec. Nothing to freeze — a spec-only file
        // predates runout readings entirely.
        val legacy = json.decodeFromString(ShaftSpec.serializer(), raw).normalized()
        return Decoded(
            format = Format.LEGACY_SPEC,
            preferredUnit = null,
            unitLocked = false,
            jobNumber = "",
            customer = "",
            vessel = "",
            shaftPosition = ShaftPosition.OTHER,
            notes = "",
            spec = legacy,
            runoutConfig = RunoutConfig(),
            wearRecord = WearRecord(),
            runoutReadings = RunoutReadings(),
            runoutStationPlacements = RunoutStationPlacements(),
            undercutRecord = UndercutRecord(),
        )
    }
}
