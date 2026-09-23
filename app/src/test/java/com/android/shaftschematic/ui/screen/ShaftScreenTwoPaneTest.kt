package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
 * The editor's window-width branches, hosted through the whole container so the panes and the
 * sidebar are decided by the same window the app would read.
 *
 * What these pin is the switch itself. A phone that grew pane tags would mean the two-pane
 * branch had become the only branch; a tablet still drawing the hamburger would offer to open
 * a panel already on screen, and the editor would look like it had two navigation surfaces.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ShaftScreenTwoPaneTest {

    @get:Rule
    val rule = createComposeRule()

    /** A built shaft: one body, one liner, an authored OAL. */
    private fun vm(): ShaftViewModel =
        ShaftViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            it.onSetOverallLengthMm(3000f)
            it.addBodyAt(startMm = 0f, lengthMm = 1000f, diaMm = 152.4f)
            it.addLinerAt(startMm = 1200f, lengthMm = 400f, odMm = 160f)
        }

    private fun host() {
        val vm = vm()
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
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
    fun `an expanded window splits the editor in two and drops the hamburger`() {
        host()

        rule.onNodeWithTag(EDITOR_PANE_PREVIEW_TAG).assertIsDisplayed()
        rule.onNodeWithTag(EDITOR_PANE_COMPONENTS_TAG).assertIsDisplayed()
        rule.onAllNodesWithTag("toolbar_menu").assertCountEquals(0)
    }

    @Test
    fun `a compact window keeps one column and its hamburger`() {
        host()

        rule.onAllNodesWithTag(EDITOR_PANE_PREVIEW_TAG).assertCountEquals(0)
        rule.onAllNodesWithTag(EDITOR_PANE_COMPONENTS_TAG).assertCountEquals(0)
        rule.onNodeWithTag("toolbar_menu").assertIsDisplayed()
    }
}
