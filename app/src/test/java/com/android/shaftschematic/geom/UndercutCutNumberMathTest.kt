package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Undercut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [undercutCutNumberPlacements]: where each cut's sheet number prints — inside its section core
 * on the widest uncut stretch of its own floor, BELOW the shaft when that stretch is too narrow
 * (the baseline is always `cy + r(baselineDiaMm)`; an outside glyph's top sits a pad under the
 * outer surface), outside labels spread apart in order.
 */
class UndercutCutNumberMathTest {

    /** One glyph = 4 mm wide; labels are digits, so a label's width is its length × 4. */
    private val glyphW: (String) -> Float = { it.length * 4f }
    private val glyphH = 6f
    private val pad = 1f

    private fun notch(id: String, start: Float, end: Float, floor: Float, surface: Float = 200f) = UndercutNotch(
        id = id, startMm = start, endMm = end, floorDiaMm = floor,
        profiles = listOf(
            NotchProfile(start, end, floor, listOf(SurfacePoint(start, surface), SurfacePoint(end, surface))),
        ),
    )

    private fun place(notches: List<UndercutNotch>, numbers: Map<String, Int>) =
        undercutCutNumberPlacements(notches, numbers, glyphW, glyphH, pad).associateBy { it.id }

    private fun placeAll(notches: List<UndercutNotch>, numbers: Map<String, Int>) =
        undercutCutNumberPlacements(notches, numbers, glyphW, glyphH, pad).groupBy { it.id }

    @Test
    fun `a plain cut prints its number inside, centred on the span and on the centreline`() {
        val p = place(listOf(notch("a", 100f, 150f, floor = 180f)), mapOf("a" to 1)).getValue("a")

        assertTrue(p.inside)
        assertEquals("1", p.label)
        assertEquals(125f, p.xMm, 1e-4f)
        assertEquals("baseline half a glyph below the centreline", 3f, p.baselineDiaMm, 1e-4f)
    }

    @Test
    fun `only width decides - a shallow core still takes its number on the centreline`() {
        // Floor Ø 2 is shallower than the 6-unit glyph; the full-height core still has the centreline.
        val p = place(listOf(notch("a", 100f, 150f, floor = 2f)), mapOf("a" to 1)).getValue("a")

        assertTrue(p.inside)
        assertEquals(3f, p.baselineDiaMm, 1e-4f)
    }

    @Test
    fun `a parent split by a centred child carries its number on both pieces, the child's stays inside the child`() {
        // Relief 100..300 with a deeper section 130..180: pieces 30 and 120 wide.
        val placed = placeAll(
            listOf(notch("relief", 100f, 300f, floor = 180f), notch("deep", 130f, 180f, floor = 160f)),
            mapOf("relief" to 1, "deep" to 2),
        )
        val relief = placed.getValue("relief")
        val deep = placed.getValue("deep").single()

        assertEquals("one label per piece", 2, relief.size)
        assertTrue(relief.all { it.inside && it.label == "1" })
        assertEquals("centred on the 100..130 piece", 115f, relief[0].xMm, 1e-4f)
        assertEquals("centred on the 180..300 piece", 240f, relief[1].xMm, 1e-4f)
        assertTrue(deep.inside)
        assertEquals("centred on the child's own span", 155f, deep.xMm, 1e-4f)
        assertEquals("all on the centreline", relief[0].baselineDiaMm, deep.baselineDiaMm, 1e-4f)
    }

    @Test
    fun `a child flush with one parent end leaves one piece and one parent label`() {
        val relief = placeAll(
            listOf(notch("relief", 100f, 300f, floor = 180f), notch("deep", 100f, 160f, floor = 160f)),
            mapOf("relief" to 1, "deep" to 2),
        ).getValue("relief")

        assertEquals(1, relief.size)
        assertEquals(230f, relief.single().xMm, 1e-4f)
    }

    @Test
    fun `a piece too narrow gets no label, and a parent with no piece that fits goes outside once`() {
        // 100..300 with a child 103..297: pieces 3 wide each, label needs 6.
        val none = placeAll(
            listOf(notch("relief", 100f, 300f, floor = 180f, surface = 220f), notch("deep", 103f, 297f, floor = 160f)),
            mapOf("relief" to 1, "deep" to 2),
        ).getValue("relief")
        assertEquals(1, none.size)
        assertFalse(none.single().inside)
        assertEquals(200f, none.single().xMm, 1e-4f)
        assertEquals("below the 220 surface: pad + glyph", 227f, none.single().baselineDiaMm, 1e-4f)

        // 100..300 with a child 103..250: the 3-wide AFT piece is skipped, the 50-wide FWD one labelled.
        val one = placeAll(
            listOf(notch("relief", 100f, 300f, floor = 180f), notch("deep", 103f, 250f, floor = 160f)),
            mapOf("relief" to 1, "deep" to 2),
        ).getValue("relief")
        assertEquals(1, one.size)
        assertTrue(one.single().inside)
        assertEquals(275f, one.single().xMm, 1e-4f)
    }

