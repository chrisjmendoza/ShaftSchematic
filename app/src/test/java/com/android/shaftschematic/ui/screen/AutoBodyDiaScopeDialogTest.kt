package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addLinerAt
import com.android.shaftschematic.util.toMmOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The auto-body card's Ø scope dialog, hosted through the whole editor so the wiring is what is
 * pinned: with two auto sections a typed Ø asks "This section | All auto sections" (on-device
 * report: one section's edit changed the others), each choice writes its own scope, and a Cancel
 * applies nothing and puts the STORED value back in the box.
 *
 * The new-shaft Ø prompt is pinned by `ShaftDiaPromptTest` (its predicate) only: an
 * `AlertDialog` holding a text field never idles under Robolectric (the calculator dialogs'
 * known limitation), so it cannot be hosted here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class AutoBodyDiaScopeDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private fun host(vm: ShaftViewModel) {
        rule.setContent {
            MaterialTheme {
                ShaftEditorRoute(
                    vm = vm,
                    onNavigateHome = {},
                    onNew = {},
                    onOpen = {},
                    onSave = {},
                    onOpenSettings = {},
                    onOpenDeveloperOptions = {},
                    onExportPdf = {},
                )
            }
        }
        rule.waitForIdle()
    }

    private fun freshVm() = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())

    // ── Auto-body card Ø scope ──────────────────────────────────────────────────

    /** OAL 1000 with a liner at 400..600, the AFT auto section selected. */
    private fun twoSectionVm(): ShaftViewModel = freshVm().also { vm ->
        vm.onSetOverallLengthMm(1000f)
        vm.addLinerAt(startMm = 400f, lengthMm = 200f, odMm = 200f)
        val aftAuto = vm.resolvedComponents.value
            .filterIsInstance<ResolvedBody>()
            .first { it.source == ResolvedComponentSource.AUTO }
        vm.selectComponentById(aftAuto.id)
    }

    private fun diaField() =
        rule.onNode(hasText("Diameter (", substring = true) and hasSetTextAction())

    @Test
    fun `with two auto sections a typed Ø asks for its scope, and Cancel applies nothing`() {
        val vm = twoSectionVm()
        host(vm)
        val before = vm.spec.value
        val storedText = diaField().fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text

        diaField().performTextReplacement("7")
        diaField().performImeAction()
        rule.waitForIdle()

        rule.onAllNodesWithTag("autobody_dia_scope_dialog").assertCountEquals(1)
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()

        rule.onAllNodesWithTag("autobody_dia_scope_dialog").assertCountEquals(0)
        assertEquals("nothing applied", before, vm.spec.value)
        assertEquals(
            "the box shows the stored value again",
            storedText,
            diaField().fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
        )
    }

    @Test
    fun `This section writes one override and All auto sections writes the shaft-wide Ø`() {
        val vm = twoSectionVm()
        host(vm)
        val typedMm = toMmOrNull("7", vm.unit.value)!!

        diaField().performTextReplacement("7")
        diaField().performImeAction()
        rule.waitForIdle()
        rule.onNodeWithTag("autobody_dia_this_section").performClick()
        rule.waitForIdle()

        assertEquals(listOf(typedMm), vm.spec.value.autoDiaOverrides.map { it.diaMm })
        assertEquals(0f, vm.spec.value.autoBodyDiaMm)

        diaField().performTextReplacement("8")
        diaField().performImeAction()
        rule.waitForIdle()
        rule.onNodeWithTag("autobody_dia_all_sections").performClick()
        rule.waitForIdle()

        assertEquals(toMmOrNull("8", vm.unit.value)!!, vm.spec.value.autoBodyDiaMm)
        assertTrue(vm.spec.value.autoDiaOverrides.isEmpty())
    }

    @Test
    fun `with one auto section the typed Ø is the shaft's Ø and applies without a dialog`() {
        val vm = freshVm().also { vm ->
            vm.onSetOverallLengthMm(1000f)
            vm.selectComponentById(
                vm.resolvedComponents.value.filterIsInstance<ResolvedBody>().single().id
            )
        }
        host(vm)

        diaField().performTextReplacement("7")
        diaField().performImeAction()
        rule.waitForIdle()

        rule.onAllNodesWithTag("autobody_dia_scope_dialog").assertCountEquals(0)
        assertEquals(toMmOrNull("7", vm.unit.value)!!, vm.spec.value.autoBodyDiaMm)
        assertTrue(vm.spec.value.autoDiaOverrides.isEmpty())
    }
}
