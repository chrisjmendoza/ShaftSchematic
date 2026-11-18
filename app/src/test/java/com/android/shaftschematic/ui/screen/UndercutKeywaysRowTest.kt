package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.shaftschematic.settings.PdfPrefs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The undercut drawing's "Keyways" switch (`PdfPrefs.undercutKeyways`, on-device request): one row
 * on the Undercut tab, the options panel and Settings, labelled and captioned the same everywhere,
 * app-wide, and off for a fresh install.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class UndercutKeywaysRowTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the row reads Keyways, says it is app-wide and toggles the pref`() {
        var on by mutableStateOf(PdfPrefs().undercutKeyways)
        rule.setContent {
            MaterialTheme {
                UndercutKeywaysRow(checked = on, onCheckedChange = { on = it }, testTag = "undercut_keyways_switch")
            }
        }

        rule.onNodeWithText(UNDERCUT_KEYWAYS_LABEL).assertExists()
        rule.onNodeWithText("$UNDERCUT_KEYWAYS_CAPTION App-wide.").assertExists()
        rule.onNodeWithTag("undercut_keyways_switch").assertIsOff()

        rule.onNodeWithTag("undercut_keyways_switch").performClick()

        rule.onNodeWithTag("undercut_keyways_switch").assertIsOn()
        assertEquals(true, on)
    }
}
