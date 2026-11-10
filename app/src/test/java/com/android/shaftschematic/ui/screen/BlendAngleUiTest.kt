package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.autoBlendFor
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addBodyAt
import com.android.shaftschematic.ui.viewmodel.addLinerAt
import com.android.shaftschematic.ui.viewmodel.setAutoBlend
import com.android.shaftschematic.ui.viewmodel.setAutoBlendAngle
import com.android.shaftschematic.ui.viewmodel.updateBodyBlend
import com.android.shaftschematic.ui.viewmodel.updateBodyBlendAngle
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Blend by: Length | Angle" (on-device request: "some blends have expressions in degrees") —
 * under the add-dialog-parity rule, so the explicit card, the auto card and `AddBodyDialog` all
 * carry the chips, the angle field, its derived-length line and the corner-radius field under the
 * same conditions, and the Shape chips hide while a face is entered by angle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class BlendAngleUiTest {

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
                    onSetAutoBlend = { s0, s1, end, len, prof, seal, sealLen, toOd ->
                        vm.setAutoBlend(s0, s1, end, len, prof, seal, sealLen, toOd)
                    },
                    onSetAutoBlendAngle = { s0, s1, end, deg, r -> vm.setAutoBlendAngle(s0, s1, end, deg, r) },
                    onSetShowAutoBodyDia = {},
                    onUpdateBody = { _, _, _, _ -> },
                    onUpdateBodyReference = { _, _ -> },
                    onUpdateBodyShowDia = { _, _ -> },
                    onUpdateBodyShowLabel = { _, _ -> },
                    onUpdateBodyCompressOnDrawing = { _, _ -> },
                    onUpdateBodyBlend = { i, aft, fwd, p, sAft, sFwd, sAftLen, sFwdLen, odAft, odFwd ->
                        vm.updateBodyBlend(i, aft, fwd, p, sAft, sFwd, sAftLen, sFwdLen, odAft, odFwd)
                    },
                    onUpdateBodyBlendAngle = { i, end, deg, r -> vm.updateBodyBlendAngle(i, end, deg, r) },
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

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun absent(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun field(label: String) = rule.onNode(hasText(label, substring = true) and hasSetTextAction())

    private fun commit(label: String, text: String) {
        field(label).performScrollTo()
        field(label).performTextReplacement(text)
        field(label).performImeAction()
        rule.waitForIdle()
    }

    /** A 240 mm liner then a 200 mm body: the body's AFT face steps down from the liner. */
    private fun vmWithLinerAndBody() = vm().also {
        it.onSetOverallLengthMm(800f)
        it.addLinerAt(startMm = 0f, lengthMm = 300f, odMm = 240f)
        it.addBodyAt(startMm = 300f, lengthMm = 500f, diaMm = 200f)
    }

    @Test
    fun `the explicit card switches a face to Angle and back without touching the length`() {
        val vm = vmWithLinerAndBody()
        hostCard(vm) { spec, bodies -> bodies.single { it.id == spec.bodies.single().id } }

        assertTrue(absent("body_blend_aft_entry_LENGTH"))
        tap("body_blend_aft_blend")
        rule.onNodeWithTag("body_blend_aft_entry_LENGTH").assertIsSelected()
        rule.onNodeWithTag("body_blend_profile_OGEE").assertExists()
        val typedLength = vm.spec.value.bodies.single().blendAftMm
        assertTrue(typedLength > 0f)

        tap("body_blend_aft_entry_ANGLE")
        var b = vm.spec.value.bodies.single()
        assertEquals(DEFAULT_BLEND_ANGLE_DEG, b.blendAftAngleDeg!!, 0f)
        assertEquals(typedLength, b.blendAftMm, 0f)
        rule.onNodeWithTag("body_blend_aft_entry_ANGLE").assertIsSelected()
        assertTrue("an angle-mode face has no Shape", absent("body_blend_profile_OGEE"))
        // Seat by default: (240 + 200) / 2 = 220 → rise 10 mm → 10 mm of cone at 45°.
        rule.onNodeWithText("Length 10 mm from the step", useUnmergedTree = true).assertExists()

        commit("Angle AFT", "30")
        b = vm.spec.value.bodies.single()
        assertEquals(30f, b.blendAftAngleDeg!!, 0f)

        commit("Corner radius AFT", "3")
        assertEquals(3f, vm.spec.value.bodies.single().blendAftRadiusMm, 0f)

        tap("body_blend_aft_entry_LENGTH")
        b = vm.spec.value.bodies.single()
        assertNull(b.blendAftAngleDeg)
        assertEquals("the typed length comes back", typedLength, b.blendAftMm, 0f)
        rule.onNodeWithTag("body_blend_profile_OGEE").assertExists()

        // Angle again restores the last angle of this card session.
        tap("body_blend_aft_entry_ANGLE")
        assertEquals(30f, vm.spec.value.bodies.single().blendAftAngleDeg!!, 0f)
    }

    @Test
    fun `an angle outside 0 to 90 is refused with its reason, never clamped`() {
        val vm = vmWithLinerAndBody()
        vm.updateBodyBlend(0, 25f, 0f, com.android.shaftschematic.model.BlendProfile.OGEE)
        vm.updateBodyBlendAngle(0, LinerAuthoredReference.AFT, 45f, 0f)
        hostCard(vm) { spec, bodies -> bodies.single { it.id == spec.bodies.single().id } }

        commit("Angle AFT", "95")
        assertEquals(45f, vm.spec.value.bodies.single().blendAftAngleDeg!!, 0f)
        rule.onNodeWithText("95 not applied — $BLEND_ANGLE_RANGE_MSG", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a face with no step says so`() {
        val vm = vm().also {
            it.onSetOverallLengthMm(800f)
            it.addBodyAt(startMm = 0f, lengthMm = 800f, diaMm = 200f)
        }
        vm.updateBodyBlend(0, 25f, 0f, com.android.shaftschematic.model.BlendProfile.OGEE)
        vm.updateBodyBlendAngle(0, LinerAuthoredReference.AFT, 45f, 0f)
        hostCard(vm) { spec, bodies -> bodies.single { it.id == spec.bodies.single().id } }
        rule.onNodeWithText(ANGLE_BLEND_NO_STEP_NOTE, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the auto card carries the entry and writes the anchor`() {
        val vm = vm().also {
            it.onSetOverallLengthMm(900f)
            it.setAutoBodyDiaMm(180f)
            it.addLinerAt(startMm = 0f, lengthMm = 300f, odMm = 220f)
            it.addLinerAt(startMm = 600f, lengthMm = 300f, odMm = 220f)
        }
        hostCard(vm) { _, bodies -> bodies.single { it.source == ResolvedComponentSource.AUTO } }

        assertTrue(absent("body_blend_aft_entry_ANGLE"))
        tap("body_blend_aft_blend")
        val length = vm.spec.value.autoBlends.autoBlendFor(300f, 600f, LinerAuthoredReference.AFT)!!.lengthMm
        tap("body_blend_aft_entry_ANGLE")
        val anchor = vm.spec.value.autoBlends.autoBlendFor(300f, 600f, LinerAuthoredReference.AFT)!!
        assertEquals(DEFAULT_BLEND_ANGLE_DEG, anchor.angleDeg!!, 0f)
        assertEquals(length, anchor.lengthMm, 0f)
        // Seat (220 + 180) / 2 = 200 → rise 10 → 10 mm.
        rule.onNodeWithText("Length 10 mm from the step", useUnmergedTree = true).assertExists()
        field("Corner radius AFT").assertExists()
    }

    @Test
    fun `the Add Body dialog mirrors the entry and submits the angle draft`() {
        var submitted: BlendAngleDraft? = null
        var submittedLen = -1f
        rule.setContent {
            MaterialTheme {
                AddBodyDialog(
                    unit = UnitSystem.MILLIMETERS,
                    spec = ShaftSpec(overallLengthMm = 1000f),
                    onSubmit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, bAft, _, _, _, _, _, _, _, _, angles ->
                        submitted = angles
                        submittedLen = bAft
                    },
                    onCancel = {},
                )
            }
        }
        rule.waitForIdle()

        assertTrue(absent("body_blend_aft_entry_LENGTH"))
        tap("body_blend_aft_blend")
        rule.onNodeWithTag("body_blend_aft_entry_LENGTH").assertIsSelected()
        rule.onNodeWithTag("body_blend_profile_OGEE").assertExists()
        tap("body_blend_aft_entry_ANGLE")
        assertTrue(absent("body_blend_profile_OGEE"))
        // A lone body on a bare shaft has no step at its AFT face.
        rule.onNodeWithText(ANGLE_BLEND_NO_STEP_NOTE, useUnmergedTree = true).assertExists()
        field("Corner radius AFT").performScrollTo()
        field("Corner radius AFT").performTextReplacement("4")
        rule.waitForIdle()

        rule.onNodeWithText("Add").performClick()
        rule.waitForIdle()
        assertNotNull(submitted)
        val draft = submitted!!
        assertEquals(DEFAULT_BLEND_ANGLE_DEG, draft.aftAngleDeg!!, 0f)
        assertEquals(4f, draft.aftRadiusMm, 1e-4f)
        assertNull(draft.fwdAngleDeg)
        assertTrue("the length text is still submitted for a switch back", submittedLen > 0f)
    }
}
