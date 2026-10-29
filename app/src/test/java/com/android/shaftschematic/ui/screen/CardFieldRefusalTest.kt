package com.android.shaftschematic.ui.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.ui.resolved.ResolvedCouplerBoltSlot
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Carousel-card fields that cannot apply a value REFUSE it through their validator — the field
 * reverts and names the refused value ("0 not applied — Must be > 0") — instead of dropping it
 * in the commit lambda while the box kept showing the typed text (emulator QA: a relief Ø of 0,
 * a bolt Count of 2.5, an explicit body Ø of 0). Each case renders the REAL card and drives the
 * field through IME Done, so a commit lambda that silently swallows a value fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class CardFieldRefusalTest {

    @get:Rule
    val rule = createComposeRule()

    private val oal = 2000f

    private fun field(label: String): SemanticsNodeInteraction =
        rule.onNode(hasSetTextAction() and hasText(label))

    private fun type(label: String, text: String) {
        field(label).performClick()
        field(label).performTextReplacement(text)
        field(label).performImeAction()
        rule.waitForIdle()
    }

    private fun notice(text: String) = rule.onNodeWithText(text, useUnmergedTree = true)

    /* ── Coupler bolt slot ───────────────────────────────────────────────── */

    private val slotCommits = mutableListOf<Int>()

    private fun showSlot() {
        val cs = CouplerBoltSlot(id = "s1", startFromAftMm = 1500f, holeDiaMm = 20f, count = 3, spacingMm = 50f)
        rule.setContent {
            MaterialTheme {
                CouplerBoltSlotPagerCard(
                    component = ResolvedCouplerBoltSlot(
                        id = cs.id, startMmPhysical = cs.startFromAftMm, endMmPhysical = cs.startFromAftMm + 100f,
                        holeDiaMm = cs.holeDiaMm, count = cs.count, spacingMm = cs.spacingMm,
                        through = cs.through, depthMm = cs.depthMm,
                    ),
                    explicitIndex = 0,
                    spec = ShaftSpec(overallLengthMm = oal, couplerBoltSlots = listOf(cs)),
                    unit = UnitSystem.MILLIMETERS,
                    outerPaddingHorizontal = Dp(16f),
                    showComponentDebugLabels = false,
                    f1 = { "%.1f".format(it) },
                    onUpdateCouplerBoltSlot = { _, _, _, count, _, _, _ -> slotCommits += count },
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
    fun `a bolt Count of 0 is refused and named, never stored as 1`() {
        showSlot()
        type("Count", "0")
        assertTrue("nothing committed: $slotCommits", slotCommits.isEmpty())
        notice("0 not applied — $BOLT_SLOT_COUNT_MSG").assertExists()
    }

    @Test
    fun `a bolt Count of 2_5 is refused and named, never dropped silently`() {
        showSlot()
        type("Count", "2.5")
        assertTrue(slotCommits.isEmpty())
        notice("2.5 not applied — $BOLT_SLOT_COUNT_MSG").assertExists()
    }

    @Test
    fun `a whole bolt Count commits verbatim`() {
        showSlot()
        type("Count", "4")
        assertEquals(listOf(4), slotCommits)
    }

    /* ── Liner ───────────────────────────────────────────────────────────── */

    private val linerCommits = mutableListOf<Pair<Float, Float>>()   // length, od

    private fun showLiner() {
        val l1 = Liner(id = "l1", startFromAftMm = 100f, lengthMm = 200f, odMm = 90f)
        val l2 = Liner(id = "l2", startFromAftMm = 500f, lengthMm = 200f, odMm = 90f)
        val spec = ShaftSpec(overallLengthMm = oal, liners = listOf(l1, l2))
        rule.setContent {
            MaterialTheme {
                LinerPagerCard(
                    component = ResolvedLiner(id = l1.id, startMmPhysical = 100f, endMmPhysical = 300f, odMm = l1.odMm),
                    explicitIndex = 0,
                    spec = spec,
                    unit = UnitSystem.MILLIMETERS,
                    physicalIndex = 0,
                    outerPaddingHorizontal = Dp(16f),
                    showComponentDebugLabels = false,
                    linerTitleById = mapOf("l1" to "AFT Liner", "l2" to "FWD Liner"),
                    f1 = { "%.1f".format(it) },
                    onUpdateLiner = { _, _, len, od -> linerCommits += len to od },
                    onUpdateLinerShowDia = { _, _ -> },
                    onUpdateLinerShowLabel = { _, _ -> },
                    onUpdateLinerShoulder = { _, _, _, _, _ -> },
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
    }

    /** A Length of 500 would run the AFT liner into the FWD one — refused like the same span as a Start. */
    @Test
    fun `a liner Length that runs into another liner is refused and names it`() {
        showLiner()
        type("Length (mm)", "500")
        assertTrue(linerCommits.isEmpty())
        notice("500 not applied — Overlaps FWD Liner").assertExists()
    }

    /** Running past the OAL is the bounds warning's job, so a long length that hits nothing commits. */
    @Test
    fun `a liner Length that hits nothing commits verbatim`() {
        showLiner()
        type("Length (mm)", "350")
        assertEquals(listOf(350f to 90f), linerCommits)
    }

    @Test
    fun `a liner OD of 0 is refused`() {
        showLiner()
        type("Outer Ø (mm)", "0")
        assertTrue(linerCommits.isEmpty())
        notice("0 not applied — Must be > 0").assertExists()
    }

    /* ── Taper ───────────────────────────────────────────────────────────── */

    private val taperCommits = mutableListOf<Triple<Float, Float, String>>()   // length, endDia, rate
    private val modeCommits = mutableListOf<Pair<Boolean, String?>>()

    /** 304.8 long, 100 → 125.4: exactly 1:12, so a typed Manual `1:12` equals the computed rate. */
    private fun showTaper(manual: Boolean?) {
        val t = Taper(
            id = "t1", startFromAftMm = 0f, lengthMm = 304.8f, startDiaMm = 100f, endDiaMm = 125.4f,
            taperRateText = "1:12", taperRateManual = manual,
        )
        rule.setContent {
            MaterialTheme {
                TaperPagerCard(
                    component = ResolvedTaper(id = t.id, startMmPhysical = 0f, endMmPhysical = t.lengthMm,
                        startDiaMm = t.startDiaMm, endDiaMm = t.endDiaMm),
                    explicitIndex = 0,
                    spec = ShaftSpec(overallLengthMm = oal, tapers = listOf(t)),
                    unit = UnitSystem.MILLIMETERS,
                    physicalIndex = 0,
                    outerPaddingHorizontal = Dp(16f),
                    showComponentDebugLabels = false,
                    taperTitleById = mapOf(t.id to "AFT Taper"),
                    f1 = { "%.1f".format(it) },
                    onUpdateTaper = { _, _, len, _, endDia, rate -> taperCommits += Triple(len, endDia, rate) },
                    onUpdateTaperLabel = { _, _ -> },
                    onUpdateTaperShowLabel = { _, _ -> },
                    onUpdateTaperKeyway = { _, _, _, _, _, _ -> },
                    onUpdateTaperReference = { _, _ -> },
                    onUpdateTaperRateMode = { _, m, text -> modeCommits += m to text },
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

    /**
     * A stored Manual mode survives a reopen even when the typed rate equals the computed one,
     * and a Length edit then keeps the TYPED rate — the reopened card seeded Auto from the text and
     * the next Length edit replaced `1:12` with a recomputed rate (emulator QA).
     */
    @Test
    fun `a stored Manual rate is kept through a Length edit`() {
        showTaper(manual = true)
        type("Length (mm)", "406.4")
        assertEquals("1:12", taperCommits.single().third)
    }

    /** A taper saved before the mode was stored keeps the old text seeding: equal text ⇒ Auto. */
    @Test
    fun `a taper with no stored mode follows Auto when its text matches`() {
        showTaper(manual = null)
        type("Length (mm)", "406.4")
        assertEquals("1:16", taperCommits.single().third)
    }

    @Test
    fun `the mode chips store the mode`() {
        showTaper(manual = null)
        rule.onNodeWithText("Manual").performClick()
        rule.waitForIdle()
        assertEquals(true to null, modeCommits.last())
        rule.onNodeWithText("Auto").performClick()
        rule.waitForIdle()
        assertEquals(false, modeCommits.last().first)
    }

    /** Re-deriving that end from the rate on a typed 0 would store a value other than the one typed. */
    @Test
    fun `a taper diameter of 0 is refused`() {
        showTaper(manual = true)
        type("L.E.T. Ø (mm)", "0")
        assertTrue(taperCommits.isEmpty())
        notice("0 not applied — Must be > 0").assertExists()
    }
}
