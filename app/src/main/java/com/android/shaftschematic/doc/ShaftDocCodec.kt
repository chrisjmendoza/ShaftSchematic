package com.android.shaftschematic.doc

import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
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
    )

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encodeV1(doc: ShaftDocV1): String = json.encodeToString(ShaftDocV1.serializer(), doc)

    fun decode(raw: String): Decoded {
        // Try envelope first.
        runCatching { json.decodeFromString(ShaftDocV1.serializer(), raw) }
            .onSuccess { doc ->
                return Decoded(
                    format = Format.ENVELOPE_V1,
                    preferredUnit = doc.preferredUnit,
                    unitLocked = doc.unitLocked,
                    jobNumber = doc.jobNumber,
                    customer = doc.customer,
                    vessel = doc.vessel,
                    shaftPosition = doc.shaftPosition,
                    notes = doc.notes,
                    // Back-compat thread normalization (pitch/tpi)
                    spec = doc.spec.normalized(),
                    runoutConfig = doc.runoutConfig,
                )
            }

        // Back-compat: older files were just the spec.
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
        )
    }
}
