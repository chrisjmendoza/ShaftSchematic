package com.android.shaftschematic.doc

import com.android.shaftschematic.model.DyePenResult
import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacement
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.WearPit
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Duplicate for mate": the copy is the same shaft under a new identity, carrying none of the
 * measurements taken on the original. See `doc/MateDuplicate.kt`.
 */
class MateDuplicateTest {

    private val spec = ShaftSpec(overallLengthMm = 5000f, autoBodyDiaMm = 152.4f)

    private val source = ShaftDocCodec.ShaftDocV1(
        preferredUnit = UnitSystem.MILLIMETERS,
        unitLocked = false,
        jobNumber = "J-100",
        customer = "Acme",
        vessel = "Tidewater",
        item = "Tail shaft",
        shaftPosition = ShaftPosition.PORT,
        notes = "Straighten before fitting",
        spec = spec,
        runoutConfig = RunoutConfig(
            componentOverrides = mapOf("body-1" to 4),
            heightScale = 1.4f,
            linersProportional = true,
        ),
        wearRecord = WearRecord(
            pits = listOf(WearPit(componentId = "liner-1", axialMm = 10f)),
            dyePenResult = DyePenResult.FAIL,
        ),
        runoutReadings = RunoutReadings(
            listOf(RunoutReading(componentId = "body-1", stationIndex = 0, valueMm = 0.08f))
        ),
        runoutStationPlacements = RunoutStationPlacements(
            listOf(RunoutStationPlacement(componentId = "body-1", stationIndex = 0, axialMm = 25f))
        ),
        undercutRecord = UndercutRecord(
            undercuts = listOf(Undercut(startFromAftMm = 100f, lengthMm = 50f, diaMm = 149f))
        ),
        unitOverrides = mapOf("taper-1" to UnitSystem.MILLIMETERS),
        dualUnits = true,
    )

    private fun duplicate() = mateDuplicate(
        source = source,
        jobNumber = "J-101",
        customer = "Acme Marine",
        vessel = "Tidewater II",
        position = ShaftPosition.STBD,
    )

    @Test
    fun `identity fields come from the arguments`() {
        val mate = duplicate()

        assertEquals("J-101", mate.jobNumber)
        assertEquals("Acme Marine", mate.customer)
        assertEquals("Tidewater II", mate.vessel)
        assertEquals(ShaftPosition.STBD, mate.shaftPosition)
    }

    @Test
    fun `geometry and authoring facts travel verbatim`() {
        val mate = duplicate()

        assertEquals(spec, mate.spec)
        assertEquals(UnitSystem.MILLIMETERS, mate.preferredUnit)
        assertEquals(false, mate.unitLocked)
        assertEquals(source.unitOverrides, mate.unitOverrides)
        assertEquals(true, mate.dualUnits)
        assertEquals(source.runoutConfig, mate.runoutConfig)
        assertEquals("Tail shaft", mate.item)
        assertEquals("Straighten before fitting", mate.notes)
    }

    @Test
    fun `every measurement record resets to its empty default`() {
        val mate = duplicate()

        assertEquals(WearRecord(), mate.wearRecord)
        assertEquals(RunoutReadings(), mate.runoutReadings)
        assertEquals(RunoutStationPlacements(), mate.runoutStationPlacements)
        assertEquals(UndercutRecord(), mate.undercutRecord)
    }

    @Test
    fun `a reset record carries nothing of the original's measurements`() {
        val mate = duplicate()

        assertTrue(mate.wearRecord.pits.isEmpty())
        assertEquals(null, mate.wearRecord.dyePenResult)
        assertTrue(mate.runoutReadings.readings.isEmpty())
        assertTrue(mate.runoutStationPlacements.placements.isEmpty())
        assertTrue(mate.undercutRecord.undercuts.isEmpty())
    }

    @Test
    fun `the source document is untouched`() {
        duplicate()

        assertEquals("J-100", source.jobNumber)
        assertEquals(ShaftPosition.PORT, source.shaftPosition)
        assertEquals(1, source.runoutReadings.readings.size)
    }

    @Test
    fun `blank identity is accepted verbatim - the duplicate never invents one`() {
        val mate = mateDuplicate(source, "", "", "", ShaftPosition.OTHER)

        assertEquals("", mate.jobNumber)
        assertEquals("", mate.customer)
        assertEquals("", mate.vessel)
        assertEquals(ShaftPosition.OTHER, mate.shaftPosition)
    }

    /* ── matePosition ── */

    @Test
    fun `matePosition flips the two sides and leaves the rest alone`() {
        assertEquals(ShaftPosition.STBD, matePosition(ShaftPosition.PORT))
        assertEquals(ShaftPosition.PORT, matePosition(ShaftPosition.STBD))
        assertEquals(ShaftPosition.CENTER, matePosition(ShaftPosition.CENTER))
        assertEquals(ShaftPosition.OTHER, matePosition(ShaftPosition.OTHER))
    }

    @Test
    fun `matePosition is its own inverse`() {
        ShaftPosition.entries.forEach { p ->
            assertEquals(p, matePosition(matePosition(p)))
        }
    }

    /* ── The codec seam the Open screen duplicates through ── */

    @Test
    fun `decodeEnvelope round-trips an encoded document`() {
        val decoded = ShaftDocCodec.decodeEnvelope(ShaftDocCodec.encodeV1(source))

        assertEquals(source.jobNumber, decoded.jobNumber)
        assertEquals(source.customer, decoded.customer)
        assertEquals(source.vessel, decoded.vessel)
        assertEquals(source.item, decoded.item)
        assertEquals(source.notes, decoded.notes)
        assertEquals(source.shaftPosition, decoded.shaftPosition)
        assertEquals(source.spec, decoded.spec)
        assertEquals(source.runoutConfig, decoded.runoutConfig)
        assertEquals(source.unitOverrides, decoded.unitOverrides)
        assertEquals(source.dualUnits, decoded.dualUnits)
        assertEquals(source.undercutRecord, decoded.undercutRecord)
        assertEquals(source.runoutReadings, decoded.runoutReadings)
    }

    @Test
    fun `a document duplicated through the codec seam keeps its geometry and drops its readings`() {
        val mate = mateDuplicate(
            source = ShaftDocCodec.decodeEnvelope(ShaftDocCodec.encodeV1(source)),
            jobNumber = "J-101",
            customer = "Acme",
            vessel = "Tidewater",
            position = matePosition(source.shaftPosition),
        )

        assertEquals(spec, mate.spec)
        assertEquals(ShaftPosition.STBD, mate.shaftPosition)
        assertEquals(RunoutReadings(), mate.runoutReadings)
        assertEquals(UndercutRecord(), mate.undercutRecord)
    }
}
