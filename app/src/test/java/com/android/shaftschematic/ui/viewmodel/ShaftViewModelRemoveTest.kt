package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.WearRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Component removal.
 *
 * Deleting a taper frees the span it occupied, and `mergeBodiesAround` decides what happens to
 * the bodies that flanked it: fragments that already agree on Ø fuse back into the one body they
 * were split from, while flanks at DIFFERENT diameters are two distinct authored sections and
 * must both survive untouched — fusing them would invent a diameter nobody typed. The remove
 * cases here drive the real [ShaftViewModel] so the merge rule is exercised where it actually
 * runs (`BodySplitMergeTest` covers the pure transform).
 *
 * Recovery from a delete flows through the general session undo/redo; the `delete + undoEdit`
 * cases at the bottom drive the real [SessionHistory] + [EditState] the ViewModel records
 * directly, since the recorder — not the delete — is what they are about.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelRemoveTest {

    private fun vm(oalMm: Float) =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
            .also { it.onSetOverallLengthMm(oalMm) }

    private fun spans(spec: ShaftSpec) =
        spec.bodies.map { it.startFromAftMm to it.lengthMm }.toSet()

    @Test
    fun `removing a taper fuses equal-diameter flanking bodies into one`() {
        val vm = vm(600f)
        vm.addBodyAt(startMm = 0f, lengthMm = 200f, diaMm = 60f)
        vm.addBodyAt(startMm = 350f, lengthMm = 250f, diaMm = 60f)
        vm.addTaperAt(startMm = 200f, lengthMm = 150f, startDiaMm = 60f, endDiaMm = 60f)

        vm.removeTaper(vm.spec.value.tapers.single().id)

        val s = vm.spec.value
        assertTrue("the taper is gone", s.tapers.isEmpty())
        assertEquals("the two flanks fused", 1, s.bodies.size)
        assertEquals("the merged body spans the freed gap",
            setOf(0f to 600f), spans(s))
    }

    @Test
    fun `removing a taper leaves unequal-diameter flanking bodies untouched`() {
        val vm = vm(600f)
        vm.addBodyAt(startMm = 0f, lengthMm = 200f, diaMm = 60f)
        vm.addBodyAt(startMm = 350f, lengthMm = 250f, diaMm = 90f)
        vm.addTaperAt(startMm = 200f, lengthMm = 150f, startDiaMm = 60f, endDiaMm = 90f)

        vm.removeTaper(vm.spec.value.tapers.single().id)

        val s = vm.spec.value
        assertTrue("the taper is gone", s.tapers.isEmpty())
        assertEquals("both authored sections survive", 2, s.bodies.size)
        assertEquals("neither span moved",
            setOf(0f to 200f, 350f to 250f), spans(s))
        assertEquals("neither diameter was invented over",
            setOf(60f, 90f), s.bodies.map { it.diaMm }.toSet())
    }

    // ── Delete-undo recovery (via general session undo) ──────────

    private fun editState(spec: ShaftSpec) = EditState(
        spec = spec,
        wearRecord = WearRecord(),
        runoutReadings = RunoutReadings(),
        runoutStationPlacements = RunoutStationPlacements(),
        stationCountOverrides = emptyMap(),
        undercutRecord = UndercutRecord(),
    )

    @Test
    fun `deleting a body then undoEdit restores the body`() {
        val body1 = Body(id = "b1", startFromAftMm = 0f,   lengthMm = 100f, diaMm = 50f)
        val body2 = Body(id = "b2", startFromAftMm = 100f, lengthMm = 100f, diaMm = 50f)
        val before = editState(ShaftSpec(bodies = listOf(body1, body2)))

        // removeBody("b1"): the spec drops b1; its carousel row goes with it.
        val after = editState(before.spec.copy(bodies = listOf(body2)))

        val h = SessionHistory<EditState>()
        h.record(before, 1_000)      // recorder seeds pre-delete state
        h.record(after, 2_000)       // delete is its own step (gap past window)

        val restored = h.undo(after)!!
        assertEquals("body restored in spec", 2, restored.spec.bodies.size)
        assertTrue("deleted body id back in spec", restored.spec.bodies.any { it.id == "b1" })
        assertEquals("bodies restored in their stored sequence",
            listOf("b1", "b2"), restored.spec.bodies.map { it.id })
    }

    @Test
    fun `deleting a taper then undoEdit restores the taper`() {
        val body  = Body(id = "b1", startFromAftMm = 0f,   lengthMm = 100f, diaMm = 50f)
        val taper = Taper(id = "t1", startFromAftMm = 100f, lengthMm = 50f, startDiaMm = 50f, endDiaMm = 30f)
        val before = editState(ShaftSpec(bodies = listOf(body), tapers = listOf(taper)))

        val after = editState(before.spec.copy(tapers = emptyList()))

        val h = SessionHistory<EditState>()
        h.record(before, 1_000)
        h.record(after, 2_000)

        val restored = h.undo(after)!!
        assertEquals("taper restored", 1, restored.spec.tapers.size)
        assertEquals("t1", restored.spec.tapers.first().id)
        assertEquals("taper restored at its stored span", 100f,
            restored.spec.tapers.first().startFromAftMm, 0.001f)
    }
}
