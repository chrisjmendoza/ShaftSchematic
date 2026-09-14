package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The keyway "Standard size…" picker.
 *
 * It is a menu, not an automation: nothing reaches [KeywayStdSizePicker]'s `onPick` until the
 * user opens it and taps an entry, and the entry it offers FIRST is the one the standard names
 * for the host diameter. These pin both halves plus the numbers a pick writes — in canonical mm,
 * off whichever table the keyway's own unit selects.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class KeywayStdSizePickerTest {

    @get:Rule
    val rule = createComposeRule()

    private var picked: Pair<Float, Float>? = null

    private fun host(unit: UnitSystem, hostDiaMm: Float) {
        rule.setContent {
            MaterialTheme {
                KeywayStdSizePicker(unit = unit, hostDiaMm = hostDiaMm) { w, d -> picked = w to d }
            }
        }
    }

    @Test
    fun `the menu is closed until the button is tapped`() {
        host(UnitSystem.INCHES, 25.4f)
        rule.onNodeWithTag(FIRST).assertDoesNotExist()
        rule.onNodeWithTag(BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(FIRST).assertExists()
        assertTrue("opening the menu must write nothing", picked == null)
    }

    @Test
    fun `an inch host is offered its standard key first`() {
        host(UnitSystem.INCHES, 25.4f)   // a 1" shaft
        rule.onNodeWithTag(BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(FIRST).assertTextContains("1/4")
        rule.onNodeWithTag(FIRST).assertTextContains("Suggested", substring = true)
    }

    @Test
    fun `picking the suggested inch key writes canonical millimetres`() {
        host(UnitSystem.INCHES, 25.4f)
        rule.onNodeWithTag(BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(FIRST).performClick()
        rule.waitForIdle()
        val p = picked
        assertNotNull("a tap must reach onPick", p)
        assertEquals(6.35, p!!.first.toDouble(), 1e-3)   // 1/4" wide
        assertEquals(3.175, p.second.toDouble(), 1e-3)   // 1/8" deep into the shaft
    }

    @Test
    fun `a metric host is offered the DIN key`() {
        host(UnitSystem.MILLIMETERS, 25f)
        rule.onNodeWithTag(BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(FIRST).assertTextContains("8 × 7")
        rule.onNodeWithTag(FIRST).performClick()
        rule.waitForIdle()
        val p = picked
        assertNotNull("a tap must reach onPick", p)
        assertEquals(8.0, p!!.first.toDouble(), 1e-4)
        assertEquals(4.0, p.second.toDouble(), 1e-4)
    }

    private companion object {
        const val BUTTON = "keyway_std_size_button"
        const val FIRST = "keyway_std_size_0"
    }
}
