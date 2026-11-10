package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "Next cut" goes AWAY from the datum (on-device report: under the FWD S.E.T. it opened back
 * toward the datum and the Distance had to be retyped).
 */
class UndercutNextCutDirectionTest {

    private fun cut(id: String, start: Float, len: Float, ref: UndercutReference) =
        Undercut(id = id, startFromAftMm = start, lengthMm = len, diaMm = 200f, authoredReference = ref)

    @Test
    fun `an AFT datum seeds on the FWD end`() {
        val seed = undercutNextCutSeed(cut("a", 600f, 50f, UndercutReference.AFT_SET), oalMm = 2000f)!!
        assertEquals(650f, seed.startFromAftMm, 1e-4f)
    }

    @Test
    fun `a FWD datum seeds on the AFT end, for every FWD-side reference`() {
        for (ref in listOf(UndercutReference.FWD_SET, UndercutReference.LINER_FWD, UndercutReference.PARENT_FWD)) {
            val seed = undercutNextCutSeed(cut("a", 600f, 50f, ref), oalMm = 2000f, lengthMm = 25.4f)!!
            // Its FWD end sits on the cut's AFT end, so its Distance is the last cut's far edge.
            assertEquals(600f, seed.startFromAftMm + seed.lengthMm, 1e-4f)
            assertEquals(ref, seed.authoredReference)
        }
    }

    @Test
    fun `a taken spot steps to the open end of the run`() {
        // FWD datum: cut b, then a (AFT of it) already recorded; Next from b opens AFT of a.
        val b = cut("b", 600f, 50f, UndercutReference.FWD_SET)
        val a = UndercutSpanMm("a", 546f, 600f)
        val seed = undercutNextCutSeed(b, 2000f, 25.4f, listOf(a, UndercutSpanMm("b", 600f, 650f)))!!
        assertEquals(546f, seed.startFromAftMm + seed.lengthMm, 1e-4f)
        // AFT datum mirrors it.
        val c = cut("c", 600f, 50f, UndercutReference.AFT_SET)
        val fwd = undercutNextCutSeed(c, 2000f, 25.4f, listOf(UndercutSpanMm("d", 650f, 700f)))!!
        assertEquals(700f, fwd.startFromAftMm, 1e-4f)
    }

    @Test
    fun `a parent that contains the cut is not in the way`() {
        val child = cut("k", 600f, 50f, UndercutReference.PARENT_FWD)
        val parent = UndercutSpanMm("p", 400f, 800f)
        val seed = undercutNextCutSeed(child, 2000f, 25.4f, listOf(parent))!!
        assertEquals(600f, seed.startFromAftMm + seed.lengthMm, 1e-4f)
    }

    @Test
    fun `no stock on the datum-away side gives no seed`() {
        assertNull(undercutNextCutSeed(cut("a", 10f, 50f, UndercutReference.FWD_SET), 2000f, 25.4f))
    }

    @Test
    fun `an add on a FWD-measured strip tries the AFT side first`() {
        val s = UndercutSpanMm("a", 600f, 650f)
        assertEquals(650f, undercutAddSeedSpan(listOf(s), listOf(s), 2000f, 25.4f)!!.startMm, 1e-4f)
        assertEquals(574.6f, undercutAddSeedSpan(listOf(s), listOf(s), 2000f, 25.4f, aftFirst = true)!!.startMm, 1e-3f)
    }
}
