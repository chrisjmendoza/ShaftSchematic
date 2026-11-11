package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.resolveWearAreaSpans
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.model.WearSpotReference
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.autoBodyRuns
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.resolved.wearAreaHosts
import com.android.shaftschematic.ui.screen.wearSpotSpanProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What a shaft change does to wear areas, through the REAL [ShaftViewModel] seam and recorder
 * (on-device request: "all wear within a liner should remain in place with reference to the
 * liner"). A liner's areas are stored from the liner's own AFT edge, so no liner move, OAL
 * change or neighbour's edit touches them; a body's areas follow the body the way its pits do —
 * a bare-shaft run through the same re-keying, in the same undo step.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelWearAreaHostTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    // AFT taper [0,500] · bare [500,1000] · L1 [1000,2000] · bare G [2000,3000] ·
    // L2 [3000,3400], measured from FWD · bare [3400,4000].
    private val spec = ShaftSpec(
        overallLengthMm = 4000f,
        autoBodyDiaMm = 240f,
        tapers = listOf(Taper(id = "t", startFromAftMm = 0f, lengthMm = 500f, startDiaMm = 200f, endDiaMm = 240f)),
        liners = listOf(
            Liner(id = "L1", startFromAftMm = 1000f, lengthMm = 1000f, odMm = 254f),
            Liner(
                id = "L2", startFromAftMm = 3000f, lengthMm = 400f, odMm = 254f,
                authoredReference = LinerAuthoredReference.FWD,
            ),
        ),
    )
    private val gapId = autoBodyRuns(resolveComponents(spec)).single { it.startMm == 2000f }.id

    private val onL1 = WearSpot(
        id = "s1", linerId = "L1", startMm = 100.0125f, lengthMm = 200f, note = "scored",
        authoredReference = WearSpotReference.AFT_SET,
    )
    private val onL2 = WearSpot(id = "s2", linerId = "L2", startMm = 50f, lengthMm = 100f)
    private val onGap = WearSpot(id = "s3", linerId = gapId, startMm = 300f, lengthMm = 150f, note = "pitted")

    private fun open(): ShaftViewModel {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.importJson(
            ShaftDocCodec.encodeV1(
                ShaftDocCodec.ShaftDocV1(spec = spec, wearRecord = WearRecord(spots = listOf(onL1, onL2, onGap)))
            )
        )
        idle()
        return vm
    }

    private fun ShaftViewModel.spot(id: String) = wearRecord.value.spots.single { it.id == id }

    private fun ShaftViewModel.updateLinerById(id: String, startMm: Float, lengthMm: Float) {
        val index = spec.value.liners.indexOfFirst { it.id == id }
        updateLiner(index, startMm, lengthMm, spec.value.liners[index].odMm)
        idle()
    }

    /** The areas the sheets would draw: the record resolved against the current components. */
    private fun ShaftViewModel.drawnSpans() =
        resolveWearAreaSpans(wearRecord.value.spots, wearAreaHosts(resolveComponents(spec.value)))

    private fun ShaftViewModel.autoIdAt(mm: Float): String =
        autoBodyRuns(resolveComponents(spec.value)).single { mm >= it.startMm && mm < it.endMm }.id

    @Test
    fun `a decoded document keeps every area, on liners and on a bare-shaft run`() {
        val vm = open()
        assertEquals(listOf(onL1, onL2, onGap), vm.wearRecord.value.spots)
        assertEquals(3, vm.drawnSpans().size)
    }

    @Test
    fun `moving a liner leaves its wear areas where they were on the liner`() {
        val vm = open()
        vm.updateLinerById("L1", startMm = 1150f, lengthMm = 1000f)

        assertEquals("stored values untouched", onL1, vm.spot("s1"))
        assertEquals(onL2, vm.spot("s2"))
        // On the shaft the area moved with its liner.
        val span = vm.drawnSpans().single { it.spotId == "s1" }
        assertEquals(1150f + 100.0125f, span.startMm, 1e-3f)
    }

    @Test
    fun `an OAL change leaves liner wear areas untouched, a FWD-referenced liner included`() {
        val vm = open()
        vm.onSetOverallLengthMm(4500f)
        idle()

        assertEquals("premise: the FWD liner re-anchored", 3500f, vm.spec.value.liners.single { it.id == "L2" }.startFromAftMm, 1e-3f)
        assertEquals(onL1, vm.spot("s1"))
        assertEquals(onL2, vm.spot("s2"))
        assertEquals(3550f, vm.drawnSpans().single { it.spotId == "s2" }.startMm, 1e-3f)
    }

    @Test
    fun `shortening a liner under an area rewrites nothing - the card warns and the band clamps`() {
        val vm = open()
        vm.updateLinerById("L1", startMm = 1000f, lengthMm = 250f)

        assertEquals(onL1, vm.spot("s1"))
        assertNotNull(wearSpotSpanProblem(onL1.startMm, onL1.lengthMm, linerLengthMm = 250f))
        val span = vm.drawnSpans().single { it.spotId == "s1" }
        assertEquals("drawn only as far as the liner runs", 1250f, span.endMm, 1e-3f)
    }

    @Test
    fun `a neighbour's edit re-keys a bare-shaft area and keeps its shaft position, in ONE undo step`() {
        val vm = open()
        assertFalse(vm.canUndo.value)

        // L1 grows 80 mm FWD: the gap's AFT end moves, so its id changes.
        vm.updateLinerById("L1", startMm = 1000f, lengthMm = 1080f)
        val newId = vm.autoIdAt(2500f)
        assertNotEquals(gapId, newId)

        val area = vm.spot("s3")
        assertEquals(newId, area.linerId)
        assertEquals("same place on the shaft", 300f - 80f, area.startMm, 1e-3f)
        assertEquals("the length is the user's", 150f, area.lengthMm, 0f)
        assertEquals("pitted", area.note)
        assertEquals(2300f, vm.drawnSpans().single { it.spotId == "s3" }.startMm, 1e-3f)

        vm.undoEdit()
        idle()
        assertEquals(onGap, vm.spot("s3"))
        assertFalse("one undo took the edit and the re-key together", vm.canUndo.value)
    }

    @Test
    fun `promoting and demoting a bare-shaft run carries its area with it`() {
        val vm = open()
        val run = resolveComponents(vm.spec.value).filterIsInstance<ResolvedBody>().single { it.id == gapId }
        vm.addBodyAt(run.startMmPhysical, run.endMmPhysical - run.startMmPhysical, run.diaMm)
        idle()
        val bodyId = vm.spec.value.bodies.single().id

        assertEquals(onGap.copy(linerId = bodyId), vm.spot("s3"))
        assertEquals(2300f, vm.drawnSpans().single { it.spotId == "s3" }.startMm, 1e-3f)

        vm.reseedEditHistory() // the demotion is its own step
        vm.removeBody(bodyId)
        idle()
        assertEquals(gapId, vm.autoIdAt(2500f))
        assertEquals(onGap, vm.spot("s3"))

        vm.undoEdit()
        idle()
        assertEquals(bodyId, vm.spot("s3").linerId)
    }

    @Test
    fun `an explicit body carries its area when it moves`() {
        val vm = open()
        val run = resolveComponents(vm.spec.value).filterIsInstance<ResolvedBody>().single { it.id == gapId }
        vm.addBodyAt(run.startMmPhysical + 100f, 500f, run.diaMm)
        idle()
        val bodyId = vm.spec.value.bodies.single().id
        vm.addWearSpot(bodyId)
        val added = vm.wearRecord.value.spots.last()
        vm.updateWearSpot(added.id, startMm = 60f, lengthMm = 90f, minDiaMm = 0f, note = "")
        idle()

        vm.updateBody(0, 2300f, 500f, run.diaMm)
        idle()

        val moved = vm.spot(added.id)
        assertEquals(bodyId, moved.linerId)
        assertEquals(60f, moved.startMm, 0f)
        assertEquals(90f, moved.lengthMm, 0f)
        assertEquals(2360f, vm.drawnSpans().single { it.spotId == added.id }.startMm, 1e-3f)
    }

    @Test
    fun `deleting a liner orphans its areas - kept, not drawn - and undo brings them back`() {
        val vm = open()
        vm.removeLiner("L1")
        idle()

        assertEquals("the record is never pruned", onL1, vm.spot("s1"))
        assertTrue(vm.drawnSpans().none { it.spotId == "s1" })

        vm.undoEdit()
        idle()
        assertEquals(onL1, vm.spot("s1"))
        assertTrue(vm.drawnSpans().any { it.spotId == "s1" })
    }

    @Test
    fun `add spot on a bare-shaft run keys it to the run and opens at one inch`() {
        val vm = open()
        vm.addWearSpot(gapId)
        idle()

        val added = vm.wearRecord.value.spots.last()
        assertEquals(gapId, added.linerId)
        assertEquals(0f, added.startMm, 0f)
        assertEquals(25.4f, added.lengthMm, 0f)
    }

    @Test
    fun `a bar drag's one write is one undo step`() {
        val vm = open()
        // What the detail canvas commits on release: the dragged span, already on the grid.
        vm.updateWearSpot("s1", startMm = 101.6f, lengthMm = 152.4f, minDiaMm = onL1.minDiaMm, note = onL1.note)
        idle()
        assertEquals(101.6f, vm.spot("s1").startMm, 0f)
        assertEquals(152.4f, vm.spot("s1").lengthMm, 0f)
        assertTrue(vm.canUndo.value)

        vm.undoEdit()
        idle()
        assertEquals(onL1, vm.spot("s1"))
        assertFalse(vm.canUndo.value)
    }
}
