package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.ui.input.taperPhysStartForNewLength
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Component updates move ONLY the component they target.
 *
 * A stored position is a user input, so nothing may shift it but an explicit edit aimed at it:
 * no snap to a neighbour's face, no forward cascade, no re-derivation of the shaft around it.
 * These drive the REAL `updateBody` / `updateTaper` / `updateThread` / `updateLiner` on a
 * Robolectric-hosted [ShaftViewModel] and read the resulting `spec` — a mirror of the update
 * arithmetic would pass whatever a reintroduced cascade did to the ViewModel.
 *
 * Every case pins three things: the edited component's values land **verbatim**, every other
 * component's span is byte-identical, and the shaft's own `overallLengthMm` is untouched —
 * nothing an edit does to a component grows or shrinks the shaft around it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelUpdateTest {

    private fun vm(oalMm: Float) =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
            .also { it.onSetOverallLengthMm(oalMm) }

    private fun linerIndexAt(vm: ShaftViewModel, startMm: Float) =
        vm.spec.value.liners.indexOfFirst { kotlin.math.abs(it.startFromAftMm - startMm) < 0.001f }

    private fun assertOal(expected: Float, vm: ShaftViewModel) =
        assertEquals("an update must never move the shaft's own length",
            expected, vm.spec.value.overallLengthMm, 0.001f)

    // ── Liner ────────────────────────────────────────────────────────────────

    @Test
    fun `updating liner start does not move subsequent body`() {
        val vm = vm(300f)
        vm.addBodyAt(startMm = 100f, lengthMm = 200f, diaMm = 50f)
        vm.addLinerAt(startMm = 0f, lengthMm = 100f, odMm = 50f)

        vm.updateLiner(0, startMm = 10f, lengthMm = 80f, odMm = 50f)

        val s = vm.spec.value
        assertEquals("body start must not change", 100f, s.bodies[0].startFromAftMm, 0.001f)
        assertEquals("body length must not change", 200f, s.bodies[0].lengthMm, 0.001f)
        assertEquals("liner start updated", 10f, s.liners[0].startFromAftMm, 0.001f)
        assertEquals("liner length updated", 80f, s.liners[0].lengthMm, 0.001f)
        assertOal(300f, vm)
    }

    @Test
    fun `updating liner length does not move preceding body`() {
        val vm = vm(200f)
        vm.addBodyAt(startMm = 0f, lengthMm = 100f, diaMm = 50f)
        vm.addLinerAt(startMm = 100f, lengthMm = 50f, odMm = 50f)

        vm.updateLiner(0, startMm = 100f, lengthMm = 80f, odMm = 50f)

        val s = vm.spec.value
        assertEquals("body start must not change", 0f, s.bodies[0].startFromAftMm, 0.001f)
        assertEquals("body length must not change", 100f, s.bodies[0].lengthMm, 0.001f)
        assertEquals("liner length updated", 80f, s.liners[0].lengthMm, 0.001f)
        assertOal(200f, vm)
    }

    @Test
    fun `updating liner does not move a taper fwd of it`() {
        val vm = vm(300f)
        vm.addLinerAt(startMm = 0f, lengthMm = 100f, odMm = 50f)
        vm.addTaperAt(startMm = 150f, lengthMm = 50f, startDiaMm = 50f, endDiaMm = 40f)

        vm.updateLiner(0, startMm = 0f, lengthMm = 120f, odMm = 50f)

        assertEquals("taper start must not change", 150f, vm.spec.value.tapers[0].startFromAftMm, 0.001f)
        assertOal(300f, vm)
    }

    // ── Body ─────────────────────────────────────────────────────────────────

    @Test
    fun `updating body start does not move subsequent liner`() {
        val vm = vm(200f)
        vm.addBodyAt(startMm = 0f, lengthMm = 100f, diaMm = 50f)
        vm.addLinerAt(startMm = 100f, lengthMm = 60f, odMm = 50f)

        // The start genuinely moves: 0 → 20.
        vm.updateBody(0, startMm = 20f, lengthMm = 80f, diaMm = 50f)

        val s = vm.spec.value
        assertEquals("body start updated", 20f, s.bodies[0].startFromAftMm, 0.001f)
        assertEquals("body length updated", 80f, s.bodies[0].lengthMm, 0.001f)
        assertEquals("liner start must not change", 100f, s.liners[0].startFromAftMm, 0.001f)
        assertEquals("liner length must not change", 60f, s.liners[0].lengthMm, 0.001f)
        assertOal(200f, vm)
    }

    @Test
    fun `updating body does not move a taper aft of it`() {
        val vm = vm(300f)
        vm.addTaperAt(startMm = 0f, lengthMm = 100f, startDiaMm = 60f, endDiaMm = 50f)
        vm.addBodyAt(startMm = 100f, lengthMm = 200f, diaMm = 50f)

        vm.updateBody(0, startMm = 100f, lengthMm = 150f, diaMm = 50f)

        val s = vm.spec.value
        assertEquals("taper start must not change", 0f, s.tapers[0].startFromAftMm, 0.001f)
        assertEquals("taper length must not change", 100f, s.tapers[0].lengthMm, 0.001f)
        assertOal(300f, vm)
    }

    /** Golden rule: a typed start is stored exactly as typed, sign included — nothing snaps it. */
    @Test
    fun `a negative body start commits verbatim`() {
        val vm = vm(300f)
        vm.addBodyAt(startMm = 0f, lengthMm = 100f, diaMm = 50f)
        vm.addLinerAt(startMm = 150f, lengthMm = 50f, odMm = 50f)

        vm.updateBody(0, startMm = -3f, lengthMm = 100f, diaMm = 50f)

        val s = vm.spec.value
        assertEquals("typed start stored verbatim", -3f, s.bodies[0].startFromAftMm, 0f)
        assertEquals("liner untouched", 150f, s.liners[0].startFromAftMm, 0.001f)
        assertOal(300f, vm)
    }

    /** An oversized component is a legal state: the shaft does not stretch to swallow it. */
    @Test
    fun `an edit running a body past the end leaves the OAL alone`() {
        val vm = vm(300f)
        vm.addBodyAt(startMm = 0f, lengthMm = 100f, diaMm = 50f)

        vm.updateBody(0, startMm = 0f, lengthMm = 900f, diaMm = 50f)

        assertEquals("length stored verbatim", 900f, vm.spec.value.bodies[0].lengthMm, 0.001f)
        assertOal(300f, vm)
    }

    // ── Taper ────────────────────────────────────────────────────────────────

    @Test
    fun `updating taper does not move fwd body`() {
        val vm = vm(300f)
        vm.addTaperAt(startMm = 0f, lengthMm = 100f, startDiaMm = 60f, endDiaMm = 50f)
        vm.addBodyAt(startMm = 100f, lengthMm = 200f, diaMm = 50f)

        vm.updateTaper(0, startMm = 0f, lengthMm = 120f, startDiaMm = 60f, endDiaMm = 50f)

        val s = vm.spec.value
        assertEquals("taper length updated", 120f, s.tapers[0].lengthMm, 0.001f)
        assertEquals("body start must not change", 100f, s.bodies[0].startFromAftMm, 0.001f)
        assertEquals("body length must not change", 200f, s.bodies[0].lengthMm, 0.001f)
        assertOal(300f, vm)
    }

    @Test
    fun `updating taper does not move aft liner`() {
        val vm = vm(200f)
        vm.addLinerAt(startMm = 0f, lengthMm = 50f, odMm = 50f)
        vm.addTaperAt(startMm = 50f, lengthMm = 100f, startDiaMm = 50f, endDiaMm = 40f)

        vm.updateTaper(0, startMm = 60f, lengthMm = 100f, startDiaMm = 50f, endDiaMm = 40f)

        val s = vm.spec.value
        assertEquals("taper start updated", 60f, s.tapers[0].startFromAftMm, 0.001f)
        assertEquals("liner start must not change", 0f, s.liners[0].startFromAftMm, 0.001f)
        assertEquals("liner length must not change", 50f, s.liners[0].lengthMm, 0.001f)
        assertOal(200f, vm)
    }

    // ── Thread ───────────────────────────────────────────────────────────────

    @Test
    fun `updating in-shaft thread does not move adjacent body`() {
        val vm = vm(250f)
        vm.addBodyAt(startMm = 50f, lengthMm = 200f, diaMm = 50f)
        vm.addThreadAt(startMm = 0f, lengthMm = 50f, majorDiaMm = 45f, pitchMm = 2f)

        vm.updateThread(0, startMm = 0f, lengthMm = 60f, majorDiaMm = 45f, pitchMm = 2f)

        val s = vm.spec.value
        assertEquals("thread start kept as authored", 0f, s.threads[0].startFromAftMm, 0.001f)
        assertEquals("thread length updated", 60f, s.threads[0].lengthMm, 0.001f)
        assertEquals("body start must not change", 50f, s.bodies[0].startFromAftMm, 0.001f)
        assertEquals("body length must not change", 200f, s.bodies[0].lengthMm, 0.001f)
        assertOal(250f, vm)
    }

    @Test
    fun `updating excluded aft thread does not move any body`() {
        val vm = vm(300f)
        vm.addBodyAt(startMm = 0f, lengthMm = 200f, diaMm = 50f)
        vm.addThreadAt(
            startMm = -50f, lengthMm = 50f, majorDiaMm = 45f, pitchMm = 2f,
            excludeFromOAL = true, isAftEnd = true,
        )

        vm.updateThread(0, startMm = -50f, lengthMm = 60f, majorDiaMm = 45f, pitchMm = 2f)

        val s = vm.spec.value
        assertEquals("body start must not change", 0f, s.bodies[0].startFromAftMm, 0.001f)
        assertEquals("body length must not change", 200f, s.bodies[0].lengthMm, 0.001f)
        assertEquals("excluded thread start derived from its own length",
            -60f, s.threads[0].startFromAftMm, 0.001f)
        assertOal(300f, vm)
    }

    // ── Multi-component stability ─────────────────────────────────────────────

    @Test
    fun `updating one of three liners leaves the other two untouched`() {
        val vm = vm(400f)
        vm.addLinerAt(startMm = 0f, lengthMm = 100f, odMm = 50f)
        vm.addLinerAt(startMm = 150f, lengthMm = 100f, odMm = 50f)
        vm.addLinerAt(startMm = 300f, lengthMm = 100f, odMm = 50f)

        val middle = linerIndexAt(vm, 150f)
        vm.updateLiner(middle, startMm = 160f, lengthMm = 90f, odMm = 50f)

        val liners = vm.spec.value.liners
        assertEquals("middle liner start updated", 160f, liners[middle].startFromAftMm, 0.001f)
        assertEquals("middle liner length updated", 90f, liners[middle].lengthMm, 0.001f)
        val others = liners.filterIndexed { i, _ -> i != middle }
        assertEquals("the other two keep their spans",
            setOf(0f to 100f, 300f to 100f),
            others.map { it.startFromAftMm to it.lengthMm }.toSet())
        assertOal(400f, vm)
    }

    @Test
    fun `updating aft liner in mixed spec leaves taper body and fwd liner positions unchanged`() {
        val vm = vm(300f)
        vm.addBodyAt(startMm = 130f, lengthMm = 100f, diaMm = 40f)
        vm.addTaperAt(startMm = 80f, lengthMm = 50f, startDiaMm = 50f, endDiaMm = 40f)
        vm.addLinerAt(startMm = 0f, lengthMm = 80f, odMm = 50f)
        vm.addLinerAt(startMm = 230f, lengthMm = 70f, odMm = 40f)

        val aft = linerIndexAt(vm, 0f)
        val fwd = linerIndexAt(vm, 230f)
        // Extend the AFT liner by 20mm — nothing else may move.
        vm.updateLiner(aft, startMm = 0f, lengthMm = 100f, odMm = 50f)

        val s = vm.spec.value
        assertEquals("aft liner extended", 100f, s.liners[aft].lengthMm, 0.001f)
        assertEquals("taper start unchanged", 80f, s.tapers[0].startFromAftMm, 0.001f)
        assertEquals("body start unchanged", 130f, s.bodies[0].startFromAftMm, 0.001f)
        assertEquals("fwd liner start unchanged", 230f, s.liners[fwd].startFromAftMm, 0.001f)
        assertOal(300f, vm)
    }

    // ── FWD-reference taper length edits ──────────────────────────────────────
    // The card re-anchors a FWD-referenced taper through [taperPhysStartForNewLength] and hands
    // the ViewModel the resulting canonical start. These drive that same pair.

    @Test
    fun `fwd-ref taper length change does not move adjacent aft body`() {
        val vm = vm(500f)
        vm.addBodyAt(startMm = 0f, lengthMm = 100f, diaMm = 60f)
        vm.addTaperAt(
            startMm = 200f, lengthMm = 100f, startDiaMm = 60f, endDiaMm = 50f,
            reference = LinerAuthoredReference.FWD,
        )

        val newLen = 150f
        val newStart = taperPhysStartForNewLength(vm.spec.value.tapers[0], newLen, 500f)
        vm.updateTaper(0, startMm = newStart, lengthMm = newLen, startDiaMm = 60f, endDiaMm = 50f)

        val s = vm.spec.value
        assertEquals("body start must not change", 0f, s.bodies[0].startFromAftMm, 0.001f)
        assertEquals("body length must not change", 100f, s.bodies[0].lengthMm, 0.001f)
        assertEquals("taper start re-anchored", 150f, s.tapers[0].startFromAftMm, 0.001f)
        assertEquals("taper length updated", newLen, s.tapers[0].lengthMm, 0.001f)
        assertOal(500f, vm)
    }

    @Test
    fun `fwd-ref taper length change does not move adjacent fwd liner`() {
        val vm = vm(500f)
        vm.addLinerAt(startMm = 300f, lengthMm = 100f, odMm = 50f)
        vm.addTaperAt(
            startMm = 100f, lengthMm = 100f, startDiaMm = 60f, endDiaMm = 50f,
            reference = LinerAuthoredReference.FWD,
        )

        val newLen = 50f
        val newStart = taperPhysStartForNewLength(vm.spec.value.tapers[0], newLen, 500f)
        vm.updateTaper(0, startMm = newStart, lengthMm = newLen, startDiaMm = 60f, endDiaMm = 50f)

        val s = vm.spec.value
        assertEquals("liner start must not change", 300f, s.liners[0].startFromAftMm, 0.001f)
        assertEquals("liner length must not change", 100f, s.liners[0].lengthMm, 0.001f)
        assertOal(500f, vm)
    }

    @Test
    fun `fwd-ref taper fwd end is unchanged after length update`() {
        val vm = vm(600f)
        vm.addTaperAt(
            startMm = 300f, lengthMm = 200f, startDiaMm = 60f, endDiaMm = 50f,
            reference = LinerAuthoredReference.FWD,
        )
        val originalFwdEnd = 500f

        val newLen = 120f
        val newStart = taperPhysStartForNewLength(vm.spec.value.tapers[0], newLen, 600f)
        vm.updateTaper(0, startMm = newStart, lengthMm = newLen, startDiaMm = 60f, endDiaMm = 50f)

        val t = vm.spec.value.tapers[0]
        assertEquals("FWD end of taper must be preserved",
            originalFwdEnd, t.startFromAftMm + t.lengthMm, 0.001f)
        assertOal(600f, vm)
    }

    @Test
    fun `aft-ref taper fwd end changes when length changes (contrast to fwd-ref)`() {
        // An AFT anchor keeps startFromAftMm fixed, so the FWD end moves — the correct
        // AFT-ref behaviour, and the contrast that makes the FWD cases above meaningful.
        val vm = vm(500f)
        vm.addTaperAt(
            startMm = 100f, lengthMm = 100f, startDiaMm = 60f, endDiaMm = 50f,
            reference = LinerAuthoredReference.AFT,
        )

        val newLen = 200f
        val newStart = taperPhysStartForNewLength(vm.spec.value.tapers[0], newLen, 500f)
        vm.updateTaper(0, startMm = newStart, lengthMm = newLen, startDiaMm = 60f, endDiaMm = 50f)

        val t = vm.spec.value.tapers[0]
        assertEquals("AFT start unchanged", 100f, t.startFromAftMm, 0.001f)
        assertEquals("FWD end moved to 300mm", 300f, t.startFromAftMm + t.lengthMm, 0.001f)
        assertOal(500f, vm)
    }
}
