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
