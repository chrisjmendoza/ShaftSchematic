package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.COUPLING_PILOT_COMPONENT_ID
import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two Runout-tab rules (emulator QA): the coupling-face row shows the entered pilot runout, and
 * "Undo move" retires once the moved component's station count has changed — a −/+ renumbers
 * its stations, so the stored index could un-pin a different bubble.
 */
class RunoutTabRowsTest {

    private fun pilot(valueMm: Float?, tick: Int? = null) =
        RunoutReading(componentId = COUPLING_PILOT_COMPONENT_ID, stationIndex = 0, valueMm = valueMm, highSpotHalfHours = tick)

    @Test
    fun `an entered pilot value shows in the bubble's format`() {
        assertEquals("Pilot runout: .003 in", pilotRunoutSummary(pilot(0.0762f), UnitSystem.INCHES))
    }

    @Test
    fun `the high spot rides along when set`() {
        val s = pilotRunoutSummary(pilot(0.0762f, tick = 6), UnitSystem.INCHES)!!
        assertTrue(s, s.startsWith("Pilot runout: .003 in · high spot "))
    }

    @Test
    fun `nothing entered shows nothing`() {
        assertNull(pilotRunoutSummary(null, UnitSystem.INCHES))
        assertNull(pilotRunoutSummary(pilot(null), UnitSystem.INCHES))
    }

    @Test
    fun `undo move applies only at the count it was made at`() {
        assertTrue(undoMoveStillApplies(countAtMove = 4, currentCount = 4))
        assertFalse(undoMoveStillApplies(countAtMove = 4, currentCount = 5))
        assertFalse(undoMoveStillApplies(countAtMove = 4, currentCount = 3))
        assertFalse("component gone", undoMoveStillApplies(countAtMove = 4, currentCount = null))
        assertFalse(undoMoveStillApplies(countAtMove = null, currentCount = 4))
    }
}
