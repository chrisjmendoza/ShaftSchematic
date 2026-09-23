package com.android.shaftschematic.data

import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.util.UnitSystem
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-format tests for the draft ring ([AutosaveManager.DraftEntry]). Pins the JSON list shape
 * that data/AutosaveManager.kt reads/writes, incl. an embedded legacy [SessionSnapshot] and
 * unknown-key tolerance. See docs/archive/Autosave_Incident_2026-07-25.md.
 */
class AutosaveDraftSerializationTest {

    // Mirrors AutosaveManager's private Json config.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun snapshot(customer: String) = AutosaveManager.SessionSnapshot(
        shaftSpec = ShaftSpec(overallLengthMm = 400f),
        unitSystem = UnitSystem.INCHES,
        shaftPosition = ShaftPosition.PORT,
        customer = customer,
        vessel = "Tidewater",
        jobNumber = "J-1",
        notes = "",
        runoutConfig = RunoutConfig(),
        unitLocked = true,
        wearRecord = WearRecord(
            spots = listOf(WearSpot(id = "s1", linerId = "ln1", startMm = 12f, lengthMm = 30f, minDiaMm = 90f))
        ),
    )

    @Test
    fun `draft entry list round trips including embedded snapshot`() {
        val list = listOf(
            AutosaveManager.DraftEntry(
                draftId = "d-1",
                documentName = "AleutianSpray.shaft",
                updatedAtEpochMs = 1_700_000_000_000L,
                snapshot = snapshot("Acme"),
            ),
            AutosaveManager.DraftEntry(
                draftId = "d-2",
                documentName = null,
                updatedAtEpochMs = 1_699_000_000_000L,
                snapshot = snapshot("Beta"),
            ),
        )

        val raw = json.encodeToString(list)
        val restored = json.decodeFromString<List<AutosaveManager.DraftEntry>>(raw)

        assertEquals(2, restored.size)
        assertEquals("d-1", restored[0].draftId)
        assertEquals("AleutianSpray.shaft", restored[0].documentName)
        assertEquals(1_700_000_000_000L, restored[0].updatedAtEpochMs)
        assertEquals("Acme", restored[0].snapshot.customer)
        assertEquals(1, restored[0].snapshot.wearRecord.spots.size)
        assertEquals("ln1", restored[0].snapshot.wearRecord.spots.single().linerId)

        assertEquals("d-2", restored[1].draftId)
        assertEquals(null, restored[1].documentName)
    }

    @Test
    fun `unknown keys in draft json are tolerated`() {
        // Simulates a newer/older writer including fields this decoder doesn't know.
        val raw = """
            [
              {
                "draftId": "d-1",
                "documentName": "X.shaft",
                "updatedAtEpochMs": 123,
                "snapshot": {
                  "shaftSpec": { "overallLengthMm": 400.0 },
                  "unitSystem": "INCHES",
                  "shaftPosition": "OTHER",
                  "customer": "",
                  "vessel": "",
                  "jobNumber": "",
                  "notes": ""
                },
                "futureField": "ignored"
              }
            ]
        """.trimIndent()

        val restored = json.decodeFromString<List<AutosaveManager.DraftEntry>>(raw)

        assertEquals(1, restored.size)
        assertEquals("d-1", restored.single().draftId)
        // Snapshot fields absent in the older embedded shape default cleanly.
        assertTrue(restored.single().snapshot.wearRecord.spots.isEmpty())
        assertEquals(400f, restored.single().snapshot.shaftSpec.overallLengthMm, 0.001f)
    }

    @Test
    fun `legacy single-slot snapshot decodes as a snapshot for migration wrapping`() {
        // The exact shape stored under the old autosave_last_session key.
        val legacyRaw = json.encodeToString(snapshot("Legacy Co"))

        val snap = json.decodeFromString<AutosaveManager.SessionSnapshot>(legacyRaw)
        val wrapped = AutosaveManager.DraftEntry(
            draftId = AutosaveManager.LEGACY_DRAFT_ID,
            documentName = null,
            updatedAtEpochMs = 42L,
            snapshot = snap,
        )

        val raw = json.encodeToString(listOf(wrapped))
        val restored = json.decodeFromString<List<AutosaveManager.DraftEntry>>(raw)

        assertEquals(AutosaveManager.LEGACY_DRAFT_ID, restored.single().draftId)
        assertEquals("Legacy Co", restored.single().snapshot.customer)
    }
}
