package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.resolved.wearAreaHosts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a wear area sits on the shaft — the ONE resolution behind every whole-shaft draw site.
 * Hosts come from the REAL resolver: a liner, and each drawn body run (bare shaft here).
 */
class WearAreaMathTest {

    // AFT taper [0,500] · bare shaft [500,1000] · liner [1000,2000] · bare shaft [2000,4000].
    private val spec = ShaftSpec(
        overallLengthMm = 4000f,
        autoBodyDiaMm = 240f,
        tapers = listOf(Taper(id = "t", startFromAftMm = 0f, lengthMm = 500f, startDiaMm = 200f, endDiaMm = 240f)),
        liners = listOf(Liner(id = "l", startFromAftMm = 1000f, lengthMm = 1000f, odMm = 254f)),
    )
    private val resolved = resolveComponents(spec)
    private val hosts = wearAreaHosts(resolved)
    private val fwdRun = resolved.filterIsInstance<ResolvedBody>().single { it.startMmPhysical == 2000f }

    @Test fun `liners and drawn body runs host wear areas, tapers do not`() {
        assertEquals(3, hosts.size)
        assertTrue(hosts.none { it.id == "t" })
        val liner = hosts.single { it.isLiner }
        assertEquals("l", liner.id)
        assertEquals(254f, liner.diaMm, 0f)
        val body = hosts.single { it.id == fwdRun.id }
        assertEquals(2000f, body.startMm, 0f)
        assertEquals(2000f, body.lengthMm, 0f)
        assertEquals(240f, body.diaMm, 0f)
    }

    @Test fun `an area is placed from its host's AFT edge, in shaft space`() {
        val spans = resolveWearAreaSpans(
            listOf(
                WearSpot(id = "on-liner", linerId = "l", startMm = 100f, lengthMm = 200f),
                WearSpot(id = "on-body", linerId = fwdRun.id, startMm = 300f, lengthMm = 150f),
            ),
            hosts,
        )
        assertEquals(listOf("on-liner", "on-body"), spans.map { it.spotId })
        assertEquals(1100f, spans[0].startMm, 0f)
        assertEquals(1300f, spans[0].endMm, 0f)
        assertTrue(spans[0].host.isLiner)
        assertEquals(2300f, spans[1].startMm, 0f)
        assertEquals(2450f, spans[1].endMm, 0f)
        assertEquals(240f, spans[1].host.diaMm, 0f)
    }

    @Test fun `an area whose host no longer resolves is skipped, not drawn anywhere`() {
        val spans = resolveWearAreaSpans(
            listOf(
                WearSpot(id = "gone-liner", linerId = "deleted", startMm = 10f, lengthMm = 20f),
                WearSpot(id = "gone-run", linerId = "auto_body_1.000_2.000", startMm = 10f, lengthMm = 20f),
                WearSpot(id = "kept", linerId = "l", startMm = 10f, lengthMm = 20f),
            ),
            hosts,
        )
        assertEquals(listOf("kept"), spans.map { it.spotId })
    }

    @Test fun `an area is clamped to its host for drawing and never crosses a neighbour`() {
        val spans = resolveWearAreaSpans(
            listOf(
                // Runs 300 mm past the body run's FWD end and starts 50 mm AFT of it.
                WearSpot(id = "over", linerId = fwdRun.id, startMm = -50f, lengthMm = 2350f),
                // Entirely past the liner's end: nothing to draw.
                WearSpot(id = "out", linerId = "l", startMm = 1200f, lengthMm = 100f),
                WearSpot(id = "zero", linerId = "l", startMm = 100f, lengthMm = 0f),
            ),
            hosts,
        )
        val over = spans.single()
        assertEquals("over", over.spotId)
        assertEquals("never AFT of the run, under the liner", 2000f, over.startMm, 0f)
        assertEquals(4000f, over.endMm, 0f)
    }

    @Test fun `a band stops at each side of a break gap`() {
        assertEquals(listOf(10f..40f), spanPiecesClearOf(10f, 40f, emptyList()))
        assertEquals(listOf(10f..20f, 30f..40f), spanPiecesClearOf(10f, 40f, listOf(20f..30f)))
        assertEquals("a gap elsewhere changes nothing", listOf(10f..40f), spanPiecesClearOf(10f, 40f, listOf(50f..60f)))
        assertEquals("a band ending in the gap", listOf(10f..20f), spanPiecesClearOf(10f, 25f, listOf(20f..30f)))
        assertEquals("a band inside the gap draws nothing", emptyList<Any>(), spanPiecesClearOf(22f, 28f, listOf(20f..30f)))
        assertEquals(
            listOf(0f..10f, 20f..30f, 40f..50f),
            spanPiecesClearOf(0f, 50f, listOf(10f..20f, 30f..40f)),
        )
    }
}
