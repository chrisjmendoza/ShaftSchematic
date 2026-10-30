package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.android.shaftschematic.ui.viewmodel.updateBodyBlend
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "At a liner: Seat | Liner OD" chips (on-device request: a body that ramps right up to the
 * liner's OD) — under the add-dialog-parity rule, so the explicit card, the auto card and
 * `AddBodyDialog` all carry the row, each only under a face set to Blend, and each tap commits
 * the flag verbatim with every other blend field passed through.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class BlendLinerTargetUiTest {

    @get:Rule
    val rule = createComposeRule()

    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())

    /**
     * Hosts the real [BodyPagerCard] (it scrolls itself) for [pick] over the VM's live spec;
     * blend commits reach the VM.
     */
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
                    unit = UnitSystem.INCHES,
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
                    onSetShowAutoBodyDia = {},
                    onUpdateBody = { _, _, _, _ -> },
                    onUpdateBodyReference = { _, _ -> },
                    onUpdateBodyShowDia = { _, _ -> },
                    onUpdateBodyShowLabel = { _, _ -> },
                    onUpdateBodyCompressOnDrawing = { _, _ -> },
                    onUpdateBodyBlend = { i, aft, fwd, p, sAft, sFwd, sAftLen, sFwdLen, odAft, odFwd ->
                        vm.updateBodyBlend(i, aft, fwd, p, sAft, sFwd, sAftLen, sFwdLen, odAft, odFwd)
                    },
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

    @Test
    fun `the explicit card shows At a liner only under a blended face and a tap commits it`() {
        val vm = vm().also {
            it.onSetOverallLengthMm(800f)
            it.addBodyAt(startMm = 0f, lengthMm = 300f, diaMm = 200f)
            it.addLinerAt(startMm = 300f, lengthMm = 400f, odMm = 240f)
        }
        hostCard(vm) { spec, bodies -> bodies.single { it.id == spec.bodies.single().id } }

        rule.onNodeWithText("Component blend").assertExists()
        assertTrue(absent("body_blend_fwd_liner_target_SEAT"))

        tap("body_blend_fwd_blend")
        rule.onNodeWithTag("body_blend_fwd_liner_target_SEAT").assertIsSelected()
        rule.onNodeWithTag("body_blend_fwd_liner_target_LINER_OD").assertIsNotSelected()
        assertTrue("the AFT face is still square", absent("body_blend_aft_liner_target_SEAT"))
        val blended = vm.spec.value.bodies.single()

        tap("body_blend_fwd_liner_target_LINER_OD")
        val b = vm.spec.value.bodies.single()
        assertTrue(b.blendFwdToLinerOd)
        assertFalse(b.blendAftToLinerOd)
        assertEquals(blended.copy(blendFwdToLinerOd = true), b)
        rule.onNodeWithTag("body_blend_fwd_liner_target_LINER_OD").assertIsSelected()

        // Square hides the row; the stored choice stays for when the face is blended again.
        tap("body_blend_fwd_square")
        assertTrue(absent("body_blend_fwd_liner_target_SEAT"))
        assertTrue(vm.spec.value.bodies.single().blendFwdToLinerOd)
    }

    @Test
    fun `the auto card carries the row and writes the anchor flag`() {
        val vm = vm().also {
            it.onSetOverallLengthMm(900f)
            it.setAutoBodyDiaMm(180f)
            it.addLinerAt(startMm = 0f, lengthMm = 300f, odMm = 220f)
            it.addLinerAt(startMm = 600f, lengthMm = 300f, odMm = 220f)
        }
        hostCard(vm) { _, bodies -> bodies.single { it.source == ResolvedComponentSource.AUTO } }

        assertTrue(absent("body_blend_aft_liner_target_SEAT"))
        tap("body_blend_aft_blend")
        rule.onNodeWithTag("body_blend_aft_liner_target_SEAT").assertIsSelected()

        tap("body_blend_aft_liner_target_LINER_OD")
        val anchor = vm.spec.value.autoBlends.autoBlendFor(300f, 600f, LinerAuthoredReference.AFT)!!
        assertTrue(anchor.toLinerOd)
        assertTrue(anchor.lengthMm > 0f)
        assertTrue(absent("body_blend_fwd_liner_target_SEAT"))
    }

    @Test
    fun `the Add Body dialog mirrors the row and submits the flag`() {
        var submitted: Pair<Boolean, Boolean>? = null
        rule.setContent {
            MaterialTheme {
                AddBodyDialog(
                    unit = UnitSystem.INCHES,
                    spec = ShaftSpec(overallLengthMm = 100f * 25.4f),
                    onSubmit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, odAft, odFwd ->
                        submitted = odAft to odFwd
                    },
                    onCancel = {},
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Component blend").assertExists()
        assertTrue(absent("body_blend_aft_liner_target_SEAT"))
        tap("body_blend_aft_blend")
        rule.onNodeWithTag("body_blend_aft_liner_target_SEAT").assertIsSelected()
        tap("body_blend_aft_liner_target_LINER_OD")
        tap("body_blend_fwd_blend")
        rule.onNodeWithTag("body_blend_fwd_liner_target_SEAT").assertIsSelected()

        rule.onNodeWithText("Add").performClick()
        rule.waitForIdle()
        assertEquals(true to false, submitted)
    }
}
