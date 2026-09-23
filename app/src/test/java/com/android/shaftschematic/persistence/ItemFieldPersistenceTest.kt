package com.android.shaftschematic.persistence

import com.android.shaftschematic.data.AutosaveManager
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The optional "Item" project field (shaft designation — "Tail shaft", "Line shaft", …).
 *
 * Additive and defaulted at every persistence layer, so a document saved before the field
 * existed decodes to blank and re-encodes byte-identically to the way it always did.
 */
class ItemFieldPersistenceTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `item survives an envelope round-trip verbatim`() {
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = ShaftSpec(overallLengthMm = 2000f),
            item = "Tail shaft",
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals("Tail shaft", decoded.item)
    }

    @Test
    fun `an envelope written before the field decodes to blank`() {
        val legacy = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "job_number": "J-100",
              "customer": "Test Customer",
              "vessel": "Test Vessel",
              "shaft_position": "PORT",
              "spec": { "overallLengthMm": 1000.0 }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacy)

        assertEquals("", decoded.item)
        // The rest of the envelope is untouched by the addition.
        assertEquals("J-100", decoded.jobNumber)
        assertEquals(ShaftPosition.PORT, decoded.shaftPosition)
    }

    @Test
    fun `a legacy spec-only document decodes to blank`() {
        val decoded = ShaftDocCodec.decode("""{ "overallLengthMm": 500.0 }""")

        assertEquals(ShaftDocCodec.Format.LEGACY_SPEC, decoded.format)
        assertEquals("", decoded.item)
    }

    @Test
    fun `item is written under the item key`() {
        val raw = ShaftDocCodec.encodeV1(
            ShaftDocCodec.ShaftDocV1(spec = ShaftSpec(), item = "Line shaft")
        )
        assertTrue(raw.contains("\"item\": \"Line shaft\""))
    }

    @Test
    fun `item round-trips through an autosave draft snapshot`() {
        val snap = AutosaveManager.SessionSnapshot(
            shaftSpec = ShaftSpec(overallLengthMm = 400f),
            unitSystem = UnitSystem.INCHES,
            shaftPosition = ShaftPosition.OTHER,
            customer = "",
            vessel = "",
            jobNumber = "",
            notes = "",
            item = "Line shaft",
        )

        val restored = json.decodeFromString(
            AutosaveManager.SessionSnapshot.serializer(),
            json.encodeToString(AutosaveManager.SessionSnapshot.serializer(), snap),
        )

        assertEquals("Line shaft", restored.item)
    }

    @Test
    fun `a draft written before the field decodes to blank`() {
        val legacySnapshot = """
            {
              "shaftSpec": { "overallLengthMm": 400.0 },
              "unitSystem": "INCHES",
              "shaftPosition": "OTHER",
              "customer": "",
              "vessel": "",
              "jobNumber": "",
              "notes": ""
            }
        """.trimIndent()

        val restored = json.decodeFromString(
            AutosaveManager.SessionSnapshot.serializer(), legacySnapshot
        )

        assertEquals("", restored.item)
    }
}
