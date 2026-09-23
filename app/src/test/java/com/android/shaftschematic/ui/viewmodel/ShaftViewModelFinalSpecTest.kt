package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.WearRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The final drawing is a SECOND geometry, edited through the same mutators under an explicit
 * [SpecTarget]. What these pin: starting one copies the original with its component ids intact;
 * a FINAL edit lands only on the final and an ORIGINAL edit only on the original (the original
 * is the record of what came in — one leak either way and the before/after pair is worthless);
 * a FINAL write with no final drawing is a no-op; and every document boundary that clears the
 * drawing clears the final with it.
 *
 * These drive the REAL [ShaftViewModel] on Robolectric — a mirror of the target switch would
 * pass whatever a mutator that forgot to thread its target did. The undo cases at the bottom
 * drive the real [SessionHistory] over real [EditState]s, the shape `ShaftViewModelRemoveTest`
 * uses: the ViewModel's recorder is an async combine with wall-clock coalescing, and what these
 * are about is that the final rides the snapshot at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelFinalSpecTest {

    /** A built shaft: one body, one liner, an authored OAL. */
    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.onSetOverallLengthMm(3000f)
            it.addBodyAt(startMm = 0f, lengthMm = 1000f, diaMm = 152.4f)
            it.addLinerAt(startMm = 1200f, lengthMm = 400f, odMm = 160f)
        }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Test
    fun `startFinalSpec copies the original with its component ids`() {
        val vm = vm()

        vm.startFinalSpec()

        val f = requireNotNull(vm.finalSpec.value)
        assertEquals("the copy is structurally the original", vm.spec.value, f)
        assertEquals("liner ids are shared by construction",
            vm.spec.value.liners.map { it.id }, f.liners.map { it.id })
        assertEquals("body ids are shared by construction",
            vm.spec.value.bodies.map { it.id }, f.bodies.map { it.id })
    }

    @Test
    fun `startFinalSpec is a no-op once a final drawing exists`() {
        val vm = vm()
        vm.startFinalSpec()
        vm.updateLiner(0, startMm = 1350f, lengthMm = 500f, odMm = 160f, target = SpecTarget.FINAL)
        val edited = requireNotNull(vm.finalSpec.value)

        vm.startFinalSpec()

        assertEquals("work on the final is not silently thrown away", edited, vm.finalSpec.value)
    }

    @Test
    fun `resetFinalSpec replaces the final with a fresh copy of the original`() {
        val vm = vm()
        vm.startFinalSpec()
        vm.updateLiner(0, startMm = 1350f, lengthMm = 500f, odMm = 160f, target = SpecTarget.FINAL)

        vm.resetFinalSpec()

        assertEquals(vm.spec.value, vm.finalSpec.value)
        assertEquals("the reset copy is back at the original's liner position",
            1200f, requireNotNull(vm.finalSpec.value).liners.single().startFromAftMm, 0.001f)
    }

    @Test
    fun `discardFinalSpec drops the final drawing`() {
        val vm = vm()
        vm.startFinalSpec()

        vm.discardFinalSpec()

        assertNull(vm.finalSpec.value)
        assertEquals("the original is untouched by the discard", 3000f, vm.spec.value.overallLengthMm, 0.001f)
    }

    // ── Target isolation ─────────────────────────────────────────────────────

    @Test
    fun `a FINAL edit moves the liner on the final only`() {
        val vm = vm()
        vm.startFinalSpec()
        val originalBefore = vm.spec.value

        vm.updateLiner(0, startMm = 1350f, lengthMm = 500f, odMm = 160f, target = SpecTarget.FINAL)

        assertEquals("the original drawing is byte-identical", originalBefore, vm.spec.value)
        val f = requireNotNull(vm.finalSpec.value)
        assertEquals("the final's liner moved", 1350f, f.liners.single().startFromAftMm, 0.001f)
        assertEquals("the final's liner grew", 500f, f.liners.single().lengthMm, 0.001f)
    }

    @Test
    fun `an ORIGINAL edit leaves the final drawing alone`() {
        val vm = vm()
        vm.startFinalSpec()
        vm.updateLiner(0, startMm = 1350f, lengthMm = 500f, odMm = 160f, target = SpecTarget.FINAL)
        val finalBefore = requireNotNull(vm.finalSpec.value)

        vm.updateLiner(0, startMm = 1100f, lengthMm = 300f, odMm = 160f)

        assertEquals("the final drawing is byte-identical", finalBefore, vm.finalSpec.value)
        assertEquals("the original's liner moved", 1100f, vm.spec.value.liners.single().startFromAftMm, 0.001f)
    }

    @Test
    fun `adding and removing components on the final never touches the original`() {
        val vm = vm()
        vm.startFinalSpec()
        val originalBefore = vm.spec.value

        vm.addTaperAt(
            startMm = 2000f, lengthMm = 300f, startDiaMm = 150f, endDiaMm = 120f,
            target = SpecTarget.FINAL,
        )
        val addedId = requireNotNull(vm.finalSpec.value).tapers.single().id
        assertEquals("the original grew no taper", originalBefore, vm.spec.value)

        vm.removeTaper(addedId, target = SpecTarget.FINAL)

        assertTrue("the taper is gone from the final", requireNotNull(vm.finalSpec.value).tapers.isEmpty())
        assertEquals("and the original still never moved", originalBefore, vm.spec.value)
    }

    @Test
    fun `the OAL and auto-section setters honour the target`() {
        val vm = vm()
        vm.startFinalSpec()

        vm.onSetOverallLengthMm(3200f, target = SpecTarget.FINAL)

        assertEquals(3000f, vm.spec.value.overallLengthMm, 0.001f)
        assertEquals(3200f, requireNotNull(vm.finalSpec.value).overallLengthMm, 0.001f)
    }

    @Test
    fun `a FINAL mutation with no final drawing is a no-op`() {
        val vm = vm()
        val originalBefore = vm.spec.value

        vm.updateLiner(0, startMm = 1350f, lengthMm = 500f, odMm = 160f, target = SpecTarget.FINAL)
        vm.addBodyAt(startMm = 2000f, lengthMm = 100f, diaMm = 100f, target = SpecTarget.FINAL)
        vm.onSetOverallLengthMm(9999f, target = SpecTarget.FINAL)

        assertNull("no final drawing is conjured out of a stray edit", vm.finalSpec.value)
        assertEquals("and nothing spilled onto the original", originalBefore, vm.spec.value)
    }

    // ── Document boundaries ──────────────────────────────────────────────────

    @Test
    fun `newDocument clears the final drawing`() {
        val vm = vm()
        vm.startFinalSpec()

        vm.newDocument()

        assertNull(vm.finalSpec.value)
    }

    @Test
    fun `a template built from a document with a final carries none`() {
        val vm = vm()
        vm.startFinalSpec()
        vm.updateLiner(0, startMm = 1350f, lengthMm = 500f, odMm = 160f, target = SpecTarget.FINAL)

        val decoded = ShaftDocCodec.decode(vm.exportTemplateJson())

        assertNull("a template is the pre-job shape", decoded.finalSpec)
        assertEquals("the geometry still travels", 1, decoded.spec.liners.size)
    }

    @Test
    fun `applyTemplate clears any final drawing the session had`() {
        val vm = vm()
        val template = vm.exportTemplateJson()
        vm.startFinalSpec()

        vm.applyTemplate(template)

        assertNull(vm.finalSpec.value)
    }

    @Test
    fun `the mate export carries no final drawing`() {
        val vm = vm()
        vm.startFinalSpec()

        val decoded = ShaftDocCodec.decode(
            vm.exportMateJson(
                jobNumber = "J-2",
                customer = "Acme",
                vessel = "Tug",
                position = com.android.shaftschematic.model.ShaftPosition.STBD,
            )
        )

        assertNull("the mate is its own job", decoded.finalSpec)
    }

    @Test
    fun `the save envelope carries the final drawing and reopens with it`() {
        val vm = vm()
        vm.startFinalSpec()
        vm.updateLiner(0, startMm = 1350f, lengthMm = 500f, odMm = 160f, target = SpecTarget.FINAL)
        val raw = vm.exportJson()

        val reopened = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        reopened.importJson(raw)

        assertNotNull(reopened.finalSpec.value)
        assertEquals(1350f, requireNotNull(reopened.finalSpec.value).liners.single().startFromAftMm, 0.001f)
        assertEquals("the original came back unchanged",
            1200f, reopened.spec.value.liners.single().startFromAftMm, 0.001f)
    }

    // ── Undo ─────────────────────────────────────────────────────────────────

    private fun editState(spec: ShaftSpec, finalSpec: ShaftSpec?) = EditState(
        spec = spec,
        finalSpec = finalSpec,
        wearRecord = WearRecord(),
        runoutReadings = RunoutReadings(),
        runoutStationPlacements = RunoutStationPlacements(),
        stationCountOverrides = emptyMap(),
        undercutRecord = UndercutRecord(),
    )

    @Test
    fun `undo after starting a final drawing goes back to none`() {
        val spec = ShaftSpec(overallLengthMm = 3000f)
        val history = SessionHistory<EditState>()
        val before = editState(spec, finalSpec = null)
        val after = editState(spec, finalSpec = spec)

        history.record(before, 1_000L)
        history.record(after, 5_000L)

        assertEquals(before, history.undo(after))
    }

    @Test
    fun `undo after an edit on the final restores the pre-edit final`() {
        val spec = ShaftSpec(overallLengthMm = 3000f)
        val history = SessionHistory<EditState>()
        val before = editState(spec, finalSpec = spec)
        val after = editState(spec, finalSpec = spec.copy(overallLengthMm = 3200f))

        history.record(before, 1_000L)
        history.record(after, 5_000L)

        val restored = requireNotNull(history.undo(after))
        assertEquals("the original was never in play", spec, restored.spec)
        assertEquals("the final came back as it was", spec, restored.finalSpec)
    }
}
