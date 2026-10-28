package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where an Add dialog's Start opens. On a shaft whose components run to the OAL the end of the
 * last component IS the OAL, and Add Body opened on a body starting at the FWD face (on-device
 * report); it now opens on the AFT-most bare-shaft span instead.
 */
class AddStartSeedTest {

    private val oal = 4000f
    private val aftTaper = Taper(id = "t1", startFromAftMm = 0f, lengthMm = 300f, startDiaMm = 150f, endDiaMm = 175f)
    private val fwdTaper = Taper(id = "t2", startFromAftMm = oal - 300f, lengthMm = 300f, startDiaMm = 175f, endDiaMm = 150f)
    private val liner = Liner(id = "l1", startFromAftMm = 1000f, lengthMm = 400f, odMm = 240f)

    @Test
    fun `a fully covered shaft seeds from the first bare-shaft span`() {
        val spec = ShaftSpec(overallLengthMm = oal, tapers = listOf(aftTaper, fwdTaper), liners = listOf(liner))

        val seed = addStartSeed(spec)

        assertEquals("the AFT-most auto span starts at the AFT taper's end", 300f, seed.startMm, 0.01f)
        assertEquals("sized to that span — up to the liner", 1000f, seed.bareSpanEndMm!!, 0.01f)
        assertEquals(seed.startMm, computeAddDefaults(spec).startMm, 0f)
        assertEquals(seed.startMm, addDialogStartMm(spec), 0f)
    }

    @Test
    fun `a shaft with a trailing gap keeps the end seed`() {
        val spec = ShaftSpec(overallLengthMm = oal, tapers = listOf(aftTaper), liners = listOf(liner))

        val seed = addStartSeed(spec)

        assertEquals("the end of the last component, unchanged", 1400f, seed.startMm, 0.01f)
        assertNull(seed.bareSpanEndMm)
    }

    @Test
    fun `a shaft with no bare shaft left keeps the end seed`() {
        val whole = Liner(id = "l2", startFromAftMm = 300f, lengthMm = oal - 600f, odMm = 240f)
        val spec = ShaftSpec(overallLengthMm = oal, tapers = listOf(aftTaper, fwdTaper), liners = listOf(whole))

        assertEquals(oal, addStartSeed(spec).startMm, 0.01f)
        assertNull(addStartSeed(spec).bareSpanEndMm)
    }

    @Test
    fun `an empty shaft seeds at zero`() {
        assertEquals(0f, addStartSeed(ShaftSpec(overallLengthMm = oal)).startMm, 0f)
    }
}
