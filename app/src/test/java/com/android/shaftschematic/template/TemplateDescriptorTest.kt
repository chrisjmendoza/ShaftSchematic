package com.android.shaftschematic.template

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The template descriptor is what tells two same-bucket templates apart — "Ø8, three liners" twice
 * over, differing only in where those liners sit. These pin the derivation, the composed caption,
 * and the name the save dialog seeds from it.
 */
class TemplateDescriptorTest {

    private val inch = 25.4f

    /** A 90" shaft: thirds break at 30" and 60". */
    private val spanIn = 90f
    private val spanMm = spanIn * inch

    private fun linerCenteredAt(id: String, centerMm: Float, lengthMm: Float = 100f, odIn: Float = 6f) =
        Liner(
            id = id,
            startFromAftMm = centerMm - lengthMm / 2f,
            lengthMm = lengthMm,
            odMm = odIn * inch,
        )

    private fun shaft(
        liners: List<Liner> = emptyList(),
        overallLengthMm: Float = spanMm,
        bodyDiaIn: Float = 4f,
        tapers: List<Taper> = emptyList(),
        threads: List<Threads> = emptyList(),
    ) = ShaftSpec(
        overallLengthMm = overallLengthMm,
        bodies = listOf(Body(startFromAftMm = 0f, lengthMm = spanMm, diaMm = bodyDiaIn * inch)),
        liners = liners,
        tapers = tapers,
        threads = threads,
    )

    // ── Liner zones ───────────────────────────────────────────────────────────

    @Test
    fun `three liners one per third read aft to forward`() {
        val spec = shaft(
            listOf(
                linerCenteredAt("a", 15f * inch),
                linerCenteredAt("m", 45f * inch),
                linerCenteredAt("f", 75f * inch),
            )
        )
        assertEquals("A·M·F", linerZoneString(spec))
    }

    @Test
    fun `two aft and one forward read A A F`() {
        val spec = shaft(
            listOf(
                linerCenteredAt("a1", 10f * inch),
                linerCenteredAt("a2", 20f * inch),
                linerCenteredAt("f", 75f * inch),
            )
        )
        assertEquals("A·A·F", linerZoneString(spec))
    }

    @Test
    fun `zones report in position order, not list order`() {
        // Authored forward-first; the descriptor still reads AFT→FWD.
        val spec = shaft(
            listOf(
                linerCenteredAt("f", 75f * inch),
                linerCenteredAt("a", 15f * inch),
            )
        )
        assertEquals("A·F", linerZoneString(spec))
    }

    @Test
    fun `a single liner reports its one zone`() {
        assertEquals("M", linerZoneString(shaft(listOf(linerCenteredAt("m", 45f * inch)))))
        assertEquals("A", linerZoneString(shaft(listOf(linerCenteredAt("a", 5f * inch)))))
        assertEquals("F", linerZoneString(shaft(listOf(linerCenteredAt("f", 88f * inch)))))
    }

    @Test
    fun `a centre exactly on a third boundary belongs to the forward side`() {
        // Boundaries tile the shaft with no gap and no overlap: 1/3 reads M, 2/3 reads F.
        assertEquals("M", linerZoneString(shaft(listOf(linerCenteredAt("b", spanMm / 3f)))))
        assertEquals("F", linerZoneString(shaft(listOf(linerCenteredAt("b", spanMm * 2f / 3f)))))
        // A hair below each boundary stays on the aft side.
        assertEquals("A", linerZoneString(shaft(listOf(linerCenteredAt("b", spanMm / 3f - 1f)))))
        assertEquals("M", linerZoneString(shaft(listOf(linerCenteredAt("b", spanMm * 2f / 3f - 1f)))))
    }

    @Test
    fun `an unset OAL falls back to the covered span`() {
        val spec = shaft(
            liners = listOf(linerCenteredAt("a", 15f * inch), linerCenteredAt("f", 75f * inch)),
            overallLengthMm = 0f,
        )
        // The body still covers 0..90", so the thirds are the same as with OAL set.
        assertEquals("A·F", linerZoneString(spec))
    }

    @Test
    fun `no liners means no zone string`() {
        assertNull(linerZoneString(shaft()))
    }

    @Test
    fun `an unfinished zero-OD liner is not a liner to place`() {
        val spec = shaft(listOf(linerCenteredAt("x", 45f * inch, odIn = 0f)))
        assertNull(linerZoneString(spec))
    }

    @Test
    fun `nothing to measure against means no zone string`() {
        val spec = ShaftSpec(
            overallLengthMm = 0f,
            liners = listOf(Liner(id = "l", startFromAftMm = 0f, lengthMm = 0f, odMm = 6f * inch)),
        )
        assertNull(linerZoneString(spec))
    }

    // ── Descriptor composition ────────────────────────────────────────────────

    @Test
    fun `the descriptor reads OAL, max diameter, count and zones`() {
        val spec = shaft(
            listOf(
                linerCenteredAt("a", 15f * inch),
                linerCenteredAt("m", 45f * inch),
                linerCenteredAt("f", 75f * inch),
            )
        )
        assertEquals(
            "90\" OAL · Ø6\" max · 3 liners (A·M·F)",
            templateDescriptor(spec, UnitSystem.INCHES),
        )
    }

