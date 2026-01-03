package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Test

class DeterministicTierAssignerTest {

    private data class S(
        val id: String,
        val start: Double,
        val end: Double,
        val kind: SpanKind
    )

    @Test
    fun `single local span gets tier 0`() {
        val spans = listOf(S("L1", 0.0, 10.0, SpanKind.LOCAL))
        val tiers = DeterministicTierAssigner.assign(
            spans = spans,
            startMm = { it.start },
            endMm = { it.end },
            kind = { it.kind },
        )
        assertEquals(mapOf("L1" to 0), tiers.associate { it.item.id to it.tier })
    }

    @Test
    fun `overlapping locals may share tier 0`() {
        val spans = listOf(
            S("L1", 20.0, 120.0, SpanKind.LOCAL),
            S("L2", 60.0, 140.0, SpanKind.LOCAL),
        )

        val tiers = DeterministicTierAssigner.assign(
            spans = spans,
            startMm = { it.start },
            endMm = { it.end },
            kind = { it.kind },
        )

        assertEquals(
            mapOf(
                "L1" to 0,
                "L2" to 0,
            ),
            tiers.associate { it.item.id to it.tier }
        )
    }

    @Test
    fun `datum overlapping a local must stair-step above it`() {
        // This models the problematic liner case:
        // - A local length span from 20..120
        // - A different liner's SET->edge datum span from 0..60 that cuts across 20..120
        // Desired: local stays low; datum bumps above.
        val spans = listOf(
            S("LOCAL", 20.0, 120.0, SpanKind.LOCAL),
            S("DATUM", 0.0, 60.0, SpanKind.DATUM),
        )

        val tiers = DeterministicTierAssigner.assign(
            spans = spans,
            startMm = { it.start },
            endMm = { it.end },
            kind = { it.kind },
        )

        assertEquals(
            mapOf(
                "LOCAL" to 0,
                "DATUM" to 1,
            ),
            tiers.associate { it.item.id to it.tier }
        )
    }

    @Test
    fun `taper local stays low while overlapping datum bumps above`() {
        val spans = listOf(
            S("TAPER", 0.0, 200.0, SpanKind.LOCAL),
            S("OFFSET", 0.0, 60.0, SpanKind.DATUM),
        )

        val tiers = DeterministicTierAssigner.assign(
            spans = spans,
            startMm = { it.start },
            endMm = { it.end },
            kind = { it.kind },
        )

        assertEquals(
            mapOf(
                "TAPER" to 0,
                "OFFSET" to 1,
            ),
            tiers.associate { it.item.id to it.tier }
        )
    }

    @Test
    fun `datum offsets stair-step outward when same start`() {
        // Typical machinist convention: larger offset is farther from the part.
        val spans = listOf(
            S("D10", 0.0, 10.0, SpanKind.DATUM),
            S("D60", 0.0, 60.0, SpanKind.DATUM),
            S("D20", 0.0, 20.0, SpanKind.DATUM),
        )

        val tiers = DeterministicTierAssigner.assign(
            spans = spans,
            startMm = { it.start },
            endMm = { it.end },
            kind = { it.kind },
        )

        assertEquals(
            mapOf(
                "D10" to 0,
                "D20" to 1,
                "D60" to 2,
            ),
            tiers.associate { it.item.id to it.tier }
        )
    }

    @Test
    fun `endpoints touching do not force a bump`() {
        val spans = listOf(
            S("D1", 0.0, 20.0, SpanKind.DATUM),
            S("L1", 20.0, 120.0, SpanKind.LOCAL),
        )

        val tiers = DeterministicTierAssigner.assign(
            spans = spans,
            startMm = { it.start },
            endMm = { it.end },
            kind = { it.kind },
        )

        // Touching at x=20 is allowed; both can be on tier 0.
        assertEquals(
            mapOf(
                "D1" to 0,
                "L1" to 0,
            ),
            tiers.associate { it.item.id to it.tier }
        )
    }
}
