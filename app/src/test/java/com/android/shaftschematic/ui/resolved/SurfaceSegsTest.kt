package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.geom.minOuterDiaOver
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.model.LinerShoulder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SurfaceSegs — the resolved → outer-surface mapping every undercut/wear draw site reads.
 *
 * The point of the shoulder segs is what `outerDiaAt`/`minOuterDiaOver` answer over a
 * shouldered end: a reading or a cut there must see the REDUCED diameter, not the liner OD.
 */
class SurfaceSegsTest {

    private val eps = 1e-3f

    private fun liner(
        start: Float = 100f,
        end: Float = 400f,
        od: Float = 120f,
        aft: LinerShoulder? = null,
        fwd: LinerShoulder? = null,
    ) = ResolvedLiner(
        id = "ln1",
        startMmPhysical = start,
        endMmPhysical = end,
        odMm = od,
        shoulderAft = aft,
        shoulderFwd = fwd,
    )

    // ── linerSurfaceSegs ─────────────────────────────────────────────────────

    @Test
    fun `a square liner is one constant-diameter seg`() {
        val segs = surfaceSegsFrom(listOf(liner()))
        assertEquals(1, segs.size)
        assertEquals(100f, segs[0].startMm, eps)
        assertEquals(400f, segs[0].endMm, eps)
        assertEquals(120f, segs[0].diaStartMm, eps)
        assertEquals(120f, segs[0].diaEndMm, eps)
    }

    @Test
    fun `both shoulders give three segs at the TRUE mm spans`() {
        val segs = surfaceSegsFrom(
            listOf(
                liner(
                    aft = LinerShoulder(lenMm = 25f, odMm = 110f, radiusMm = 3f),
                    fwd = LinerShoulder(lenMm = 40f, odMm = 105f, radiusMm = 3f),
                )
            )
        )
        assertEquals(3, segs.size)
        assertEquals(100f, segs[0].startMm, eps); assertEquals(125f, segs[0].endMm, eps)
        assertEquals(110f, segs[0].diaStartMm, eps)
        assertEquals(125f, segs[1].startMm, eps); assertEquals(360f, segs[1].endMm, eps)
        assertEquals(120f, segs[1].diaStartMm, eps)
        assertEquals(360f, segs[2].startMm, eps); assertEquals(400f, segs[2].endMm, eps)
        assertEquals(105f, segs[2].diaStartMm, eps)
    }

    @Test
    fun `one shouldered end leaves the rest at full OD`() {
        val segs = surfaceSegsFrom(listOf(liner(aft = LinerShoulder(25f, 110f, 0f))))
        assertEquals(2, segs.size)
        assertEquals(110f, segs[0].diaStartMm, eps)
        assertEquals(125f, segs[1].startMm, eps)
        assertEquals(120f, segs[1].diaStartMm, eps)
        assertEquals(400f, segs[1].endMm, eps)
    }

    @Test
    fun `a shoulder OD at or above the liner OD contributes no step`() {
        // Matches shoulderDrawSpec's null: there is nothing to show, and nothing is rewritten.
        val equal = surfaceSegsFrom(listOf(liner(aft = LinerShoulder(25f, 120f, 0f))))
        assertEquals(1, equal.size)
        assertEquals(120f, equal[0].diaStartMm, eps)

        val bigger = surfaceSegsFrom(listOf(liner(fwd = LinerShoulder(25f, 140f, 0f))))
        assertEquals(1, bigger.size)
        assertEquals(120f, bigger[0].diaStartMm, eps)
    }

    @Test
    fun `overlapping shoulder lengths clamp without inverting, aft winning`() {
        // 250 + 250 on a 300 mm liner: the aft shoulder keeps its full length, the fwd one
        // takes what is left, and no seg runs backwards or overlaps.
        val segs = surfaceSegsFrom(
            listOf(
                liner(
                    aft = LinerShoulder(250f, 110f, 0f),
                    fwd = LinerShoulder(250f, 105f, 0f),
                )
            )
        )
        assertEquals(2, segs.size)
        assertEquals(100f, segs[0].startMm, eps); assertEquals(350f, segs[0].endMm, eps)
        assertEquals(110f, segs[0].diaStartMm, eps)
        assertEquals(350f, segs[1].startMm, eps); assertEquals(400f, segs[1].endMm, eps)
        assertEquals(105f, segs[1].diaStartMm, eps)
        segs.forEach { assertTrue("seg inverted: $it", it.endMm > it.startMm) }
        for (i in 1 until segs.size) {
            assertTrue("segs overlap at $i", segs[i].startMm >= segs[i - 1].endMm - eps)
        }
    }

    @Test
    fun `a single shoulder longer than the liner swallows it without inverting`() {
        val segs = surfaceSegsFrom(listOf(liner(aft = LinerShoulder(900f, 110f, 0f))))
        assertEquals(1, segs.size)
        assertEquals(100f, segs[0].startMm, eps)
        assertEquals(400f, segs[0].endMm, eps)
        assertEquals(110f, segs[0].diaStartMm, eps)
    }

    // ── what the envelope then answers ───────────────────────────────────────

    @Test
    fun `the envelope reads the shoulder OD over a shouldered end`() {
        val segs = surfaceSegsFrom(
            listOf(
                liner(
                    aft = LinerShoulder(25f, 110f, 0f),
                    fwd = LinerShoulder(40f, 105f, 0f),
                )
            )
        )
        assertEquals(110f, outerDiaAt(segs, 110f), eps)
        assertEquals(120f, outerDiaAt(segs, 250f), eps)
        assertEquals(105f, outerDiaAt(segs, 380f), eps)
        assertEquals(110f, minOuterDiaOver(segs, 105f, 120f), eps)
        assertEquals(105f, minOuterDiaOver(segs, 365f, 395f), eps)
    }

    @Test
    fun `a body under the shoulder pokes through where it is larger`() {
        // Max-wins is the whole envelope rule — a fat shaft under a thin shoulder IS the
        // surface there, and no special case is needed to say so.
        val body = ResolvedBody(
            id = "b1",
            type = ResolvedComponentType.BODY,
            source = ResolvedComponentSource.EXPLICIT,
            startMmPhysical = 0f,
            endMmPhysical = 500f,
            diaMm = 115f,
        )
        val segs = surfaceSegsFrom(listOf(body, liner(aft = LinerShoulder(25f, 110f, 0f))))
        assertEquals(115f, outerDiaAt(segs, 110f), eps) // body wins under the shoulder
        assertEquals(120f, outerDiaAt(segs, 250f), eps) // liner wins over its full OD
    }
}
