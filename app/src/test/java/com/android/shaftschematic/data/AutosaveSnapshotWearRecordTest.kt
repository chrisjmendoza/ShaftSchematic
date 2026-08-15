package com.android.shaftschematic.data

import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.model.WearSpotReference
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.util.UnitSystem
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The autosave draft snapshot must carry the wear record alongside the spec, exactly like
 * it already carries `runoutConfig` — see docs/LinerWearAreas_Proposal.md §4/§5 and
 * `ShaftViewModel`'s autosave `combine(...)` block.
 */
class AutosaveSnapshotWearRecordTest {

    // Mirrors AutosaveManager's private Json config; SessionSnapshot itself is the real,
    // public nested class so this test pins the actual wire format.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `session snapshot round trip preserves wear record`() {
        val snapshot = AutosaveManager.SessionSnapshot(
            shaftSpec = ShaftSpec(overallLengthMm = 400f),
            unitSystem = UnitSystem.INCHES,
            shaftPosition = ShaftPosition.PORT,
            customer = "Acme Tug Co",
            vessel = "Tidewater",
            jobNumber = "J-934918",
            notes = "",
            runoutConfig = RunoutConfig(),
            unitLocked = true,
            overallIsManual = false,
            wearRecord = WearRecord(
                spots = listOf(WearSpot(id = "s1", linerId = "ln1", startMm = 12f, lengthMm = 30f, minDiaMm = 90f))
            ),
        )

        val raw = json.encodeToString(snapshot)
        assertTrue("expected wearRecord key in autosave JSON", raw.contains("wearRecord"))

        val restored = json.decodeFromString<AutosaveManager.SessionSnapshot>(raw)

        assertEquals(1, restored.wearRecord.spots.size)
        val spot = restored.wearRecord.spots.single()
        assertEquals("ln1", spot.linerId)
        assertEquals(12f, spot.startMm, 0.001f)
        assertEquals(30f, spot.lengthMm, 0.001f)
        assertEquals(90f, spot.minDiaMm, 0.001f)
    }

    @Test
    fun `session snapshot round trip preserves the wear spot's authoredReference`() {
        val snapshot = AutosaveManager.SessionSnapshot(
            shaftSpec = ShaftSpec(overallLengthMm = 400f),
            unitSystem = UnitSystem.INCHES,
            shaftPosition = ShaftPosition.PORT,
            customer = "Acme Tug Co",
            vessel = "Tidewater",
            jobNumber = "J-934918",
            notes = "",
            runoutConfig = RunoutConfig(),
            unitLocked = true,
            overallIsManual = false,
            wearRecord = WearRecord(
                spots = listOf(
                    WearSpot(
                        id = "s1", linerId = "ln1", startMm = 12f, lengthMm = 30f,
                        authoredReference = WearSpotReference.LINER_FWD,
                    )
                )
            ),
        )

        val raw = json.encodeToString(snapshot)
        val restored = json.decodeFromString<AutosaveManager.SessionSnapshot>(raw)

        assertEquals(WearSpotReference.LINER_FWD, restored.wearRecord.spots.single().authoredReference)
    }

    @Test
    fun `session snapshot round trip preserves the wear sheet's strip election`() {
        val snapshot = AutosaveManager.SessionSnapshot(
            shaftSpec = ShaftSpec(overallLengthMm = 400f),
            unitSystem = UnitSystem.INCHES,
            shaftPosition = ShaftPosition.PORT,
            customer = "Acme Tug Co",
            vessel = "Tidewater",
            jobNumber = "J-934918",
            notes = "",
            runoutConfig = RunoutConfig(),
            unitLocked = true,
            overallIsManual = false,
            wearRecord = WearRecord(
                stripComponentIds = listOf("ln1", "auto_body_0.000_100.000"),
                showShaftProfile = false,
            ),
        )

        val raw = json.encodeToString(snapshot)
        val restored = json.decodeFromString<AutosaveManager.SessionSnapshot>(raw)

        assertEquals(listOf("ln1", "auto_body_0.000_100.000"), restored.wearRecord.stripComponentIds)
        assertEquals(false, restored.wearRecord.showShaftProfile)
    }

    @Test
    fun `an empty strip election survives the draft round trip as empty`() {
        val snapshot = AutosaveManager.SessionSnapshot(
            shaftSpec = ShaftSpec(overallLengthMm = 400f),
            unitSystem = UnitSystem.INCHES,
            shaftPosition = ShaftPosition.PORT,
            customer = "",
            vessel = "",
            jobNumber = "",
            notes = "",
            runoutConfig = RunoutConfig(),
            unitLocked = true,
            overallIsManual = false,
            wearRecord = WearRecord(stripComponentIds = emptyList()),
        )

        val restored = json.decodeFromString<AutosaveManager.SessionSnapshot>(json.encodeToString(snapshot))

        assertEquals(emptyList<String>(), restored.wearRecord.stripComponentIds)
    }

    @Test
    fun `older draft json without wearRecord field decodes to empty record`() {
        // Simulates a draft written before this field existed.
        val raw = """
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

        val restored = json.decodeFromString<AutosaveManager.SessionSnapshot>(raw)

        assertTrue(restored.wearRecord.spots.isEmpty())
        // Strip election unset = the default sheet: every drawable liner, profile drawn.
        assertTrue(restored.wearRecord.stripComponentIds == null)
        assertTrue(restored.wearRecord.showShaftProfile)
    }
}