    @Test
    fun `two templates of one bucket differ only by their zones`() {
        val spread = shaft(
            listOf(
                linerCenteredAt("a", 15f * inch),
                linerCenteredAt("m", 45f * inch),
                linerCenteredAt("f", 75f * inch),
            )
        )
        val clustered = shaft(
            listOf(
                linerCenteredAt("a1", 10f * inch),
                linerCenteredAt("a2", 20f * inch),
                linerCenteredAt("f", 75f * inch),
            )
        )
        assertEquals(templateSizeBucket(spread), templateSizeBucket(clustered))
        assertEquals(templateLinerCount(spread), templateLinerCount(clustered))
        assertTrue(
            templateDescriptor(spread, UnitSystem.INCHES) !=
                templateDescriptor(clustered, UnitSystem.INCHES)
        )
    }

    @Test
    fun `the max diameter is canonical — a thread bigger than every liner still wins`() {
        val spec = shaft(
            liners = listOf(linerCenteredAt("a", 15f * inch)),
            threads = listOf(
                Threads(id = "t", startFromAftMm = 0f, lengthMm = 100f, majorDiaMm = 9f * inch)
            ),
        )
        assertTrue(templateDescriptor(spec, UnitSystem.INCHES).contains("Ø9\" max"))
    }

    @Test
    fun `a taper bigger than every liner also wins`() {
        val spec = shaft(
            liners = listOf(linerCenteredAt("a", 15f * inch)),
            tapers = listOf(
                Taper(
                    id = "tp",
                    startFromAftMm = 0f,
                    lengthMm = 200f,
                    startDiaMm = 10f * inch,
                    endDiaMm = 7f * inch,
                )
            ),
        )
        assertTrue(templateDescriptor(spec, UnitSystem.INCHES).contains("Ø10\" max"))
    }

    @Test
    fun `a linerless shaft says No liners once and carries no zones`() {
        val descriptor = templateDescriptor(shaft(), UnitSystem.INCHES)
        assertEquals("90\" OAL · Ø4\" max · No liners", descriptor)
        assertEquals(1, Regex("No liners").findAll(descriptor).count())
    }

    @Test
    fun `the descriptor formats in the unit it is asked for`() {
        val spec = shaft(listOf(linerCenteredAt("m", 45f * inch)))
        val mm = templateDescriptor(spec, UnitSystem.MILLIMETERS)
        assertTrue(mm.contains("2286 mm OAL"))
        assertTrue(mm.contains("Ø152.4 mm max"))
    }

    // ── Bucket path ───────────────────────────────────────────────────────────

    @Test
    fun `a linerless bucket path does not say No liners twice`() {
        assertEquals("No liners", templateBucketPath(shaft()))
    }

    @Test
    fun `a lined bucket path names both axes`() {
        val spec = shaft(listOf(linerCenteredAt("a", 15f * inch), linerCenteredAt("f", 75f * inch)))
        assertEquals("6\" liners · 2 liners", templateBucketPath(spec))
    }

    // ── Suggested name ────────────────────────────────────────────────────────

    @Test
    fun `the suggested name carries size, count and zones`() {
        val spec = shaft(
            listOf(
                linerCenteredAt("a", 15f * inch),
                linerCenteredAt("m", 45f * inch),
                linerCenteredAt("f", 75f * inch),
            )
        )
        assertEquals("6in 3 liners A-M-F", suggestedTemplateName(spec, "Job 1138"))
    }

    @Test
    fun `the suggested name is filename-safe`() {
        val spec = shaft(listOf(linerCenteredAt("a", 15f * inch), linerCenteredAt("f", 75f * inch)))
        val name = suggestedTemplateName(spec, "")
        assertTrue(name.none { it in "\\/:*?\"<>|" })
        assertTrue(!name.contains(TEMPLATE_ZONE_SEPARATOR))
        assertEquals("6in 2 liners A-F", name)
    }

    @Test
    fun `a linerless shaft keeps the document-name fallback`() {
        assertEquals("Job 42", suggestedTemplateName(shaft(), "Job 42.shaft"))
        assertEquals("Straight shaft", suggestedTemplateName(shaft(), "   "))
    }

    @Test
    fun `an out-of-range liner size keeps the generic fallback`() {
        val spec = shaft(listOf(linerCenteredAt("a", 15f * inch, odIn = 14f)))
        assertEquals(TemplateSizeBucket.Other, templateSizeBucket(spec))
        assertEquals("My shaft", suggestedTemplateName(spec, "My shaft.shaft"))
        assertEquals("Shaft template", suggestedTemplateName(spec, ""))
    }

    // ── Name deduplication ────────────────────────────────────────────────────

    @Test
    fun `a free name is handed back untouched`() {
        assertEquals(
            "6in 3 liners A-M-F",
            dedupeTemplateName("6in 3 liners A-M-F", listOf("8in 2 liners A-F.shaft")),
        )
    }

    @Test
    fun `a taken name gains an ordinal`() {
        assertEquals(
            "6in 3 liners A-M-F (2)",
            dedupeTemplateName("6in 3 liners A-M-F", listOf("6in 3 liners A-M-F.shaft")),
        )
    }

    @Test
    fun `the collision check is case-insensitive`() {
        assertEquals(
            "6in 3 liners A-M-F (2)",
            dedupeTemplateName("6in 3 liners A-M-F", listOf("6IN 3 LINERS a-m-f.shaft")),
        )
    }

    @Test
    fun `ordinals skip to the first free one`() {
        val existing = listOf(
            "6in 3 liners A-M-F.shaft",
            "6in 3 liners A-M-F (2).shaft",
            "6in 3 liners A-M-F (3).shaft",
        )
        assertEquals("6in 3 liners A-M-F (4)", dedupeTemplateName("6in 3 liners A-M-F", existing))
    }

    @Test
    fun `an empty store never renames anything`() {
        assertEquals("Straight shaft", dedupeTemplateName("Straight shaft", emptyList()))
    }
}
