package com.android.shaftschematic.util

import com.android.shaftschematic.util.FractionText.Run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure half of the fraction typography system. Everything here is string-in / runs-out —
 * the Paint/Canvas side is exercised by `FractionTextRendererTest`.
 */
class FractionTextTest {

    private fun frac(n: String, d: String) = Run.Frac(n, d)
    private fun plain(s: String) = Run.Plain(s)

    @Test
    fun `bare fraction`() {
        assertEquals(listOf(frac("3", "16")), FractionText.parse("3/16"))
    }

    @Test
    fun `mixed number tightens the whole-number space`() {
        assertEquals(
            listOf(plain("12"), Run.Gap, frac("5", "8"), plain("\"")),
            FractionText.parse("12 5/8\""),
        )
    }

    @Test
    fun `fraction inside a sentence keeps its surroundings`() {
        assertEquals(
            listOf(plain("OAL:"), Run.Gap, frac("1", "2"), plain("\" from AFT")),
            FractionText.parse("OAL: 1/2\" from AFT"),
        )
    }

    @Test
    fun `several fractions in one label`() {
        assertEquals(
            listOf(frac("1", "4"), Run.Gap, plain("×"), Run.Gap, frac("1", "8")),
            FractionText.parse("1/4 × 1/8"),
        )
    }

    /** No `n/d` grid caps the renderer — an arbitrary ratio sets like any other. */
    @Test
    fun `oddball denominators are ordinary fractions`() {
        assertEquals(listOf(frac("399", "4000")), FractionText.parse("399/4000"))
        assertEquals(listOf(frac("63", "64")), FractionText.parse("63/64"))
        assertEquals(listOf(plain("7"), Run.Gap, frac("127", "128")), FractionText.parse("7 127/128"))
    }

    /**
     * The neighbour guard. These are the strings that would be silently mangled if a bare
     * `\d+/\d+` match were enough — a job number and a date both live in the footer.
     */
    @Test
    fun `dates decimals and multi-slash tokens stay plain`() {
        assertFalse(FractionText.hasFraction("12/25/2026"))
        assertFalse(FractionText.hasFraction("1.5/2"))
        assertFalse(FractionText.hasFraction("3/16.5"))
        assertFalse(FractionText.hasFraction("1/2/3"))
        assertFalse(FractionText.hasFraction("1/0"))
        assertFalse(FractionText.hasFraction("a/b"))
        assertFalse(FractionText.hasFraction("1:12"))
        assertFalse(FractionText.hasFraction("in/ft"))
    }

    @Test
    fun `unicode vulgar fractions set the same as spelled-out ones`() {
        assertEquals(FractionText.parse("5/8"), FractionText.parse("⅝"))
        assertEquals(
            listOf(plain("1"), Run.Gap, frac("1", "2")),
            FractionText.parse("1 ½"),
        )
    }

    @Test
    fun `unicode fraction slash divides like an ascii slash`() {
        assertEquals(listOf(frac("3", "16")), FractionText.parse("3⁄16"))
    }

    @Test
    fun `negative sign stays with the plain run`() {
        assertEquals(listOf(plain("-"), frac("1", "4")), FractionText.parse("-1/4"))
    }

    @Test
    fun `text with no fraction is one plain run`() {
        assertEquals(listOf(plain("Ø 5.125\"")), FractionText.parse("Ø 5.125\""))
        assertFalse(FractionText.hasFraction("Ø 5.125\""))
        assertTrue(FractionText.hasFraction("2 1/2\""))
    }

    @Test
    fun `empty text parses to nothing`() {
        assertEquals(emptyList<Run>(), FractionText.parse(""))
    }

    @Test
    fun `flatten round-trips to a plain single line`() {
        assertEquals("12 5/8\"", FractionText.flatten(FractionText.parse("12 5/8\"")))
        assertEquals("1/4 × 1/8", FractionText.flatten(FractionText.parse("1/4 × 1/8")))
        // A vulgar glyph flattens to its spelled-out form — one spelling downstream.
        assertEquals("5/8", FractionText.flatten(FractionText.parse("⅝")))
    }
}
