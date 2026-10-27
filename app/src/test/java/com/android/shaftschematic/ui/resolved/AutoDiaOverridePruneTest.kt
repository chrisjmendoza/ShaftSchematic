package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.AutoDiaOverride
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [withAutoDiaOverridesSurviving] — an auto-section Ø override dies with its section on a
 * component add/remove (on-device ruling: a deleted section that comes back without Undo takes
 * the shaft's Ø). Kept iff the anchor sits in an auto run both before and after the edit and
 * that run's start did not move AFT-ward.
 */
class AutoDiaOverridePruneTest {

    private val aft = AutoDiaOverride(anchorMm = 200f, diaMm = 150f)  // section 0..400
    private val fwd = AutoDiaOverride(anchorMm = 800f, diaMm = 160f)  // section 600..1000

    private val separator = Liner(id = "l1", startFromAftMm = 400f, lengthMm = 200f, odMm = 200f)

    /** OAL 1000 with a liner at 400..600 — auto sections 0..400 and 600..1000. */
    private fun twoSections(vararg overrides: AutoDiaOverride) = ShaftSpec(
        overallLengthMm = 1000f,
        liners = listOf(separator),
        autoDiaOverrides = overrides.toList(),
    )

    @Test
    fun `removing the liner AFT of a section prunes that section's override`() {
        val before = twoSections(fwd)
        val after = before.copy(liners = emptyList())

        assertTrue(after.withAutoDiaOverridesSurviving(before).autoDiaOverrides.isEmpty())
    }

    @Test
    fun `removing the liner FWD of a section keeps that section's override`() {
        val before = twoSections(aft)
        val after = before.copy(liners = emptyList())

        assertSame(after, after.withAutoDiaOverridesSurviving(before))
    }

    @Test
    fun `a merge keeps the aft section's override and drops the fwd one`() {
        val before = twoSections(aft, fwd)
        val after = before.copy(liners = emptyList())

        assertEquals(listOf(aft), after.withAutoDiaOverridesSurviving(before).autoDiaOverrides)
    }

    @Test
    fun `a liner added upstream inside the span keeps the override`() {
        val before = twoSections(fwd)
        // 650..700 trims the section to 700..1000 — its start moved FWD-ward.
        val after = before.copy(
            liners = before.liners + Liner(id = "l2", startFromAftMm = 650f, lengthMm = 50f, odMm = 200f)
        )

        assertSame(after, after.withAutoDiaOverridesSurviving(before))
    }

    @Test
    fun `a liner added over the anchor prunes the override`() {
        val before = twoSections(fwd)
        val after = before.copy(
            liners = before.liners + Liner(id = "l2", startFromAftMm = 750f, lengthMm = 100f, odMm = 200f)
        )

        assertTrue(after.withAutoDiaOverridesSurviving(before).autoDiaOverrides.isEmpty())
    }

    @Test
    fun `an explicit body added over the anchor prunes the override`() {
        val before = twoSections(fwd)
        // The "Explicit body" promotion: a body over the whole section.
        val after = before.copy(
            bodies = listOf(Body(id = "b1", startFromAftMm = 600f, lengthMm = 400f, diaMm = 160f))
        )

        assertTrue(after.withAutoDiaOverridesSurviving(before).autoDiaOverrides.isEmpty())
    }

    @Test
    fun `an override dormant before the edit stays pruned when its cover is removed`() {
        // Anchor 500 sits inside the liner — dormant. Removing the liner must not resurrect it.
        val dormant = AutoDiaOverride(anchorMm = 500f, diaMm = 170f)
        val before = twoSections(dormant)
        val after = before.copy(liners = emptyList())

        assertTrue(after.withAutoDiaOverridesSurviving(before).autoDiaOverrides.isEmpty())
    }

    @Test
    fun `an unchanged spec drops nothing, dormant overrides included`() {
        val dormant = AutoDiaOverride(anchorMm = 500f, diaMm = 170f)
        val spec = twoSections(aft, dormant)

        assertSame(spec, spec.withAutoDiaOverridesSurviving(spec))
    }
}
