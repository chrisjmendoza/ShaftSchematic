package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addBodyAt
import com.android.shaftschematic.ui.viewmodel.addLinerAt
import org.junit.Assert.assertTrue
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
        rule.onNodeWithTag(EDITOR_BAR_TITLE_TAG).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w700dp-h1000dp")
    fun `a medium window keeps the bar title`() {
        host()

        rule.onNodeWithTag(EDITOR_BAR_TITLE_TAG).assertIsDisplayed()
    }

    @Test
    fun `a compact window keeps one column and its hamburger`() {
        host()

        rule.onAllNodesWithTag(EDITOR_PANE_PREVIEW_TAG).assertCountEquals(0)
        rule.onAllNodesWithTag(EDITOR_PANE_COMPONENTS_TAG).assertCountEquals(0)
        rule.onNodeWithTag("toolbar_menu").assertIsDisplayed()
    }

    /** The phone bar's icons leave the title a couple of characters — it printed as "..". */
    @Test
    fun `a compact window prints no bar title`() {
        host()

        rule.onAllNodesWithTag(EDITOR_BAR_TITLE_TAG).assertCountEquals(0)
        rule.onNodeWithTag("toolbar_save").assertIsDisplayed()
    }

    /**
     * A portrait tablet is a taller column, not a phone: the carousel runs to the foot of the
     * screen instead of stopping at the phone's 360 dp with the card's lower fields cut off above
     * an empty quarter of the screen.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1280dp")
    fun `a medium window's carousel takes the column's remaining height`() {
        host()
        rule.waitForIdle()

        val carousel = rule.onNodeWithTag(EDITOR_MEDIUM_CAROUSEL_TAG).getBoundsInRoot()
        val height = carousel.bottom - carousel.top
        assertTrue("carousel $height should be well past the phone's $CAROUSEL_HEIGHT", height > CAROUSEL_HEIGHT + 100.dp)
    }

    @Test
    fun `a compact window keeps the phone's fixed carousel`() {
        host()

        rule.onAllNodesWithTag(EDITOR_MEDIUM_CAROUSEL_TAG).assertCountEquals(0)
    }
}
