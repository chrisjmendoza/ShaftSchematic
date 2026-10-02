package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The keyway section gate — ONE header row ([KeywayGateRow]) behind the Body/Taper cards and
 * both Add dialogs, and ONE untick decision ([keywayGateAction]) behind both cards.
 *
 * The row is rendered; the "Remove keyway?" dialog and the Add dialogs are not — an
 * `AlertDialog` opens its own window, which the Compose test rule under Robolectric never
 * settles (`AppNotIdleException`, then OOM in every later test of the class), and no test in
 * this repo renders one. The card's confirm-vs-clear choice is therefore pinned through the pure
 * decision it routes through.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class KeywaySectionTest {

    @get:Rule
    val rule = createComposeRule()

    // ── KeywayGateRow ────────────────────────────────────────────────────────────

    @Test
    fun `gate row toggles through a row click and reports the new value`() {
        val reported = mutableListOf<Boolean>()
        rule.setContent {
            MaterialTheme {
                var checked by remember { mutableStateOf(false) }
                KeywayGateRow(checked = checked, testTag = TAG) {
                    reported += it
                    checked = it
                }
            }
        }
        rule.onNodeWithTag(TAG).assertIsOff()
        rule.onNodeWithTag(TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TAG).assertIsOn()
        rule.onNodeWithTag(TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TAG).assertIsOff()
        assertEquals(listOf(true, false), reported)
    }

    @Test
    fun `gate row leaves the decision to the caller`() {
        // A card that answers an untick with a confirm keeps the box ticked until confirmed.
        val reported = mutableListOf<Boolean>()
        rule.setContent {
            MaterialTheme {
                KeywayGateRow(checked = true, testTag = TAG) { reported += it }
            }
        }
        rule.onNodeWithTag(TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TAG).assertIsOn()
        assertEquals(listOf(false), reported)
    }

    // ── keywayGateAction ─────────────────────────────────────────────────────────

    @Test
    fun `ticking always just reveals`() {
        assertEquals(KeywayGateAction.REVEAL, keywayGateAction(checked = true, hasAnyKeywayValue = false))
        assertEquals(KeywayGateAction.REVEAL, keywayGateAction(checked = true, hasAnyKeywayValue = true))
    }

    @Test
    fun `unticking with any typed value asks before clearing`() {
        assertEquals(KeywayGateAction.CONFIRM_REMOVE, keywayGateAction(checked = false, hasAnyKeywayValue = true))
    }

    @Test
    fun `unticking an empty section just hides it`() {
        assertEquals(KeywayGateAction.HIDE, keywayGateAction(checked = false, hasAnyKeywayValue = false))
    }

    private companion object {
        const val TAG = "kw_gate_under_test"
    }
}
