package com.android.shaftschematic.persistence

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.UnitSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadExcludeFromOalJsonTest {

    @Serializable
    private data class ShaftDocV1(
        val version: Int = 1,
        @SerialName("preferred_unit") val preferredUnit: UnitSystem = UnitSystem.INCHES,
        @SerialName("unit_locked") val unitLocked: Boolean = true,
        val spec: ShaftSpec = ShaftSpec(),
    )

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `export contains excludeFromOAL true and import preserves it`() {
        val th = Threads(
            startFromAftMm = 0f,
            lengthMm = 10f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = true
        )
        val raw = json.encodeToString(
            ShaftDocV1(spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th)))
        )

        assertTrue("expected excludeFromOAL=true to be present in JSON", raw.contains("\"excludeFromOAL\": true"))

        val decoded = json.decodeFromString<ShaftDocV1>(raw)
        assertEquals(1, decoded.spec.threads.size)
        assertTrue(decoded.spec.threads.first().excludeFromOAL)
    }

    @Test
    fun `false stays false across round trip`() {
        val th = Threads(
            startFromAftMm = 0f,
            lengthMm = 10f,
            majorDiaMm = 50f,
            pitchMm = 2f,
            excludeFromOAL = false
        )
        val raw = json.encodeToString(
            ShaftDocV1(spec = ShaftSpec(overallLengthMm = 1000f, threads = listOf(th)))
        )

        val decoded = json.decodeFromString<ShaftDocV1>(raw)
        assertFalse(decoded.spec.threads.first().excludeFromOAL)
    }

    @Test
    fun `missing excludeFromOAL key defaults to false`() {
        val raw = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "unit_locked": true,
              "spec": {
                "overallLengthMm": 1000.0,
                "threads": [
                  {
                    "startFromAftMm": 0.0,
                    "majorDiaMm": 50.0,
                    "pitchMm": 2.0,
                    "lengthMm": 10.0
                  }
                ]
              }
            }
        """.trimIndent()

        val decoded = json.decodeFromString<ShaftDocV1>(raw)
        assertFalse(decoded.spec.threads.first().excludeFromOAL)
    }

    @Test
    fun `legacy key excludeFromOal is accepted`() {
        val raw = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "unit_locked": true,
              "spec": {
                "overallLengthMm": 1000.0,
                "threads": [
                  {
                    "startFromAftMm": 0.0,
                    "majorDiaMm": 50.0,
                    "pitchMm": 2.0,
                    "lengthMm": 10.0,
                    "excludeFromOal": true
                  }
                ]
              }
            }
        """.trimIndent()

        val decoded = json.decodeFromString<ShaftDocV1>(raw)
        assertTrue(decoded.spec.threads.first().excludeFromOAL)
    }
}
