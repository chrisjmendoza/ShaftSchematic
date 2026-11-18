package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Ø tolerance row is value entry, so it sits under the add-dialog-parity rule: the liner card
 * and `AddLinerDialog` (and the body pair) both carry the `+` / `−` fields under the Ø, typed in the
 * document unit and handed on in mm verbatim.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class DiaToleranceUiTest {

    @get:Rule
    val rule = createComposeRule()

    private fun tagged(tag: String): SemanticsNodeInteraction =
        rule.onNode(hasTestTag(tag) and hasSetTextAction(), useUnmergedTree = true)

    private val liner = Liner(id = "l1", startFromAftMm = 100f, lengthMm = 200f, odMm = 228.6f,
        endMmPhysical = 300f, diaTolMinusMm = 0.0254f)

    @Test
    fun `the liner card carries the tolerance row and commits a typed limit in mm`() {
        val commits = mutableListOf<Pair<Float, Float>>()
        val spec = ShaftSpec(overallLengthMm = 2000f, liners = listOf(liner))
        rule.setContent {
            MaterialTheme {
                LinerPagerCard(
                    component = ResolvedLiner(id = liner.id, startMmPhysical = 100f, endMmPhysical = 300f, odMm = liner.odMm),
                    explicitIndex = 0,
                    spec = spec,
                    unit = UnitSystem.INCHES,
                    physicalIndex = 0,
                    outerPaddingHorizontal = Dp(16f),
                    showComponentDebugLabels = false,
                    linerTitleById = mapOf("l1" to "Liner"),
                    f1 = { "%.1f".format(it) },
                    onUpdateLiner = { _, _, _, _ -> },
                    onUpdateLinerShowDia = { _, _ -> },
                    onUpdateLinerShowLabel = { _, _ -> },
                    onUpdateLinerShoulder = { _, _, _, _, _ -> },
                    onUpdateLinerDiaTol = { _, plus, minus -> commits += plus to minus },
                    linerShouldersEnabled = false,
                    onUpdateLinerLabel = { _, _ -> },
                    onUpdateLinerReference = { _, _ -> },
                    onRemoveLiner = {},
                    collidingComponentIds = emptySet(),
                    perComponentUnitsEnabled = false,
                    unitOverrides = emptyMap(),
                    onSetComponentUnit = { _, _ -> },
                )
            }
        }
        tagged("liner_dia_tol_plus").assertExists()
        tagged("liner_dia_tol_minus").assertExists()

        tagged("liner_dia_tol_plus").performClick()
        tagged("liner_dia_tol_plus").performTextReplacement(".002")
        tagged("liner_dia_tol_plus").performImeAction()
        rule.waitForIdle()
        // The other side is passed through untouched.
        assertEquals(1, commits.size)
        assertEquals(0.0508f, commits.single().first, 1e-6f)
        assertEquals(0.0254f, commits.single().second, 0f)
    }

    @Test
    fun `AddLinerDialog carries the tolerance row and submits it`() {
        var submitted: DiaToleranceDraft? = null
        rule.setContent {
            MaterialTheme {
                AddLinerDialog(
                    unit = UnitSystem.INCHES,
                    spec = ShaftSpec(overallLengthMm = 2000f),
                    onSubmit = { _, _, _, _, _, tol -> submitted = tol },
                    onCancel = {},
                )
            }
        }
        tagged("add_liner_dia_tol_plus").assertExists()
        tagged("add_liner_dia_tol_minus").assertExists()
        tagged("add_liner_dia_tol_plus").performTextReplacement(".002")
        rule.waitForIdle()
        rule.onNode(hasText("Add") and hasSetTextAction().not()).performClick()
        rule.waitForIdle()
        assertNotNull("Add submitted", submitted)
        assertEquals(0.002f * 25.4f, submitted!!.plusMm, 1e-6f)
        assertEquals(0f, submitted!!.minusMm, 0f)
    }

    @Test
    fun `AddBodyDialog carries the tolerance row`() {
        rule.setContent {
            MaterialTheme {
                AddBodyDialog(
                    unit = UnitSystem.INCHES,
                    spec = ShaftSpec(overallLengthMm = 2000f),
                    onSubmit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    onCancel = {},
                )
            }
        }
        tagged("add_body_dia_tol_plus").performScrollTo().assertExists()
        tagged("add_body_dia_tol_minus").assertExists()
    }
}
