package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A template file carries **geometry only**.
 *
 * Job identity and measurement records are dropped at WRITE time, not merely ignored on load:
 * a template that still contained a customer name would carry it into every drawing built from
 * it, and into any copy of that file. `ShaftViewModel` is an `AndroidViewModel` and is not
 * instantiated in this JVM suite (the convention `ShaftViewModelUnsavedWorkTest` follows), so
 * this mirrors `exportTemplateJson`'s exact envelope and checks what a saved template holds.
 */
class TemplateScrubTest {

    private val spec = ShaftSpec(
        overallLengthMm = 2400f,
        bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 2400f, diaMm = 114.3f)),
        liners = listOf(Liner(id = "l1", startFromAftMm = 400f, lengthMm = 300f, odMm = 152.4f)),
    )

    /** A loaded job: geometry plus everything a template must not inherit. */
    private fun fullJobDoc() = ShaftDocCodec.ShaftDocV1(
        preferredUnit = UnitSystem.INCHES,
        unitLocked = true,
        jobNumber = "814201",
        customer = "NorthSound Marine",
        vessel = "FV Tern Point",
        shaftPosition = ShaftPosition.PORT,
        notes = "Cut liner off at the aft end.",
        spec = spec,
        runoutConfig = RunoutConfig(componentOverrides = mapOf("b1" to 5), heightScale = 1.4f),
        wearRecord = WearRecord(spots = listOf(WearSpot(linerId = "l1"))),
        runoutReadings = RunoutReadings(listOf(RunoutReading(componentId = "b1", stationIndex = 0, valueMm = 0.05f))),
        undercutRecord = UndercutRecord(),
    )

    /**
     * Exact mirror of ShaftViewModel.exportTemplateJson(). Nothing else may appear here: any
     * envelope field this mirror sets that the real function does not (or vice versa) is
     * scrub-coverage drift. `station_interval_version` is stamped inside `encodeV1` itself,
     * so neither function carries it.
     */
    private fun exportTemplateJson(doc: ShaftDocCodec.ShaftDocV1): String =
        ShaftDocCodec.encodeV1(
            ShaftDocCodec.ShaftDocV1(
                preferredUnit = doc.preferredUnit,
                unitLocked = doc.unitLocked,
                spec = doc.spec,
            )
        )

    @Test
    fun `a saved template carries no job identity`() {
        val decoded = ShaftDocCodec.decode(exportTemplateJson(fullJobDoc()))

        assertEquals("", decoded.jobNumber)
        assertEquals("", decoded.customer)
        assertEquals("", decoded.vessel)
        assertEquals("", decoded.notes)
        assertEquals(ShaftPosition.OTHER, decoded.shaftPosition)
    }

    @Test
    fun `a saved template carries no measurements`() {
        val decoded = ShaftDocCodec.decode(exportTemplateJson(fullJobDoc()))

        assertTrue(decoded.wearRecord.spots.isEmpty())
        assertTrue(decoded.wearRecord.pits.isEmpty())
        assertTrue(decoded.wearRecord.diaReadings.isEmpty())
        assertTrue(decoded.wearRecord.wornSections.isEmpty())
        assertTrue(decoded.runoutReadings.readings.isEmpty())
        assertTrue(decoded.undercutRecord.undercuts.isEmpty())
    }

    @Test
    fun `a saved template carries no per-job sheet tuning`() {
        val decoded = ShaftDocCodec.decode(exportTemplateJson(fullJobDoc()))

        assertEquals(RunoutConfig(), decoded.runoutConfig)
        assertTrue(decoded.runoutConfig.componentOverrides.isEmpty())
    }

    @Test
    fun `the geometry and authoring unit do travel`() {
        val decoded = ShaftDocCodec.decode(exportTemplateJson(fullJobDoc()))

        assertEquals(2400f, decoded.spec.overallLengthMm, 0.001f)
        assertEquals(114.3f, decoded.spec.bodies.single().diaMm, 0.001f)
        assertEquals(152.4f, decoded.spec.liners.single().odMm, 0.001f)
        assertEquals(UnitSystem.INCHES, decoded.preferredUnit)
        assertTrue(decoded.unitLocked)
    }

    @Test
    fun `a scrubbed template needs no station freeze on load`() {
        // No readings ride along, so the legacy-count freeze has nothing to pin and the
        // template picks up the current length-driven defaults.
        val decoded = ShaftDocCodec.decode(exportTemplateJson(fullJobDoc()))
        assertFalse(decoded.runoutConfig.componentOverrides.containsKey("b1"))
    }
}
