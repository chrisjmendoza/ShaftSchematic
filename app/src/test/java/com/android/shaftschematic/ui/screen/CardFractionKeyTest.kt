package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.ui.input.FRACTION_KEY_TAG
import com.android.shaftschematic.ui.input.LocalFractionKeyEnabled
import com.android.shaftschematic.ui.resolved.ResolvedCouplerBoltSlot
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.util.UnitSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The in-field "/" key on the carousel cards shows only on a fraction-capable NUMBER-PAD field —
 * the dialogs' rule. The taper's Rate asks for the text keyboard, which already has `/`, and the
 * bolt Count is a whole number; both must carry no key, while a Length field focused in an inch
 * document does. Renders the real cards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class CardFractionKeyTest {

    @get:Rule
    val rule = createComposeRule()

    private val oal = 2000f

    private fun field(label: String): SemanticsNodeInteraction =
        rule.onNode(hasSetTextAction() and hasText(label))

    private fun focus(label: String) {
        field(label).performClick()
        rule.waitForIdle()
    }

    private fun keys() = rule.onAllNodesWithTag(FRACTION_KEY_TAG)

    @Composable
    private fun InchDocument(content: @Composable () -> Unit) {
        MaterialTheme {
            CompositionLocalProvider(LocalFractionKeyEnabled provides true) { content() }
        }
    }

    private fun showTaper() {
        val t = Taper(
            id = "t1", startFromAftMm = 0f, lengthMm = 304.8f, startDiaMm = 100f, endDiaMm = 125.4f,
            taperRateText = "1:12", taperRateManual = true,
        )
        rule.setContent {
            InchDocument {
                TaperPagerCard(
                    component = ResolvedTaper(id = t.id, startMmPhysical = 0f, endMmPhysical = t.lengthMm,
                        startDiaMm = t.startDiaMm, endDiaMm = t.endDiaMm),
                    explicitIndex = 0,
                    spec = ShaftSpec(overallLengthMm = oal, tapers = listOf(t)),
                    unit = UnitSystem.INCHES,
                    physicalIndex = 0,
                    outerPaddingHorizontal = Dp(16f),
                    showComponentDebugLabels = false,
                    taperTitleById = mapOf(t.id to "AFT Taper"),
                    f1 = { "%.1f".format(it) },
                    onUpdateTaper = { _, _, _, _, _, _ -> },
                    onUpdateTaperLabel = { _, _ -> },
                    onUpdateTaperShowLabel = { _, _ -> },
                    onUpdateTaperKeyway = { _, _, _, _, _, _ -> },
                    onUpdateTaperReference = { _, _ -> },
                    onUpdateTaperRateMode = { _, _, _ -> },
                    onSetKeyways180Apart = {},
                    onSetKeyways90Apart = {},
                    onSetKeyways90Cw = {},
                    onRemoveTaper = {},
                    collidingComponentIds = emptySet(),
                    perComponentUnitsEnabled = false,
                    unitOverrides = emptyMap(),
                    onSetComponentUnit = { _, _ -> },
                    onSetKeywayUnit = { _, _ -> },
                )
            }
        }
    }

    private fun showSlot() {
        val cs = CouplerBoltSlot(id = "s1", startFromAftMm = 1500f, holeDiaMm = 20f, count = 3, spacingMm = 50f)
        rule.setContent {
            InchDocument {
                CouplerBoltSlotPagerCard(
                    component = ResolvedCouplerBoltSlot(
                        id = cs.id, startMmPhysical = cs.startFromAftMm, endMmPhysical = cs.startFromAftMm + 100f,
                        holeDiaMm = cs.holeDiaMm, count = cs.count, spacingMm = cs.spacingMm,
                        through = cs.through, depthMm = cs.depthMm,
                    ),
                    explicitIndex = 0,
                    spec = ShaftSpec(overallLengthMm = oal, couplerBoltSlots = listOf(cs)),
                    unit = UnitSystem.INCHES,
                    outerPaddingHorizontal = Dp(16f),
                    showComponentDebugLabels = false,
                    f1 = { "%.1f".format(it) },
                    onUpdateCouplerBoltSlot = { _, _, _, _, _, _, _ -> },
                    onUpdateCouplerBoltSlotReference = { _, _ -> },
                    onUpdateCouplerBoltSlotStyle = { _, _ -> },
                    onUpdateCouplerBoltSlotClocking = { _, _ -> },
                    onUpdateCouplerBoltSlotShowRail = { _, _ -> },
                    onRemoveCouplerBoltSlot = {},
                )
            }
        }
    }

    @Test
    fun `a card Length focused in an inch document carries the key`() {
        showTaper()
        focus("Length (in)")
        keys().assertCountEquals(1)
    }

    @Test
    fun `the taper card's Rate field, on the text keyboard, carries no key`() {
        showTaper()
        focus("Rate (1:12, 3/4\"/ft, or decimal)")
        keys().assertCountEquals(0)
    }

    @Test
    fun `the bolt-slot card's Count field carries no key`() {
        showSlot()
        focus("Count")
        keys().assertCountEquals(0)
    }

    @Test
    fun `the bolt-slot card's Spacing field carries the key`() {
        showSlot()
        focus("Spacing (in)")
        keys().assertCountEquals(1)
    }
}
