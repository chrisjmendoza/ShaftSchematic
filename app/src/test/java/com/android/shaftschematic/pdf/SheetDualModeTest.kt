package com.android.shaftschematic.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The consolidated sheet's whole-sheet give-back order: stacking, then the footer's line pitch,
 * then the secondary unit — and never a mode richer than asked.
 */
class SheetDualModeTest {

    @Test
    fun `the wanted mode follows the dual flag and the layout pref`() {
        assertEquals(SheetDualMode.SINGLE, SheetDualMode.wanted(dual = false, stacked = true))
        assertEquals(SheetDualMode.STACKED, SheetDualMode.wanted(dual = true, stacked = true))
        assertEquals(SheetDualMode.INLINE, SheetDualMode.wanted(dual = true, stacked = false))
    }

    @Test
    fun `the footer pitch gives before the secondary unit does`() {
        assertEquals(
            listOf(
                SheetFallback.STACKED, SheetFallback.INLINE,
                SheetFallback.INLINE_TIGHT_FOOTER, SheetFallback.SINGLE,
            ),
            sheetFallbacks(SheetDualMode.STACKED),
        )
        assertEquals(
            listOf(SheetFallback.INLINE, SheetFallback.INLINE_TIGHT_FOOTER, SheetFallback.SINGLE),
            sheetFallbacks(SheetDualMode.INLINE),
        )
        assertEquals(listOf(SheetFallback.SINGLE), sheetFallbacks(SheetDualMode.SINGLE))
    }

    @Test
    fun `stacking never takes the tight footer, the primary-only sheet may`() {
        assertFalse(SheetFallback.STACKED.tightFooter)
        assertFalse(SheetFallback.INLINE.tightFooter)
        assertTrue(SheetFallback.INLINE_TIGHT_FOOTER.tightFooter)
        assertTrue(SheetFallback.SINGLE.tightFooter)
        assertEquals(SheetDualMode.INLINE, SheetFallback.INLINE_TIGHT_FOOTER.mode)
    }

    @Test
    fun `a sheet that fits as asked prints as asked`() {
        assertEquals(SheetFallback.STACKED, fittedSheetFallback(SheetDualMode.STACKED) { true })
        assertEquals(SheetFallback.INLINE, fittedSheetFallback(SheetDualMode.INLINE) { true })
        assertEquals(SheetFallback.SINGLE, fittedSheetFallback(SheetDualMode.SINGLE) { true })
    }

    @Test
    fun `the first step that fits wins`() {
        assertEquals(
            SheetFallback.INLINE,
            fittedSheetFallback(SheetDualMode.STACKED) { it != SheetFallback.STACKED },
        )
        assertEquals(
            SheetFallback.INLINE_TIGHT_FOOTER,
            fittedSheetFallback(SheetDualMode.STACKED) { it.tightFooter },
        )
        assertEquals(
            SheetFallback.INLINE_TIGHT_FOOTER,
            fittedSheetFallback(SheetDualMode.INLINE) { it == SheetFallback.INLINE_TIGHT_FOOTER },
        )
    }

    @Test
    fun `nothing fits - the roomiest step, never one richer than asked`() {
        assertEquals(SheetFallback.SINGLE, fittedSheetFallback(SheetDualMode.STACKED) { false })
        // An INLINE sheet never moves UP to stacked, even if stacked would "fit".
        assertEquals(
            SheetFallback.SINGLE,
            fittedSheetFallback(SheetDualMode.INLINE) { it == SheetFallback.STACKED },
        )
    }

    @Test
    fun `the last step is taken without asking`() {
        val asked = mutableListOf<SheetFallback>()
        fittedSheetFallback(SheetDualMode.STACKED) { asked += it; false }
        assertEquals(
            listOf(SheetFallback.STACKED, SheetFallback.INLINE, SheetFallback.INLINE_TIGHT_FOOTER),
            asked,
        )
        asked.clear()
        assertEquals(SheetFallback.SINGLE, fittedSheetFallback(SheetDualMode.SINGLE) { asked += it; false })
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `only SINGLE drops the secondary unit, only STACKED stacks`() {
        assertTrue(SheetDualMode.STACKED.dual && SheetDualMode.STACKED.stacked)
        assertTrue(SheetDualMode.INLINE.dual)
        assertFalse(SheetDualMode.INLINE.stacked)
        assertFalse(SheetDualMode.SINGLE.dual || SheetDualMode.SINGLE.stacked)
    }
}
