package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.AutoDiaOverride
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.withAutoSectionDia
import com.android.shaftschematic.ui.screen.ADD_DIALOG_DIA_FALLBACK_MM
import com.android.shaftschematic.ui.screen.addDialogBodyDiaMm
import com.android.shaftschematic.ui.screen.addDialogLastDiaMm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The auto-section Ø setters.
 *
 * The section upsert is exercised through the pure spec transform `setAutoSectionDiaMm`
 * delegates to ([withAutoSectionDia]) — the setter carries no logic of its own. The "All auto
 * sections" setter, and the prune seam the component add/remove mutators apply, drive the REAL
 * [ShaftViewModel], since what they promise (overrides cleared or dropped, Undo bringing them
 * back, a resize leaving them alone) only exists where the history and the mutators run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelAutoSectionDiaTest {

    // Mirrors ShaftViewModel.setAutoSectionDiaMm.
    private fun setAutoSectionDiaMm(spec: ShaftSpec, startMm: Float, endMm: Float, valueMm: Float) =
        spec.withAutoSectionDia(startMm, endMm, valueMm)

    @Test
    fun `a set anchors the value at the span midpoint`() {
        val result = setAutoSectionDiaMm(ShaftSpec(overallLengthMm = 1000f), 0f, 400f, 150f)

        assertEquals(listOf(AutoDiaOverride(anchorMm = 200f, diaMm = 150f)), result.autoDiaOverrides)
    }

    @Test
    fun `a second set replaces only the anchors inside that span`() {
        val start = ShaftSpec(
            overallLengthMm = 1000f,
            autoDiaOverrides = listOf(
                AutoDiaOverride(anchorMm = 200f, diaMm = 150f),
                AutoDiaOverride(anchorMm = 800f, diaMm = 160f),
            ),
        )

        val result = setAutoSectionDiaMm(start, 0f, 400f, 170f)

        assertEquals(2, result.autoDiaOverrides.size)
        assertTrue(result.autoDiaOverrides.contains(AutoDiaOverride(anchorMm = 800f, diaMm = 160f)))
        assertTrue(result.autoDiaOverrides.contains(AutoDiaOverride(anchorMm = 200f, diaMm = 170f)))
    }

    @Test
    fun `a span holding several anchors keeps exactly one after a set`() {
        val start = ShaftSpec(
            autoDiaOverrides = listOf(
                AutoDiaOverride(anchorMm = 100f, diaMm = 150f),
                AutoDiaOverride(anchorMm = 300f, diaMm = 160f),
                AutoDiaOverride(anchorMm = 800f, diaMm = 170f),
            ),
        )

        val result = setAutoSectionDiaMm(start, 0f, 400f, 180f)

        assertEquals(
            listOf(
                AutoDiaOverride(anchorMm = 800f, diaMm = 170f),
                AutoDiaOverride(anchorMm = 200f, diaMm = 180f),
            ),
            result.autoDiaOverrides,
        )
    }

    @Test
    fun `clearing removes the span's anchors without adding one`() {
        val start = ShaftSpec(
            autoDiaOverrides = listOf(
                AutoDiaOverride(anchorMm = 200f, diaMm = 150f),
                AutoDiaOverride(anchorMm = 800f, diaMm = 160f),
            ),
        )

        val cleared = setAutoSectionDiaMm(start, 0f, 400f, 0f)

        assertEquals(listOf(AutoDiaOverride(anchorMm = 800f, diaMm = 160f)), cleared.autoDiaOverrides)
        assertEquals(cleared.autoDiaOverrides, setAutoSectionDiaMm(start, 0f, 400f, -5f).autoDiaOverrides)
    }

    @Test
    fun `clearing an untouched span is a no-op`() {
        val start = ShaftSpec(autoDiaOverrides = listOf(AutoDiaOverride(anchorMm = 800f, diaMm = 160f)))

        assertSame(start, setAutoSectionDiaMm(start, 0f, 400f, 0f))
    }

    @Test
    fun `the typed diameter is stored verbatim`() {
        // Golden rule: a typed measurement is never rounded, snapped, or "helpfully" adjusted.
        val result = setAutoSectionDiaMm(ShaftSpec(), 0f, 400f, 101.6001f)

        assertEquals(101.6001f, result.autoDiaOverrides.single().diaMm)
    }

    @Test
    fun `the anchor interval is half-open at the fwd end`() {
        // A span's fwd boundary is the next span's start; an anchor sitting exactly on it
        // belongs to the fwd span, so setting the aft span must leave it alone.
        val start = ShaftSpec(autoDiaOverrides = listOf(AutoDiaOverride(anchorMm = 400f, diaMm = 160f)))

        val result = setAutoSectionDiaMm(start, 0f, 400f, 150f)

        assertEquals(2, result.autoDiaOverrides.size)
        assertTrue(result.autoDiaOverrides.contains(AutoDiaOverride(anchorMm = 400f, diaMm = 160f)))
    }

    // ── Real ViewModel: "All auto sections" and the add/remove prune seam ─────────

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Past the coalescing window, so the next edit is its own history step. */
    private fun nextStep() {
        idle()
        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)
    }

    /** OAL 1000 with a liner at 400..600 — auto sections 0..400 and 600..1000. */
    private fun twoSectionVm(): ShaftViewModel {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        vm.onSetOverallLengthMm(1000f)
        vm.addLinerAt(startMm = 400f, lengthMm = 200f, odMm = 200f)
        idle()
        return vm
    }

    @Test
    fun `all auto sections sets the shaft-wide value, clears every override, and undoes`() {
        val vm = twoSectionVm()
        vm.setAutoSectionDiaMm(600f, 1000f, 150f)
        nextStep()
        val before = vm.spec.value
        assertEquals(1, before.autoDiaOverrides.size)

        vm.setAutoBodyDiaMm(120f)
        idle()

        assertEquals(120f, vm.spec.value.autoBodyDiaMm)
        assertTrue("no section keeps a value of its own", vm.spec.value.autoDiaOverrides.isEmpty())

        vm.undoEdit()
        idle()

        assertEquals(before.autoBodyDiaMm, vm.spec.value.autoBodyDiaMm)
        assertEquals(before.autoDiaOverrides, vm.spec.value.autoDiaOverrides)
    }

    /** The Add Body dialog's Ø when nothing else names one — its own 25 mm placeholder. */
    private val placeholderDiaMm = 25f

    @Test
    fun `the add body dialog opens at the bare-shaft diameter the prompt set`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        vm.onSetOverallLengthMm(9000f)
        vm.setAutoBodyDiaMm(203.2f)
        idle()

        assertEquals(203.2f, addDialogBodyDiaMm(vm.spec.value, placeholderDiaMm), 0f)
    }

    @Test
    fun `an added body's diameter outranks a later bare-shaft diameter`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        vm.onSetOverallLengthMm(9000f)
        vm.addBodyAt(startMm = 0f, lengthMm = 1000f, diaMm = 150f)
        vm.setAutoBodyDiaMm(203.2f)
        idle()

        assertEquals(150f, addDialogBodyDiaMm(vm.spec.value, placeholderDiaMm), 0f)
    }

    @Test
    fun `with neither a body nor a bare-shaft diameter the dialog keeps its fallback`() {
        assertEquals(placeholderDiaMm, addDialogBodyDiaMm(ShaftSpec(overallLengthMm = 1000f), placeholderDiaMm), 0f)
    }

    @Test
    fun `the taper and thread dialogs seed from the bare-shaft diameter too`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        vm.onSetOverallLengthMm(9000f)
        vm.setAutoBodyDiaMm(203.2f)
        idle()
        assertEquals(203.2f, addDialogLastDiaMm(vm.spec.value), 0f)
    }

    @Test
    fun `the last known diameter prefers an authored body or taper over the bare shaft`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        idle()
        vm.onSetOverallLengthMm(9000f)
        vm.setAutoBodyDiaMm(203.2f)
        vm.addBodyAt(startMm = 0f, lengthMm = 1000f, diaMm = 150f)
        idle()
        assertEquals(150f, addDialogLastDiaMm(vm.spec.value), 0f)
    }

    @Test
    fun `with nothing to copy the last known diameter is the fallback`() {
        assertEquals(ADD_DIALOG_DIA_FALLBACK_MM, addDialogLastDiaMm(ShaftSpec(overallLengthMm = 1000f)), 0f)
    }

    @Test
    fun `all auto sections ignores a non-positive value`() {
        val vm = twoSectionVm()
        val before = vm.spec.value

        vm.setAutoBodyDiaMm(0f)
        vm.setAutoBodyDiaMm(-3f)

        assertSame(before, vm.spec.value)
    }

    @Test
    fun `removing the separating liner drops the fwd section's override and undo restores it`() {
        val vm = twoSectionVm()
        vm.setAutoSectionDiaMm(600f, 1000f, 150f)
        nextStep()
        val withOverride = vm.spec.value.autoDiaOverrides
        assertEquals(listOf(AutoDiaOverride(anchorMm = 800f, diaMm = 150f)), withOverride)

        vm.removeLiner(vm.spec.value.liners.single().id)
        idle()

        assertTrue("the merged-away section's Ø died with it", vm.spec.value.autoDiaOverrides.isEmpty())

        vm.undoEdit()
        idle()

        assertEquals(withOverride, vm.spec.value.autoDiaOverrides)
    }

    @Test
    fun `lengthening a liner over the anchor through the update path keeps the override dormant`() {
        val vm = twoSectionVm()
        vm.setAutoSectionDiaMm(600f, 1000f, 150f)
        idle()

        // 400..900 now covers the 800 anchor — a resize, never a prune.
        vm.updateLiner(index = 0, startMm = 400f, lengthMm = 500f, odMm = 200f)
        idle()

        assertEquals(
            listOf(AutoDiaOverride(anchorMm = 800f, diaMm = 150f)),
            vm.spec.value.autoDiaOverrides,
        )
    }
}
