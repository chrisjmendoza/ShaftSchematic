package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addBodyAt
import com.android.shaftschematic.ui.viewmodel.addLinerAt
import com.android.shaftschematic.ui.viewmodel.newDocument
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A document change resets the editor's saved UI state, including the parts composed as movable
 * content (the carousel and the warning banner). Movable content roots its `rememberSaveable`
 * path at itself, outside the editor's `key(resetNonce)`, so state saved while the editor was off
 * screen — the Open screen is a separate destination — came back into the NEXT document: the start
 * card opened scrolled to wherever the previous document's card had been, title cut off
 * (on-device QA), and a warning dismissed on one document stayed dismissed on the next.
 *
 * The editor is hosted the way the NavHost hosts a destination: inside a [rememberSaveableStateHolder]
 * provider that leaves composition (saving its state) while another screen is shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
class EditorDocumentChangeStateTest {

    @get:Rule
    val rule = createComposeRule()

    /** A built shaft with one liner running past the OAL, so the spec warning banner shows. */
    private fun ShaftViewModel.buildWarnedShaft() {
        onSetOverallLengthMm(3000f)
        addBodyAt(startMm = 0f, lengthMm = 1000f, diaMm = 152.4f)
        addLinerAt(startMm = 2800f, lengthMm = 400f, odMm = 160f)
    }

    @Test
    fun `a warning dismissed on one document shows again on the next one opened`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.buildWarnedShaft()
        var editorShown by mutableStateOf(true)
        rule.setContent {
            val holder = rememberSaveableStateHolder()
            MaterialTheme {
                if (editorShown) {
                    holder.SaveableStateProvider("editor") {
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
        }

        rule.onAllNodesWithTag("spec_warning_banner").assertCountEquals(1)
        rule.onNodeWithTag("spec_warning_dismiss").performScrollTo().performClick()
        rule.onAllNodesWithTag("spec_warning_banner").assertCountEquals(0)

        // Off to the Open screen; another document (with the same problem) is loaded; back.
        editorShown = false
        rule.waitForIdle()
        vm.newDocument()
        vm.buildWarnedShaft()
        editorShown = true
        rule.waitForIdle()

        rule.onAllNodesWithTag("spec_warning_banner").assertCountEquals(1)
    }
}
