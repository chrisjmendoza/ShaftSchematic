package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Draw to the shaft end" — on by default, and reaching to the end TAPER, not just its S.E.T.
 * (on-device report: a section next to the taper put its S-break through the taper).
 */
class UndercutDrawToShaftEndTest {

    private val inch = 25.4f

    /** 200" shaft, a 30" AFT taper (S.E.T. at the shaft end), a liner from 32" to 62". */
    private fun spec(taperLenIn: Float, linerStartIn: Float) = ShaftSpec(
        overallLengthMm = 200f * inch,
        tapers = listOf(
            Taper(
                id = "T", startFromAftMm = 0f, lengthMm = taperLenIn * inch,
                startDiaMm = 7f * inch, endDiaMm = 9.5f * inch,
            ),
        ),
        liners = listOf(
            Liner(id = "L", startFromAftMm = linerStartIn * inch, lengthMm = 30f * inch, odMm = 10f * inch),
        ),
        autoBodyDiaMm = 9.5f * inch,
    )

    private fun stripFor(s: ShaftSpec): UndercutStrip.LinerStrip {
        val liner = s.liners.single()
        val (aftSet, fwdSet) = undercutSetPositions(s)
        val (aftReach, fwdReach) = undercutSetReachAnchors(s)
        return linerStripFor(
            UndercutLinerSpan(liner.id, liner.startFromAftMm, liner.startFromAftMm + liner.lengthMm),
            emptyList(), s.overallLengthMm,
            segs = surfaceSegsFrom(resolveComponents(s)),
            reachToSetMm = undercutSetReachMm(UndercutRecord().drawToSet),
            aftSetMm = aftSet, fwdSetMm = fwdSet,
            aftReachFromMm = aftReach, fwdReachFromMm = fwdReach,
        )
    }

    @Test
    fun `a new job draws to the shaft end`() {
        assertTrue(UndercutRecord().drawToSet)
    }

    @Test
    fun `a liner beside a long taper draws through the taper to the shaft end`() {
        // The S.E.T. is 32" from the liner — past the 24" reach — but the taper is 2" away.
        val s = spec(taperLenIn = 30f, linerStartIn = 32f)
        assertEquals(30f * inch, undercutSetReachAnchors(s).first, 0.01f)
        val strip = stripFor(s)
        assertEquals("the drawing runs to the shaft end", 0f, strip.drawStartMm, 0.01f)
        assertEquals("the chain runs to the S.E.T.", 0f, strip.chainStartMm, 0.01f)
    }

    @Test
    fun `a liner far from the taper keeps its break in the bare shaft`() {
        val s = spec(taperLenIn = 30f, linerStartIn = 60f)   // 30" of bare shaft to the taper
        val strip = stripFor(s)
        assertTrue("the drawing stops short of the taper", strip.drawStartMm > 30f * inch)
        assertEquals(60f * inch, strip.chainStartMm, 0.01f)
    }
}
