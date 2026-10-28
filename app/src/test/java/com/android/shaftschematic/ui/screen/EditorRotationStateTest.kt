package com.android.shaftschematic.ui.screen

import android.app.Application
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addBodyAt
import com.android.shaftschematic.ui.viewmodel.addLinerAt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tab state across a rotation that crosses the EXPANDED boundary — a landscape tablet (permanent
 * sidebar, two panes) turned to portrait (overlay sidebar, one column) and back.
 *
 * `rememberSaveable` keys its values by composition PATH. Content composed from a different
 * branch per width class reads none of the other branch's saved values, and the unread values are
 * saved again and come back a rotation later — a switch reading ON, OFF, ON over two rotations.
 * Both ways a width can change are pinned: in place (the same composition re-laid out) and across
 * an activity recreation (saved, torn down, restored at the new width — the app has no
 * `configChanges`, so a rotation recreates).
 *
 * The window is a landscape tablet throughout; the width class is driven through
 * [LocalConfiguration], the one thing `currentWindowWidthClass` reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
class EditorRotationStateTest {

    @get:Rule
    val rule = createComposeRule()

    /** A built shaft with one liner running past the OAL, so the spec warning banner shows. */
    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.onSetOverallLengthMm(3000f)
            it.addBodyAt(startMm = 0f, lengthMm = 1000f, diaMm = 152.4f)
            it.addLinerAt(startMm = 2800f, lengthMm = 400f, odMm = 160f)
        }

    @Composable
    private fun AtWidth(widthDp: Int, content: @Composable () -> Unit) {
        val base = LocalConfiguration.current
        val config = remember(base, widthDp) { Configuration(base).apply { screenWidthDp = widthDp } }
        CompositionLocalProvider(LocalConfiguration provides config, content = content)
    }

    @Composable
    private fun Editor(vm: ShaftViewModel) {
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

    private fun openRunoutTabAndTickBlankDraft() {
        rule.onNodeWithText(EditorTab.RUNOUT.label).performClick()
        rule.onNodeWithTag(RUNOUT_BLANK_DRAFT_TAG).performScrollTo().performClick()
        rule.onNodeWithTag(RUNOUT_BLANK_DRAFT_TAG).assertIsOn()
    }

    @Test
    fun `a tab's saved switch survives the width class changing in place`() {
        val vm = vm()
        var widthDp by mutableIntStateOf(EXPANDED_DP)
        rule.setContent { AtWidth(widthDp) { Editor(vm) } }

        openRunoutTabAndTickBlankDraft()

        widthDp = MEDIUM_DP
        rule.waitForIdle()
        rule.onNodeWithTag(RUNOUT_BLANK_DRAFT_TAG).assertIsOn()

        widthDp = EXPANDED_DP
        rule.waitForIdle()
        rule.onNodeWithTag(RUNOUT_BLANK_DRAFT_TAG).assertIsOn()
    }

    @Test
    fun `a tab's saved switch survives a recreation into the other width class and back`() {
        val vm = vm()
        // Plain, not snapshot state: the width changes only with the recreation, so the save
        // happens in the old branch and the restore composes the new one.
        var widthDp = EXPANDED_DP
        val restorer = StateRestorationTester(rule)
        restorer.setContent { AtWidth(widthDp) { Editor(vm) } }

        openRunoutTabAndTickBlankDraft()

        widthDp = MEDIUM_DP
        restorer.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag(RUNOUT_BLANK_DRAFT_TAG).assertIsOn()

        widthDp = EXPANDED_DP
        restorer.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag(RUNOUT_BLANK_DRAFT_TAG).assertIsOn()
    }

    /**
     * Inside the Schematic tab the warning banner itself moves: beside the drawing at EXPANDED,
     * in the scrolling column at MEDIUM. Its dismissal is saved state, so it must travel too.
     */
    @Test
    fun `a dismissed warning stays dismissed when the editor changes width in place`() {
        val vm = vm()
        var widthDp by mutableIntStateOf(EXPANDED_DP)
        rule.setContent { AtWidth(widthDp) { Editor(vm) } }

        rule.onAllNodesWithTag("spec_warning_banner").assertCountEquals(1)
        rule.onNodeWithTag("spec_warning_dismiss").performScrollTo().performClick()
        rule.onAllNodesWithTag("spec_warning_banner").assertCountEquals(0)

        widthDp = MEDIUM_DP
        rule.waitForIdle()
        rule.onAllNodesWithTag("spec_warning_banner").assertCountEquals(0)

        widthDp = EXPANDED_DP
        rule.waitForIdle()
        rule.onAllNodesWithTag("spec_warning_banner").assertCountEquals(0)
    }

    @Test
    fun `a dismissed warning stays dismissed across a recreation into the other width class`() {
        val vm = vm()
        var widthDp = EXPANDED_DP
        val restorer = StateRestorationTester(rule)
        restorer.setContent { AtWidth(widthDp) { Editor(vm) } }

        rule.onAllNodesWithTag("spec_warning_banner").assertCountEquals(1)
        rule.onNodeWithTag("spec_warning_dismiss").performScrollTo().performClick()

        widthDp = MEDIUM_DP
        restorer.emulateSavedInstanceStateRestore()
        rule.onAllNodesWithTag("spec_warning_banner").assertCountEquals(0)

        widthDp = EXPANDED_DP
        restorer.emulateSavedInstanceStateRestore()
        rule.onAllNodesWithTag("spec_warning_banner").assertCountEquals(0)
    }

    private companion object {
        const val EXPANDED_DP = 1280
        const val MEDIUM_DP = 800
    }
}
