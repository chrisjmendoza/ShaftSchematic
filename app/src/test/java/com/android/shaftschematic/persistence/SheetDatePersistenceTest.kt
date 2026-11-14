package com.android.shaftschematic.persistence

import com.android.shaftschematic.data.AutosaveManager
import com.android.shaftschematic.data.isDefaultSession
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.doc.mateDuplicate
import com.android.shaftschematic.doc.templateDocFor
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SheetDateMode
import com.android.shaftschematic.util.UnitSystem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sheet date (`sheet_date_mode` + `sheet_date_text`). Additive and defaulted at every
 * persistence layer: a document or draft saved before the fields existed decodes to TODAY with
 * a blank text, which prints exactly what it always printed.
 */
class SheetDatePersistenceTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `the mode and text survive an envelope round-trip verbatim`() {
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = ShaftSpec(overallLengthMm = 2000f),
            sheetDateMode = SheetDateMode.CUSTOM,
            sheetDateText = "11/6/2026 ",
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertEquals(SheetDateMode.CUSTOM, decoded.sheetDateMode)
        assertEquals("the typed text is kept exactly, trailing space included", "11/6/2026 ", decoded.sheetDateText)
        assertEquals(SheetDateMode.CUSTOM, ShaftDocCodec.decodeEnvelope(ShaftDocCodec.encodeV1(doc)).sheetDateMode)
    }

    @Test
    fun `None survives a round-trip`() {
        val doc = ShaftDocCodec.ShaftDocV1(spec = ShaftSpec(), sheetDateMode = SheetDateMode.NONE)
        assertEquals(SheetDateMode.NONE, ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc)).sheetDateMode)
    }

    @Test
    fun `the fields are written under snake_case keys, the mode by name`() {
        val raw = ShaftDocCodec.encodeV1(
            ShaftDocCodec.ShaftDocV1(
                spec = ShaftSpec(), sheetDateMode = SheetDateMode.CUSTOM, sheetDateText = "Nov 6",
            )
        )
        assertTrue(raw.contains("\"sheet_date_mode\": \"CUSTOM\""))
        assertTrue(raw.contains("\"sheet_date_text\": \"Nov 6\""))
    }

    @Test
    fun `an envelope written before the fields decodes to Today`() {
        val legacy = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "job_number": "J-100",
              "customer": "Test Customer",
              "shaft_position": "PORT",
              "spec": { "overallLengthMm": 1000.0 }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacy)

        assertEquals(SheetDateMode.TODAY, decoded.sheetDateMode)
        assertEquals("", decoded.sheetDateText)
        assertEquals("J-100", decoded.jobNumber)
    }

    @Test
    fun `an unknown mode name decodes to Today instead of failing the document`() {
        val raw = """
            {
              "version": 1,
              "customer": "Test Customer",
              "sheet_date_mode": "SOMEDAY",
              "sheet_date_text": "x",
              "spec": { "overallLengthMm": 1000.0 }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertEquals(SheetDateMode.TODAY, decoded.sheetDateMode)
        assertEquals("Test Customer", decoded.customer)
    }

    @Test
    fun `a legacy spec-only document decodes to Today`() {
        val decoded = ShaftDocCodec.decode("""{ "overallLengthMm": 500.0 }""")
        assertEquals(SheetDateMode.TODAY, decoded.sheetDateMode)
        assertEquals("", decoded.sheetDateText)
    }

    @Test
    fun `the date round-trips through an autosave draft snapshot`() {
        val snap = AutosaveManager.SessionSnapshot(
            shaftSpec = ShaftSpec(overallLengthMm = 400f),
            unitSystem = UnitSystem.INCHES,
            shaftPosition = ShaftPosition.OTHER,
            customer = "",
            vessel = "",
            jobNumber = "",
            notes = "",
            sheetDateMode = SheetDateMode.CUSTOM,
            sheetDateText = "11/6/2026",
        )

        val restored = json.decodeFromString(
            AutosaveManager.SessionSnapshot.serializer(),
            json.encodeToString(AutosaveManager.SessionSnapshot.serializer(), snap),
        )

        assertEquals(SheetDateMode.CUSTOM, restored.sheetDateMode)
        assertEquals("11/6/2026", restored.sheetDateText)
    }

    @Test
    fun `a draft written before the fields decodes to Today`() {
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

        val restored = json.decodeFromString(AutosaveManager.SessionSnapshot.serializer(), legacySnapshot)

        assertEquals(SheetDateMode.TODAY, restored.sheetDateMode)
        assertEquals("", restored.sheetDateText)
    }

    @Test
    fun `a session whose only content is a chosen date is not a default session`() {
        val blank = AutosaveManager.SessionSnapshot(
            shaftSpec = ShaftSpec(),
            unitSystem = UnitSystem.INCHES,
            shaftPosition = ShaftPosition.OTHER,
            customer = "", vessel = "", jobNumber = "", notes = "",
        )
        assertTrue(blank.isDefaultSession())
        assertFalse(blank.copy(sheetDateMode = SheetDateMode.NONE).isDefaultSession())
        assertFalse(blank.copy(sheetDateText = "11/6/2026").isDefaultSession())
    }

    @Test
    fun `a template carries no date - it reads Today`() {
        val doc = templateDocFor(ShaftSpec(overallLengthMm = 1000f), UnitSystem.INCHES, unitLocked = true)
        assertEquals(SheetDateMode.TODAY, doc.sheetDateMode)
        assertEquals("", doc.sheetDateText)
    }

    @Test
    fun `a mate duplicate goes back to Today`() {
        val source = ShaftDocCodec.ShaftDocV1(
            spec = ShaftSpec(overallLengthMm = 1000f),
            sheetDateMode = SheetDateMode.CUSTOM,
            sheetDateText = "11/6/2026",
        )

        val mate = mateDuplicate(source, jobNumber = "J-2", customer = "C", vessel = "V", position = ShaftPosition.STBD)

        assertEquals(SheetDateMode.TODAY, mate.sheetDateMode)
        assertEquals("", mate.sheetDateText)
    }
}
