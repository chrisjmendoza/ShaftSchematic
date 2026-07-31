package com.android.shaftschematic.data

import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.util.UnitSystem
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The autosave draft snapshot must carry the undercut record alongside the spec, exactly
 * like it already carries `wearRecord`/`runoutReadings` — see
 * `docs/UndercutDrawing_PLAN.md` §7 and `ShaftViewModel`'s autosave `combine(...)` block.
 */
class AutosaveSnapshotUndercutTest {

    // Mirrors AutosaveManager's private Json config; SessionSnapshot itself is the real,
    // public nested class so this test pins the actual wire format.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `session snapshot round trip preserves undercut record`() {
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
            undercutRecord = UndercutRecord(
                undercuts = listOf(
                    Undercut(id = "u1", startFromAftMm = 120f, lengthMm = 25.4f, diaMm = 247.5f)
                )
            ),
        )

        val raw = json.encodeToString(snapshot)
        assertTrue("expected undercutRecord key in autosave JSON", raw.contains("undercutRecord"))

        val restored = json.decodeFromString<AutosaveManager.SessionSnapshot>(raw)

        assertEquals(1, restored.undercutRecord.undercuts.size)
        val u = restored.undercutRecord.undercuts.single()
        assertEquals("u1", u.id)
        assertEquals(120f, u.startFromAftMm, 0.001f)
        assertEquals(25.4f, u.lengthMm, 0.001f)
        assertEquals(247.5f, u.diaMm, 0.001f)
    }

    @Test
    fun `session snapshot round trip preserves the undercut's authoredReference`() {
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
            undercutRecord = UndercutRecord(
                undercuts = listOf(
                    Undercut(
                        id = "u1", startFromAftMm = 12f, lengthMm = 30f,
                        authoredReference = UndercutReference.FWD_SET,
                    )
                )
            ),
        )

        val raw = json.encodeToString(snapshot)
        val restored = json.decodeFromString<AutosaveManager.SessionSnapshot>(raw)

        assertEquals(UndercutReference.FWD_SET, restored.undercutRecord.undercuts.single().authoredReference)
    }

    @Test
    fun `older draft json without undercutRecord field decodes to empty record`() {
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

        assertTrue(restored.undercutRecord.undercuts.isEmpty())
    }
}
