package com.android.shaftschematic.template

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Template bucketing is derived from the stored spec on every scan — no index file, so an
 * edited template re-files itself. These pin the derivation.
 */
class TemplateBucketsTest {

    private val inch = 25.4f

    private fun specWithLinerOds(vararg odsIn: Float) = ShaftSpec(
        overallLengthMm = 2400f,
        bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 2400f, diaMm = 4f * inch)),
        liners = odsIn.mapIndexed { i, od ->
            Liner(id = "l$i", startFromAftMm = 200f + i * 500f, lengthMm = 300f, odMm = od * inch)
        },
    )

    // ── Size bucket ───────────────────────────────────────────────────────────

    @Test
    fun `a six inch liner files under six inches`() {
        assertEquals(TemplateSizeBucket.Inches(6), templateSizeBucket(specWithLinerOds(6f)))
    }

    @Test
    fun `an off-round liner rounds to the nearest inch`() {
        assertEquals(TemplateSizeBucket.Inches(6), templateSizeBucket(specWithLinerOds(6.2f)))
        assertEquals(TemplateSizeBucket.Inches(7), templateSizeBucket(specWithLinerOds(6.6f)))
    }

    @Test
    fun `mixed liner sizes bucket by the largest`() {
        assertEquals(TemplateSizeBucket.Inches(8), templateSizeBucket(specWithLinerOds(5f, 8f, 6f)))
    }

    @Test
    fun `the four and twelve inch boundaries are inside the range`() {
        assertEquals(TemplateSizeBucket.Inches(4), templateSizeBucket(specWithLinerOds(4f)))
        assertEquals(TemplateSizeBucket.Inches(12), templateSizeBucket(specWithLinerOds(12f)))
    }

    @Test
    fun `sizes outside the range fall into Other, never dropped`() {
        assertEquals(TemplateSizeBucket.Other, templateSizeBucket(specWithLinerOds(3f)))
        assertEquals(TemplateSizeBucket.Other, templateSizeBucket(specWithLinerOds(14f)))
    }

    @Test
    fun `half-inch ODs round up, even stored as Float millimetres`() {
        // 3.5" = 88.9mm has no exact Float representation. The bucket divide runs in Double
        // so an on-the-boundary OD can never land a Float ULP below the round-half point and
        // flip down a bucket.
        assertEquals(TemplateSizeBucket.Inches(4), templateSizeBucket(specWithLinerOds(3.5f)))
        assertEquals(TemplateSizeBucket.Inches(12), templateSizeBucket(specWithLinerOds(11.5f)))
        // 12.5 rounds up and out of the 4"–12" range.
        assertEquals(TemplateSizeBucket.Other, templateSizeBucket(specWithLinerOds(12.5f)))
    }

    @Test
    fun `a linerless shaft gets its own bucket`() {
        val straight = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 1000f, diaMm = 100f)),
        )
        assertEquals(TemplateSizeBucket.None, templateSizeBucket(straight))
    }

    @Test
    fun `an unfinished zero-OD liner does not count as a liner`() {
        assertEquals(TemplateSizeBucket.None, templateSizeBucket(specWithLinerOds(0f)))
        assertEquals(TemplateLinerCount.NONE, templateLinerCount(specWithLinerOds(0f)))
    }

    // ── Count bucket ──────────────────────────────────────────────────────────

    @Test
    fun `liner counts bucket one through three, then three plus`() {
        assertEquals(TemplateLinerCount.ONE, templateLinerCount(specWithLinerOds(6f)))
        assertEquals(TemplateLinerCount.TWO, templateLinerCount(specWithLinerOds(6f, 6f)))
        assertEquals(TemplateLinerCount.THREE, templateLinerCount(specWithLinerOds(6f, 6f, 6f)))
        assertEquals(TemplateLinerCount.THREE_PLUS, templateLinerCount(specWithLinerOds(6f, 6f, 6f, 6f)))
    }

    // ── Ordering ──────────────────────────────────────────────────────────────

    @Test
    fun `buckets sort as no-liners, ascending sizes, then other`() {
        val buckets = listOf(
            TemplateSizeBucket.Other,
            TemplateSizeBucket.Inches(8),
            TemplateSizeBucket.None,
            TemplateSizeBucket.Inches(4),
        )
        val sorted = buckets.sortedBy { it.sortKey() }
        assertEquals(
            listOf(
                TemplateSizeBucket.None,
                TemplateSizeBucket.Inches(4),
                TemplateSizeBucket.Inches(8),
                TemplateSizeBucket.Other,
            ),
            sorted,
        )
    }

    @Test
    fun `every in-range size has a distinct bucket`() {
        val labels = (TEMPLATE_MIN_SIZE_IN..TEMPLATE_MAX_SIZE_IN)
            .map { TemplateSizeBucket.Inches(it).label }
        assertTrue(labels.size == labels.toSet().size)
    }
}
