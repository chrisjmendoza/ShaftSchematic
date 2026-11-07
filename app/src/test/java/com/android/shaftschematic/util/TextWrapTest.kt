package com.android.shaftschematic.util

import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Footer wrapping — what replaced the `…` that used to eat a dual value's tail.
 *
 * The rule worth pinning is that wrapping never LOSES text: a footer line is a machining figure,
 * and half of one is worse than a line that runs on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextWrapTest {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }

    private fun joined(lines: List<String>) =
        lines.joinToString(" ") { it.trim() }.replace(Regex("\\s+"), " ")

    @Test
    fun `text that already fits comes back untouched, in one line`() {
        val s = "Length: 12 5/8\""
        assertEquals(listOf(s), wrapRichLines(s, paint, 10_000f, rich = true))
    }

    @Test
    fun `a long dual-unit spec line wraps instead of losing its tail`() {
        val s = "KW: 1 3/4\" [44.5 mm] × 3/4\" [19 mm] × 21 1/2\" [546.1 mm]"
        val width = paint.measureText(s) * 0.45f
        val lines = wrapRichLines(s, paint, width, rich = true)

        assertTrue("expected a wrap at this width", lines.size > 1)
        // Nothing dropped: every word survives, in order.
        assertEquals(joined(listOf(s)), joined(lines))
        assertTrue("no line may contain an ellipsis", lines.none { it.contains("…") })
    }

    @Test
    fun `continuation rows are indented so a wrapped line reads as a tail`() {
        val s = "Customer: A Very Long Marine Services Company Limited"
        val lines = wrapRichLines(s, paint, paint.measureText(s) * 0.5f, rich = false)
        assertTrue(lines.size > 1)
        assertTrue("first row is not indented", !lines.first().startsWith(" "))
        assertTrue(
            "continuation rows should carry the hanging indent",
            lines.drop(1).all { it.startsWith(CONTINUATION_INDENT.trimEnd()) || it.startsWith(" ") },
        )
    }

    @Test
    fun `a single token too wide for the column is never chopped mid-value`() {
        val s = "1234567890123456789"
        val lines = wrapRichLines(s, paint, paint.measureText(s) * 0.25f, rich = false)
        assertEquals(listOf(s), lines)
    }

    // On-device report, footer text at 175%: "KW: … × 8 1/4"" wrapped as "8" at the line end and
    // "1/4"" under it, which reads as two values. A value moves to the next line whole.

    @Test
    fun `a mixed number moves to the next line as one unit`() {
        val s = "KW: 1 3/4\" × 3/4\" × 8 1/4\""
        // Narrow enough that "8 1/4"" cannot stay on the first line, wide enough for "× 8" alone.
        val width = paint.measureRichText("KW: 1 3/4\" × 3/4\" × 8")
        val lines = wrapRichLines(s, paint, width, rich = true)

        assertTrue("expected a wrap at this width", lines.size > 1)
        assertTrue(
            "the mixed number split across rows: $lines",
            lines.any { it.contains("8 1/4\"") },
        )
        lines.forEach { row -> assertTrue("row ends on a bare whole number: '$row'", !row.trimEnd().endsWith(" 8")) }
        assertEquals(joined(listOf(s)), joined(lines))
    }

    @Test
    fun `a metric value keeps its mm`() {
        val s = "Length: 12 5/8\" [320.7 mm] × 2\" [50.8 mm]"
        val width = paint.measureRichText("Length: 12 5/8\" [320.7")
        val lines = wrapRichLines(s, paint, width, rich = true)

        assertTrue(lines.size > 1)
        lines.forEach { row ->
            assertTrue("a number was split from its mm: '$row'", !row.trimStart().startsWith("mm"))
        }
        assertTrue(lines.any { it.contains("[320.7 mm]") })
    }

    @Test
    fun `only the spaces inside a value are glued`() {
        val s = "KW: 8 1/4\" [209.6 mm] x 2 TPI"
        assertTrue(isGluedSpace(s, s.indexOf(" 1/4")))       // 8 ␣1/4
        assertTrue(isGluedSpace(s, s.indexOf(" mm")))        // 209.6 ␣mm
        assertTrue(!isGluedSpace(s, s.indexOf(" 8")))        // KW: ␣8 — a label, breakable
        assertTrue(!isGluedSpace(s, s.indexOf(" TPI")))      // 2 ␣TPI — not mm, breakable
        assertTrue(!isGluedSpace(s, s.indexOf(" [")))        // 1/4" ␣[ — between two values
    }

    @Test
    fun `every wrapped row fits the width when the text has somewhere to break`() {
        val s = "Thread: 5.25\" [133.4 mm] × 4 TPI × 5 13/16\" [147.6 mm]"
        val width = paint.measureText(s) * 0.5f
        val lines = wrapRichLines(s, paint, width, rich = true)
        // Rows may only overflow when a single token is itself wider than the column.
        lines.forEach { row ->
            val fits = paint.measureRichText(row) <= width + 0.5f
            val singleToken = row.trim().none { it == ' ' }
            assertTrue("row overflowed: '$row'", fits || singleToken)
        }
    }
}
