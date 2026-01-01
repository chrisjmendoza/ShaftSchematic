package com.android.shaftschematic.persistence

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.UnitSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class ShaftDocEnvelopeExcludeFromOalTest {

    @Serializable
    private data class ShaftDocV1(
        val version: Int = 1,
        @SerialName("preferred_unit")
        val preferredUnit: UnitSystem = UnitSystem.INCHES,
        @SerialName("unit_locked")
        val unitLocked: Boolean = true,
        val spec: ShaftSpec,
    )

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `enveloped json round trip preserves excludeFromOAL`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(
                Threads(startFromAftMm = 0f, lengthMm = 50f, majorDiaMm = 40f, pitchMm = 2f, excludeFromOAL = true)
            )
        )

        val raw = json.encodeToString(ShaftDocV1(spec = spec))
        assertTrue("expected excludeFromOAL=true to be present in JSON", raw.contains("\"excludeFromOAL\": true"))

        val decoded = json.decodeFromString<ShaftDocV1>(raw)
        assertTrue(decoded.spec.threads.first().excludeFromOAL)
    }
}
