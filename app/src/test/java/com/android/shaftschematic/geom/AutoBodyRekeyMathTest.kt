package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacement
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WearPit
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.withNewOal
import com.android.shaftschematic.ui.resolved.autoBodyRuns
import com.android.shaftschematic.ui.resolved.isAutoBodyId
import com.android.shaftschematic.ui.resolved.resolveComponents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Data keyed to a bare-shaft (auto) body follows it when a neighbour's edit re-identifies the
 * gap (emulator QA: a 7/16" liner correction took two typed TIRs off the runout sheet). Runs
 * come from the REAL resolver, the same `autoBodyRuns(resolveComponents(..))` the editor's spec
 * seam uses.
 */
class AutoBodyRekeyMathTest {

    // AFT taper [0,500] · gap A [500,600] · L1 [600,1800] · gap B [1800,2500] · L2 [2500,3000]
    // · gap C [3000,3700] · FWD taper [3700,4000].
    private val base = ShaftSpec(
        overallLengthMm = 4000f,
        tapers = listOf(
            Taper(id = "t_aft", startFromAftMm = 0f, lengthMm = 500f, startDiaMm = 200f, endDiaMm = 240f),
            Taper(id = "t_fwd", startFromAftMm = 3700f, lengthMm = 300f, startDiaMm = 240f, endDiaMm = 210f),
        ),
        liners = listOf(
            Liner(id = "l1", startFromAftMm = 600f, lengthMm = 1200f, odMm = 254f),
            Liner(id = "l2", startFromAftMm = 2500f, lengthMm = 500f, odMm = 254f),
        ),
    )

    private fun runs(spec: ShaftSpec) = autoBodyRuns(resolveComponents(spec))

    /** The auto run containing shaft position [mm]. */
    private fun autoAt(spec: ShaftSpec, mm: Float): AutoBodyRun =
        runs(spec).single { mm >= it.startMm && mm < it.endMm }

    private fun ShaftSpec.withLiner(id: String, start: Float, length: Float) =
        copy(liners = liners.map { if (it.id == id) it.copy(startFromAftMm = start, lengthMm = length) else it })

    private fun ShaftSpec.withTaper(id: String, start: Float, length: Float) =
        copy(tapers = tapers.map { if (it.id == id) it.copy(startFromAftMm = start, lengthMm = length) else it })

    private fun rekey(before: ShaftSpec, after: ShaftSpec, data: AutoKeyedData): AutoKeyedData =
        data.rekeyedFor(autoBodySuccessors(runs(before), runs(after)))

    private fun dataOn(id: String) = AutoKeyedData(
        readings = RunoutReadings(
            listOf(
                RunoutReading(componentId = id, stationIndex = 0, valueMm = 0.0381f, highSpotHalfHours = 12),
                RunoutReading(componentId = id, stationIndex = 1, valueMm = 0.396875f),
                RunoutReading(componentId = "l1", stationIndex = 0, valueMm = 0.127f),
            )
        ),
        placements = RunoutStationPlacements(
            listOf(RunoutStationPlacement(componentId = id, stationIndex = 0, axialMm = 100f))
        ),
        wearRecord = WearRecord(
            pits = listOf(WearPit(id = "p", componentId = id, axialMm = 300f)),
            diaReadings = listOf(WearDiaReading(id = "d", componentId = id, axialMm = 200f, diaMm = 242.5f)),
            stripComponentIds = listOf("l1", id),
        ),
    )

    private fun assertFollowed(oldId: String, newRun: AutoBodyRun, shift: Float, out: AutoKeyedData) {
        assertTrue("nothing left under the old id", oldId !in out.referencedIds())
        val readings = out.readings.readings.filter { it.componentId == newRun.id }
        assertEquals(2, readings.size)
        // Typed values travel verbatim.
        assertEquals(0.0381f, readings.single { it.stationIndex == 0 }.valueMm!!, 0f)
        assertEquals(12, readings.single { it.stationIndex == 0 }.highSpotHalfHours)
        assertEquals(0.396875f, readings.single { it.stationIndex == 1 }.valueMm!!, 0f)
        assertEquals(100f + shift, out.placements.placements.single().axialMm, 1e-3f)
        assertEquals(newRun.id, out.placements.placements.single().componentId)
        assertEquals(300f + shift, out.wearRecord.pits.single().axialMm, 1e-3f)
        assertEquals(200f + shift, out.wearRecord.diaReadings.single().axialMm, 1e-3f)
        assertEquals(242.5f, out.wearRecord.diaReadings.single().diaMm, 0f)
        assertEquals(listOf("l1", newRun.id), out.wearRecord.stripComponentIds)
        // An explicit component's keys never move.
        assertEquals(0.127f, out.readings.find("l1", 0)!!.valueMm!!, 0f)
    }

    @Test
    fun `lengthening the liner AFT of a gap carries the gap's data to its new id`() {
        val oldB = autoAt(base, 2000f)
        val after = base.withLiner("l1", 600f, 1200f + 11.1125f) // a 7/16" correction
        val newB = autoAt(after, 2000f)
        assertNotEquals(oldB.id, newB.id)

        val out = rekey(base, after, dataOn(oldB.id))

        assertFollowed(oldB.id, newB, shift = oldB.startMm - newB.startMm, out = out)
        // The dragged station and the pit stay at the same SHAFT position.
        assertEquals(oldB.startMm + 100f, newB.startMm + out.placements.placements.single().axialMm, 1e-3f)
        assertEquals(oldB.startMm + 300f, newB.startMm + out.wearRecord.pits.single().axialMm, 1e-3f)
    }

    @Test
    fun `shortening the FWD taper re-keys the gap AFT of it with no axial shift`() {
        val oldC = autoAt(base, 3200f)
        val after = base.withTaper("t_fwd", 3750f, 250f)
        val newC = autoAt(after, 3200f)
        assertNotEquals(oldC.id, newC.id)
        assertEquals(oldC.startMm, newC.startMm, 0f)

        assertFollowed(oldC.id, newC, shift = 0f, out = rekey(base, after, dataOn(oldC.id)))
    }

    @Test
    fun `moving a liner a few inches re-keys the gaps on both sides of it`() {
        val oldB = autoAt(base, 2000f)
        val oldC = autoAt(base, 3500f)
        val after = base.withLiner("l2", 2500f + 76.2f, 500f)
        val newB = autoAt(after, 2000f)
        val newC = autoAt(after, 3500f)

        val successors = autoBodySuccessors(runs(base), runs(after))
        assertEquals(
            setOf(oldB.id to newB.id, oldC.id to newC.id),
            successors.map { it.oldRun.id to it.newRun.id }.toSet(),
        )

        val out = rekey(base, after, dataOn(oldC.id))
        assertFollowed(oldC.id, newC, shift = oldC.startMm - newC.startMm, out = out)
        // The pit 300 mm into the old gap sits 223.8 mm into the new one: same shaft station.
        assertEquals(223.8f, out.wearRecord.pits.single().axialMm, 1e-3f)
    }

    @Test
    fun `a value the shift strands outside the new span is stored as is, never clamped`() {
        val oldC = autoAt(base, 3500f)
        val after = base.withLiner("l2", 2500f + 76.2f, 500f)
        val data = AutoKeyedData(
            placements = RunoutStationPlacements(
                listOf(RunoutStationPlacement(componentId = oldC.id, stationIndex = 0, axialMm = 20f))
            ),
        )
        val out = rekey(base, after, data)
        // 20 mm into the old gap is now under the liner: −56.2 mm, left for the render layer.
        assertEquals(20f - 76.2f, out.placements.placements.single().axialMm, 1e-3f)
    }

    @Test
    fun `an OAL change moving the last auto run's end carries its data`() {
        val open = base.copy(tapers = base.tapers.filter { it.id != "t_fwd" })
        val oldLast = autoAt(open, 3500f)
        assertEquals(4000f, oldLast.endMm, 0f)
        val after = open.withNewOal(4500f)
        val newLast = autoAt(after, 3500f)
        assertEquals(4500f, newLast.endMm, 0f)

        assertFollowed(oldLast.id, newLast, shift = 0f, out = rekey(open, after, dataOn(oldLast.id)))
    }

    @Test
    fun `a gap split by an inserted liner is ambiguous and keeps its old keys`() {
        val oldB = autoAt(base, 2000f)
        val after = base.copy(liners = base.liners + Liner(id = "l3", startFromAftMm = 2000f, lengthMm = 200f, odMm = 254f))
        assertTrue(autoBodySuccessors(runs(base), runs(after)).none { it.oldRun.id == oldB.id })

        val data = dataOn(oldB.id)
        assertSame(data, rekey(base, after, data))
    }

    @Test
    fun `two gaps merged by a removed liner are ambiguous and keep their old keys`() {
        val oldB = autoAt(base, 2000f)
        val oldC = autoAt(base, 3500f)
        val after = base.copy(liners = base.liners.filter { it.id != "l2" })
        val merged = autoAt(after, 2000f)
        assertEquals(merged.id, autoAt(after, 3500f).id)

        assertTrue(autoBodySuccessors(runs(base), runs(after)).isEmpty())
        val data = dataOn(oldB.id).copy(
            readings = RunoutReadings(dataOn(oldB.id).readings.readings + RunoutReading(oldC.id, 0, 0.05f)),
        )
        assertSame(data, rekey(base, after, data))
    }

    @Test
    fun `a gap filled completely has no successor`() {
        val after = base.withLiner("l1", 500f, 1300f) // L1 now butts the AFT taper
        val oldA = autoAt(base, 550f)
        assertTrue(runs(after).none { 550f >= it.startMm && 550f < it.endMm })
        assertTrue(autoBodySuccessors(runs(base), runs(after)).none { it.oldRun.id == oldA.id })
    }

    @Test
    fun `crossing a 20 inch station boundary freezes the old derived count`() {
        val oldB = autoAt(base, 2000f) // 700 mm → 2 derived stations
        val after = base.withLiner("l1", 600f, 1400f) // gap B is now 500 mm → 1 derived station
        val newB = autoAt(after, 2200f)
        assertEquals(2, defaultStationCount(RunoutComponentKind.BODY, oldB.lengthMm))
        assertEquals(1, defaultStationCount(RunoutComponentKind.BODY, newB.lengthMm))

        val out = rekey(base, after, dataOn(oldB.id))
        assertEquals(mapOf(newB.id to 2), out.stationCountOverrides)
    }

    @Test
    fun `an existing override is renamed, not re-derived`() {
        val oldB = autoAt(base, 2000f)
        val after = base.withLiner("l1", 600f, 1400f)
        val newB = autoAt(after, 2200f)
        val data = dataOn(oldB.id).copy(stationCountOverrides = mapOf(oldB.id to 4, "l1" to 2))

        val out = rekey(base, after, data)
        assertEquals(mapOf(newB.id to 4, "l1" to 2), out.stationCountOverrides)
    }

    @Test
    fun `a derived count that does not change is not frozen`() {
        val oldB = autoAt(base, 2000f)
        val after = base.withLiner("l1", 600f, 1211.1125f) // 688.9 mm, still 2 stations
        val out = rekey(base, after, dataOn(oldB.id))
        assertTrue(out.stationCountOverrides.isEmpty())
    }

    @Test
    fun `a gap carrying only wear data never gains a count override`() {
        val oldB = autoAt(base, 2000f)
        val after = base.withLiner("l1", 600f, 1400f)
        val data = AutoKeyedData(wearRecord = WearRecord(pits = listOf(WearPit(componentId = oldB.id, axialMm = 50f))))
        val out = rekey(base, after, data)
        assertTrue(out.stationCountOverrides.isEmpty())
        assertEquals(autoAt(after, 2200f).id, out.wearRecord.pits.single().componentId)
    }

    @Test
    fun `a successor whose new id already holds stale data is skipped`() {
        val oldB = autoAt(base, 2000f)
        val after = base.withLiner("l1", 600f, 1400f)
        val newB = autoAt(after, 2200f)
        val data = dataOn(oldB.id).copy(
            readings = RunoutReadings(dataOn(oldB.id).readings.readings + RunoutReading(newB.id, 0, 0.2f)),
        )
        assertSame(data, rekey(base, after, data))
    }

    @Test
    fun `an edit that leaves every auto id alone changes nothing`() {
        val after = base.copy(liners = base.liners.map { if (it.id == "l1") it.copy(odMm = 260f) else it })
        assertTrue(autoBodySuccessors(runs(base), runs(after)).isEmpty())
        val data = dataOn(autoAt(base, 2000f).id)
        assertSame(data, rekey(base, after, data))
    }

    // ── Promotion / demotion: the body's identity changes, its span does not ──────────────

    private fun swapRekey(before: ShaftSpec, after: ShaftSpec, data: AutoKeyedData): AutoKeyedData =
        data.rekeyedFor(bodyKeySuccessors(runs(before), runs(after), storedBodyRuns(before), storedBodyRuns(after)))

    private fun ShaftSpec.withBody(id: String, start: Float, end: Float) =
        copy(bodies = bodies + Body(id = id, startFromAftMm = start, lengthMm = end - start, diaMm = 240f))

    @Test
    fun `promoting a gap carries its data to the new explicit body`() {
        val gap = autoAt(base, 2000f)
        val after = base.withBody("b_new", gap.startMm, gap.endMm)
        val out = swapRekey(base, after, dataOn(gap.id))
        val explicit = AutoBodyRun("b_new", gap.startMm, gap.endMm)
        assertFollowed(gap.id, explicit, shift = 0f, out = out)
    }

    @Test
    fun `promotion through the card's start plus length float sum still matches`() {
        // The card hands addBodyAt (start, end − start); the stored end is their float sum.
        val gap = autoAt(base, 2000f)
        val len = gap.endMm - gap.startMm
        val after = base.copy(bodies = listOf(Body(id = "b_new", startFromAftMm = gap.startMm, lengthMm = len, diaMm = 240f)))
        val out = swapRekey(base, after, dataOn(gap.id))
        assertEquals(2, out.readings.readings.count { it.componentId == "b_new" })
    }

    @Test
    fun `demoting an explicit body carries its data to the auto run on its span`() {
        val gap = autoAt(base, 2000f)
        val promoted = base.withBody("b_x", gap.startMm, gap.endMm)
        val out = swapRekey(promoted, base, dataOn("b_x"))
        assertFollowed("b_x", gap, shift = 0f, out = out)
        assertTrue(isAutoBodyId(out.readings.readings.first { it.componentId != "l1" }.componentId))
    }

    @Test
    fun `an explicit body added over PART of a gap takes nothing`() {
        val gap = autoAt(base, 2000f)
        val after = base.withBody("b_part", gap.startMm, gap.startMm + 300f)
        val data = dataOn(gap.id)
        // The gap's remainder is a NEW auto run overlapping the old one, so the 5a rule carries the
        // data there; the explicit body never claims it.
        val out = swapRekey(base, after, data)
        assertTrue(out.readings.readings.none { it.componentId == "b_part" })
    }

    @Test
    fun `demoting a body flanked by gaps merges them and stays ambiguous`() {
        // A body mid-gap B: removing it fuses three runs into one, which no longer equals its span.
        val withBody = base.withBody("b_mid", 2000f, 2200f)
        val data = dataOn("b_mid")
        val out = swapRekey(withBody, base, data)
        assertSame(data.readings, out.readings)
    }

    @Test
    fun `a promotion whose new id already holds stale data is skipped`() {
        val gap = autoAt(base, 2000f)
        val after = base.withBody("b_new", gap.startMm, gap.endMm)
        val stale = dataOn(gap.id).let {
            it.copy(readings = RunoutReadings(it.readings.readings + RunoutReading(componentId = "b_new", stationIndex = 0, valueMm = 0.5f)))
        }
        assertSame(stale, swapRekey(base, after, stale))
    }

    @Test
    fun `the device job's readings follow a 7-16 inch AFT liner correction`() {
        val raw = requireNotNull(javaClass.classLoader?.getResource("fixtures/runout_auto_body_polar_ranger.shaft")).readText()
        val doc = com.android.shaftschematic.doc.ShaftDocCodec.decode(raw)
        val oldId = "auto_body_1793.875_5435.600"
        assertTrue(runs(doc.spec).any { it.id == oldId })

        val after = doc.spec.copy(
            liners = doc.spec.liners.map {
                if (it.id == "26323d09-7e38-4a69-85e7-b8067936d81b") it.copy(lengthMm = 1219.2f) else it
            }
        )
        val data = AutoKeyedData(
            readings = doc.runoutReadings,
            placements = doc.runoutStationPlacements,
            stationCountOverrides = doc.runoutConfig.componentOverrides,
            wearRecord = doc.wearRecord,
        )
        val out = rekey(doc.spec, after, data)
        val newId = autoAt(after, 3000f).id
        assertNotEquals(oldId, newId)
        assertTrue(isAutoBodyId(newId))

        assertEquals(0.0381f, out.readings.find(newId, 1)!!.valueMm!!, 0f)
        assertEquals(0.396875f, out.readings.find(newId, 3)!!.valueMm!!, 0f)
        assertNull(out.readings.find(oldId, 1))
        assertEquals(4, out.stationCountOverrides[newId])
        // The untouched auto runs and the explicit components keep their keys.
        assertEquals(4, out.stationCountOverrides["auto_body_6369.050_10013.950"])
        assertEquals(2, out.stationCountOverrides["26323d09-7e38-4a69-85e7-b8067936d81b"])
        assertEquals(data.placements, out.placements)
        assertEquals(data.readings.readings.size, out.readings.readings.size)
    }
}
