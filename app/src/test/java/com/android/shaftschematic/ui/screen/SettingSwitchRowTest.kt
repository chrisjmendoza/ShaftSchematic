package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Settings switch row toggles from its LABEL as well as its switch (on-device report: tapping
 * "High contrast" did nothing), and a tap lands exactly once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingSwitchRowTest {

    @get:Rule
    val rule = createComposeRule()

    private var commits = 0

    private fun host() {
        rule.setContent {
            MaterialTheme {
                var on by remember { mutableStateOf(false) }
                SettingSwitchRow(
                    checked = on,
                    onCheckedChange = { commits++; on = it },
                    modifier = Modifier.testTag("row"),
                ) { Text("High contrast") }
            }
        }
    }

    @Test
    fun `tapping the label toggles the setting`() {
        host()
        rule.onNodeWithTag("row").assertIsOff()

        rule.onNodeWithText("High contrast").performClick()
        rule.onNodeWithTag("row").assertIsOn()

        rule.onNodeWithText("High contrast").performClick()
        rule.onNodeWithTag("row").assertIsOff()
        assertEquals(2, commits)
    }

    @Test
    fun `tapping the switch itself toggles once`() {
        host()
        // The row's toggle node merges the switch; a tap at its start lands on the switch.
        rule.onNodeWithTag("row").performClick()
        rule.onNodeWithTag("row").assertIsOn()
        assertEquals(1, commits)
    }
}
