package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addBodyAt
import com.android.shaftschematic.ui.viewmodel.updateBody
import com.android.shaftschematic.ui.viewmodel.updateBodyAuthoredReference
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The explicit body card's "Measure From: AFT | FWD" chips (on-device request: an explicit body
 * sits at an exact point, often measured off the coupling end) — the liner card's row. Hosts the
 * real [BodyPagerCard] over the real VM: a chip tap writes the reference, the Start field shows
 * and accepts the distance from the chosen end and commits the PHYSICAL start, a Length commit
 * under FWD keeps the FWD end, and the AUTO card carries no chips.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class BodyReferenceCardTest {

    @get:Rule
    val rule = createComposeRule()

    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun hostCard(vm: ShaftViewModel, pick: (ShaftSpec, List<ResolvedBody>) -> ResolvedBody) {
        rule.setContent {
            MaterialTheme {
                val spec by vm.spec.collectAsState()
                val bodies = resolveComponents(spec).filterIsInstance<ResolvedBody>()
                val run = pick(spec, bodies)
                val explicitIndex = spec.bodies.indexOfFirst { it.id == run.id }.takeIf { it >= 0 }
                BodyPagerCard(
                    component = run,
                    explicitIndex = explicitIndex,
                    spec = spec,
                    unit = UnitSystem.MILLIMETERS,
                    physicalIndex = 0,
                    outerPaddingHorizontal = 0.dp,
                    showComponentDebugLabels = false,
                    bodyTitleById = emptyMap(),
                    f1 = { it.toString() },
                    onAddBody = { _, _, _ -> },
                    onSetAutoSectionDia = { _, _, _ -> },
                    onSetAllAutoSectionsDia = {},
                    autoSectionCount = 1,
                    onSetAutoBlend = { _, _, _, _, _, _, _, _ -> },
                    onSetShowAutoBodyDia = {},
                    onUpdateBody = { i, s, l, d -> vm.updateBody(i, s, l, d) },
                    onUpdateBodyReference = { i, r -> vm.updateBodyAuthoredReference(i, r) },
                    onUpdateBodyShowDia = { _, _ -> },
                    onUpdateBodyShowLabel = { _, _ -> },
                    onUpdateBodyCompressOnDrawing = { _, _ -> },
                    onUpdateBodyBlend = { _, _, _, _, _, _, _, _, _, _ -> },
                    onUpdateBodyLabel = { _, _ -> },
                    onUpdateBodyKeyway = { _, _, _, _, _, _, _ -> },
                    onSetKeyways180Apart = {},
                    onSetKeyways90Apart = {},
                    onSetKeyways90Cw = {},
                    onRemoveBody = {},
                    collidingComponentIds = emptySet(),
                    perComponentUnitsEnabled = false,
                    unitOverrides = emptyMap(),
                    onSetComponentUnit = { _, _ -> },
                    onSetKeywayUnit = { _, _ -> },
                )
            }
        }
        rule.waitForIdle()
    }

    /** OAL 1000 with one explicit body at 700..1000 — flush with the FWD end. */
    private fun vmWithBody(): ShaftViewModel = vm().also {
        it.onSetOverallLengthMm(1000f)
        it.addBodyAt(startMm = 700f, lengthMm = 300f, diaMm = 150f)
    }

    private fun field(label: String) = rule.onNode(hasText(label, substring = true) and hasSetTextAction())

    private fun commit(label: String, text: String) {
        field(label).performTextReplacement(text)
        field(label).performImeAction()
        rule.waitForIdle()
    }

    @Test
    fun `the explicit card shows the chips and a FWD tap writes the reference`() {
        val vm = vmWithBody()
        hostCard(vm) { spec, bodies -> bodies.single { it.id == spec.bodies.single().id } }

        rule.onNodeWithTag("body_ref_aft").assertIsSelected()
        rule.onNodeWithTag("body_ref_fwd").assertIsNotSelected()
        field("Start from AFT").assertExists()

        rule.onNodeWithTag("body_ref_fwd").performClick()
        rule.waitForIdle()

        val b = vm.spec.value.bodies.single()
        assertEquals(LinerAuthoredReference.FWD, b.authoredReference)
        assertEquals("a switch never moves the body", 700f, b.startFromAftMm, 0f)
        rule.onNodeWithTag("body_ref_fwd").assertIsSelected()
        field("Start from FWD").assertExists()
    }

    @Test
    fun `a Start typed under FWD commits the physical start`() {
        val vm = vmWithBody()
        vm.updateBodyAuthoredReference(0, LinerAuthoredReference.FWD)
        hostCard(vm) { spec, bodies -> bodies.single { it.id == spec.bodies.single().id } }

        commit("Start from FWD", "50")

        // 50 from the FWD face of a 1000 shaft, 300 long → starts at 650.
        val b = vm.spec.value.bodies.single()
        assertEquals(650f, b.startFromAftMm, 1e-3f)
        assertEquals(300f, b.lengthMm, 0f)
    }

    @Test
    fun `a Length typed under FWD keeps the FWD end`() {
        val vm = vmWithBody()
        vm.updateBodyAuthoredReference(0, LinerAuthoredReference.FWD)
        hostCard(vm) { spec, bodies -> bodies.single { it.id == spec.bodies.single().id } }

        commit("Length (", "200")

        val b = vm.spec.value.bodies.single()
        assertEquals(200f, b.lengthMm, 0f)
        assertEquals("FWD end stays at the FWD face", 1000f, b.startFromAftMm + b.lengthMm, 1e-3f)
    }

    @Test
    fun `a Length typed under AFT keeps the start`() {
        val vm = vmWithBody()
        hostCard(vm) { spec, bodies -> bodies.single { it.id == spec.bodies.single().id } }

        commit("Length (", "200")

        val b = vm.spec.value.bodies.single()
        assertEquals(700f, b.startFromAftMm, 0f)
        assertEquals(200f, b.lengthMm, 0f)
    }

    @Test
    fun `the auto card carries no chips`() {
        val vm = vm().also { it.onSetOverallLengthMm(1000f); it.setAutoBodyDiaMm(150f) }
        hostCard(vm) { _, bodies -> bodies.single { it.source == ResolvedComponentSource.AUTO } }

        assertTrue(rule.onAllNodesWithTag("body_ref_aft").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("body_ref_fwd").fetchSemanticsNodes().isEmpty())
    }
}
