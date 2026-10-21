package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.geom.UNDERCUT_WINDOW_PAD_MM
import com.android.shaftschematic.geom.UndercutLinerSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tab's "Where is the cut?" chooser and the bare-shaft add's synthetic window, as pure
 * functions: one row per liner AFT → FWD then both bare-shaft rows, and a window whose free stock
 * centres the overlay's pending card exactly on the S.E.T. span.
 */
class UndercutAddChoiceTest {

    private val eps = 1e-3f

    @Test
    fun `chooser lists liners aft to fwd then both bare-shaft rows`() {
        val choices = undercutAddChoices(
            linerSpans = listOf(
                UndercutLinerSpan("fwd", 2000f, 2400f),
                UndercutLinerSpan("aft", 500f, 900f),
            ),
            linerTitles = mapOf("aft" to "Aft liner Ø10.000\"", "fwd" to "Fwd liner Ø9.000\""),
        )
        assertEquals(
            listOf(
                UndercutAddChoice.InLiner("aft", "Aft liner Ø10.000\""),
                UndercutAddChoice.InLiner("fwd", "Fwd liner Ø9.000\""),
                UndercutAddChoice.BareShaft(fromAft = true),
                UndercutAddChoice.BareShaft(fromAft = false),
            ),
            choices,
        )
        assertEquals(
            listOf(
                "undercut_add_where_liner_aft", "undercut_add_where_liner_fwd",
                "undercut_add_where_aft", "undercut_add_where_fwd",
            ),
            choices.map { it.testTag },
        )
        assertEquals("Bare shaft — from the AFT S.E.T.", choices[2].label)
        assertEquals("Bare shaft — from the FWD S.E.T.", choices[3].label)
    }

    @Test
    fun `a shaft with no liners still offers both bare-shaft rows`() {
        val choices = undercutAddChoices(emptyList(), emptyMap())
        assertEquals(
            listOf(UndercutAddChoice.BareShaft(true), UndercutAddChoice.BareShaft(false)),
            choices,
        )
    }

    @Test
    fun `an untitled liner falls back to its aft to fwd position`() {
        val choices = undercutAddChoices(
            listOf(UndercutLinerSpan("b", 900f, 1000f), UndercutLinerSpan("a", 100f, 200f)),
            mapOf("a" to "Named"),
        )
        assertEquals("Named", choices[0].label)
        assertEquals("Liner 2", choices[1].label)
    }

    @Test
    fun `aft window pads the span equally around the aft set`() {
        val s = undercutBareShaftAddStrip(fromAft = true, aftSetMm = 300f, fwdSetMm = 2700f, oalMm = 3000f)
        val len = DEFAULT_UNDERCUT_LENGTH_MM
        assertEquals(300f - UNDERCUT_WINDOW_PAD_MM, s.window.startMm, eps)
        assertEquals(300f + len + UNDERCUT_WINDOW_PAD_MM, s.window.endMm, eps)
        assertTrue("an add window holds no recorded cut", s.window.undercutIds.isEmpty())
        // The draw range starts as the window (the overlay adds break room itself).
        assertEquals(s.window.startMm, s.drawStartMm, eps)
        assertEquals(s.window.endMm, s.drawEndMm, eps)
    }

    @Test
    fun `fwd window ends its span on the fwd set`() {
        val s = undercutBareShaftAddStrip(fromAft = false, aftSetMm = 300f, fwdSetMm = 2700f, oalMm = 3000f)
        val len = DEFAULT_UNDERCUT_LENGTH_MM
        assertEquals(2700f - len - UNDERCUT_WINDOW_PAD_MM, s.window.startMm, eps)
        assertEquals(2700f + UNDERCUT_WINDOW_PAD_MM, s.window.endMm, eps)
    }

    @Test
    fun `a set at the shaft end shrinks the pad on both sides so the span stays centred`() {
        val aft = undercutBareShaftAddStrip(fromAft = true, aftSetMm = 0f, fwdSetMm = 3000f, oalMm = 3000f)
        assertEquals(0f, aft.window.startMm, eps)
        assertEquals(DEFAULT_UNDERCUT_LENGTH_MM, aft.window.endMm, eps)

        val near = undercutBareShaftAddStrip(fromAft = true, aftSetMm = 10f, fwdSetMm = 3000f, oalMm = 3000f)
        assertEquals(0f, near.window.startMm, eps)
        assertEquals(10f + DEFAULT_UNDERCUT_LENGTH_MM + 10f, near.window.endMm, eps)

        val fwd = undercutBareShaftAddStrip(fromAft = false, aftSetMm = 0f, fwdSetMm = 3000f, oalMm = 3000f)
        assertEquals(3000f - DEFAULT_UNDERCUT_LENGTH_MM, fwd.window.startMm, eps)
        assertEquals(3000f, fwd.window.endMm, eps)
    }

    @Test
    fun `a shaft shorter than the default length opens on the whole shaft`() {
        val s = undercutBareShaftAddStrip(fromAft = true, aftSetMm = 0f, fwdSetMm = 10f, oalMm = 10f)
        assertEquals(0f, s.window.startMm, eps)
        assertEquals(10f, s.window.endMm, eps)
    }
}
