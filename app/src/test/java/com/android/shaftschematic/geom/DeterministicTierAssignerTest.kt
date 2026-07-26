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
    fun `nested FWD-anchored datums stack inner to outer in raw x-space`() {
        // FWD-anchored chains share their END (the FWD SET), not their start. In AUTO
        // tiering mode coordinates are raw x, so sorting datums by start put the
        // CONTAINING span on the lower rail — and the nested span's extension line cut
        // straight through it. Shorter datum must take the lower rail.
        val spans = listOf(
            S("D_LONG", 101.0, 158.125, SpanKind.DATUM),   // FWD SET → aft liner fwd edge
            S("D_SHORT", 136.0, 158.125, SpanKind.DATUM),  // FWD SET → fwd liner fwd edge
        )

        val tiers = DeterministicTierAssigner.assign(
            spans = spans,
            startMm = { it.start },
            endMm = { it.end },
            kind = { it.kind },
        ).associate { it.item.id to it.tier }

        assertEquals(0, tiers.getValue("D_SHORT"))
        assertEquals(1, tiers.getValue("D_LONG"))
    }

    @Test
    fun `on-device shaft 2026-07-26 - no extension line crosses a lower rail`() {
        // Repro of the on-device drawing: three liner locals plus two FWD-anchored
        // datums (57 1/8" and 22 1/8"), AUTO mode (raw x). The 57 1/8" span contains the
        // 22 1/8" span, so it must sit on a HIGHER rail.
        val spans = listOf(
            S("LOCAL_36", 65.0, 101.0, SpanKind.LOCAL),
            S("LOCAL_16_MID", 117.0, 133.0, SpanKind.LOCAL),
            S("LOCAL_16_FWD", 142.125, 158.125, SpanKind.LOCAL),
            S("DATUM_57", 101.0, 158.125, SpanKind.DATUM),
            S("DATUM_22", 136.0, 158.125, SpanKind.DATUM),
        )

        val assigned = DeterministicTierAssigner.assign(
            spans = spans,
            startMm = { it.start },
            endMm = { it.end },
            kind = { it.kind },
        )
        val tiers = assigned.associate { it.item.id to it.tier }

        assertEquals(0, tiers.getValue("LOCAL_36"))
        assertEquals(0, tiers.getValue("LOCAL_16_MID"))
        assertEquals(0, tiers.getValue("LOCAL_16_FWD"))
        // 22 1/8" hugs the first free rail; the containing 57 1/8" stacks above it.
        assertEquals(1, tiers.getValue("DATUM_22"))
        assertEquals(2, tiers.getValue("DATUM_57"))

        // Invariant: no span's extension line (vertical drop at its endpoints) may pass
        // strictly through the interior of a span on a lower tier.
        val eps = 1e-3
        for (upper in assigned) {
            for (lower in assigned) {
                if (lower.tier >= upper.tier) continue
                for (x in listOf(upper.item.start, upper.item.end)) {
                    val cuts = x > lower.item.start + eps && x < lower.item.end - eps
                    org.junit.Assert.assertFalse(
                        "${upper.item.id} endpoint $x cuts through ${lower.item.id} on tier ${lower.tier}",
                        cuts
                    )
                }
            }
        }
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
