package com.android.shaftschematic.pdf.dim

import org.junit.Assert.assertEquals
import org.junit.Test

class SpanDedupingTest {

    @Test
    fun `identical spans are deduped before tiering`() {
        val a = DimSpan(
            x1Mm = 0.0,
            x2Mm = 406.4,
            labelTop = "16 in",
            kind = SpanKind.LOCAL
        )
        val b = DimSpan(
            x1Mm = 0.0,
            x2Mm = 406.4,
            labelTop = "16 in",
            kind = SpanKind.LOCAL
        )

        val rails = RailPlanner().assignAll(listOf(a, b))
        assertEquals(1, rails.size)
        assertEquals(0, rails.single().rail)
    }

    @Test
    fun `spans with same endpoints but different labels are not deduped`() {
        val a = DimSpan(
            x1Mm = 0.0,
            x2Mm = 406.4,
            labelTop = "16 in",
            kind = SpanKind.LOCAL
        )
        val b = DimSpan(
            x1Mm = 0.0,
            x2Mm = 406.4,
            labelTop = "16.0 in",
            kind = SpanKind.LOCAL
        )

        val rails = RailPlanner().assignAll(listOf(a, b))
        assertEquals(2, rails.size)
    }

    @Test
    fun `identical spans across kinds are deduped preferring local`() {
        val datum = DimSpan(
            x1Mm = 0.0,
            x2Mm = 406.4,
            labelTop = "16 in",
            kind = SpanKind.DATUM
        )
        val local = DimSpan(
            x1Mm = 0.0,
            x2Mm = 406.4,
            labelTop = "16 in",
            kind = SpanKind.LOCAL
        )

        val rails = RailPlanner().assignAll(listOf(datum, local))
        assertEquals(1, rails.size)
        assertEquals(SpanKind.LOCAL, rails.single().span.kind)
    }
}