    @Test
    fun `a stretch too narrow for the label sends the number below the shaft`() {
        // 5 mm wide, label needs 4 + 2·pad = 6.
        val p = place(listOf(notch("g", 100f, 105f, floor = 190f, surface = 210f)), mapOf("g" to 7)).getValue("g")

        assertFalse(p.inside)
        assertEquals("centred on the cut", 102.5f, p.xMm, 1e-4f)
        // Measured DOWN from the centreline: the glyph's top a pad under the bottom surface, its
        // baseline a glyph below that.
        assertEquals("glyph top a pad below the outer surface", 210f + pad, p.baselineDiaMm - glyphH, 1e-4f)
        assertEquals(217f, p.baselineDiaMm, 1e-4f)
    }

    @Test
    fun `a nested cut that goes outside clears the outer surface, not its parent's floor`() {
        val placed = place(
            listOf(notch("relief", 100f, 300f, floor = 180f, surface = 220f), notch("pin", 200f, 203f, floor = 170f, surface = 180f)),
            mapOf("relief" to 1, "pin" to 2),
        )
        val pin = placed.getValue("pin")

        assertFalse(pin.inside)
        // Below the relief's 220 outer surface on the bottom side, not the pin's own 180 surface
        // (its parent's floor).
        assertEquals(227f, pin.baselineDiaMm, 1e-4f)
        assertTrue("the glyph top clears the bottom surface", pin.baselineDiaMm - glyphH > 220f)
    }

    @Test
    fun `overlapping outside labels spread apart minimally, keeping their order`() {
        // Two 2 mm grooves 3 mm apart: both outside, centres 101 and 104; the labels need
        // 4/2 + 4/2 + pad = 5 mm between centres, so each moves 1 mm away from the other.
        val placed = place(
            listOf(notch("a", 100f, 102f, floor = 190f), notch("b", 103f, 105f, floor = 190f)),
            mapOf("a" to 1, "b" to 2),
        )
        val a = placed.getValue("a")
        val b = placed.getValue("b")

        assertFalse(a.inside)
        assertFalse(b.inside)
        assertEquals(100f, a.xMm, 1e-4f)
        assertEquals(105f, b.xMm, 1e-4f)
        // Both on the one row below the shaft: the spread is horizontal only.
        assertEquals(207f, a.baselineDiaMm, 1e-4f)
        assertEquals(a.baselineDiaMm, b.baselineDiaMm, 1e-4f)
        // Far-apart outside labels stay where they are.
        val far = place(
            listOf(notch("a", 100f, 102f, floor = 190f), notch("b", 200f, 202f, floor = 190f)),
            mapOf("a" to 1, "b" to 2),
        )
        assertEquals(101f, far.getValue("a").xMm, 1e-4f)
        assertEquals(201f, far.getValue("b").xMm, 1e-4f)
    }

    @Test
    fun `numbers come from the sheet numbering, AFT to FWD, and a cut without one draws nothing`() {
        // Record order FWD first; the sheet numbers them by position.
        val cuts = listOf(
            Undercut(id = "fwd", startFromAftMm = 500f, lengthMm = 40f, diaMm = 180f),
            Undercut(id = "aft", startFromAftMm = 100f, lengthMm = 40f, diaMm = 180f),
        )
        val numbers = undercutSheetNumbers(cuts, oalMm = 1000f)
        val placed = place(
            listOf(
                notch("fwd", 500f, 540f, floor = 180f),
                notch("aft", 100f, 140f, floor = 180f),
                notch("draft", 700f, 740f, floor = 180f),
            ),
            numbers,
        )

        assertEquals("1", placed.getValue("aft").label)
        assertEquals("2", placed.getValue("fwd").label)
        assertFalse("a draft not in the record has no number", "draft" in placed)
        assertTrue(undercutCutNumberPlacements(emptyList(), numbers, glyphW, glyphH, pad).isEmpty())
    }
}
