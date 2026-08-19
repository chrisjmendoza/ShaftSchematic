package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BodyBlends — the derived geometry behind a blended body face. Pins the rules both draw
 * sites depend on: the blend is machined out of its OWN body, its diameters derive from
 * whatever the face steps to, and a face with nothing to step to simply stays square.
 */
class BodyBlendsTest {

    private val eps = 1e-2f

    /** A smaller aft body butted against a larger fwd one: a coupling fit that enlarges. */
    private fun steppedSpec(
        blendAftMm: Float = 0f,
        blendFwdMm: Float = 0f,
        profile: BlendProfile = BlendProfile.OGEE,
    ) = ShaftSpec(
        overallLengthMm = 400f,
        bodies = listOf(
            Body(id = "small", startFromAftMm = 0f, lengthMm = 200f, diaMm = 150f),
            Body(
                id = "big", startFromAftMm = 200f, lengthMm = 200f, diaMm = 200f,
                blendAftMm = blendAftMm, blendFwdMm = blendFwdMm, blendProfile = profile,
            ),
        ),
    )

    private fun blendsOf(spec: ShaftSpec) =
        bodyBlends(spec, resolveComponents(spec, overallIsManual = true))

    @Test
    fun `a shaft with no blend fields yields no blends`() {
        assertTrue(blendsOf(steppedSpec()).isEmpty())
    }

    @Test
    fun `an aft blend derives its diameters from the component across the face`() {
        val b = blendsOf(steppedSpec(blendAftMm = 50f)).single()
        assertEquals(LinerAuthoredReference.AFT, b.end)
        assertEquals("big", b.bodyId)
        assertEquals(200f, b.faceMm, eps)
        assertEquals(50f, b.lengthMm, eps)
        assertEquals(200f, b.bodyDiaMm, eps)      // its own diameter, reached inward
        assertEquals(150f, b.neighbourDiaMm, eps) // what it steps FROM at the face
    }

    @Test
    fun `a face with no step to make draws no blend`() {
        // Both bodies at the same diameter: nothing to blend, so the face stays square.
        val spec = ShaftSpec(
            overallLengthMm = 400f,
            bodies = listOf(
                Body(id = "a", startFromAftMm = 0f, lengthMm = 200f, diaMm = 150f),
                Body(id = "b", startFromAftMm = 200f, lengthMm = 200f, diaMm = 150f, blendAftMm = 50f),
            ),
        )
        assertTrue(blendsOf(spec).isEmpty())
    }

    @Test
    fun `a face at the open end of the shaft draws no blend`() {
        val spec = ShaftSpec(
            overallLengthMm = 200f,
            bodies = listOf(
                Body(id = "only", startFromAftMm = 0f, lengthMm = 200f, diaMm = 150f, blendAftMm = 50f),
            ),
        )
        assertTrue(blendsOf(spec).isEmpty())
    }

    @Test
    fun `a blend steps to a taper local diameter, not just another body`() {
        val spec = ShaftSpec(
            overallLengthMm = 400f,
            tapers = listOf(
                Taper(id = "t", startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 100f, endDiaMm = 140f),
            ),
            bodies = listOf(
                Body(id = "big", startFromAftMm = 200f, lengthMm = 200f, diaMm = 200f, blendAftMm = 40f),
            ),
        )
        val b = blendsOf(spec).single()
        assertEquals(140f, b.neighbourDiaMm, 1f) // the taper diameter where it meets the face
        assertEquals(200f, b.bodyDiaMm, eps)
    }

    @Test
    fun `a liner over the shaft is not a diameter the shaft steps to`() {
        // The liner OD would win an ordinary surface lookup; a sleeve is not a step.
        val spec = ShaftSpec(
            overallLengthMm = 400f,
            bodies = listOf(
                Body(id = "small", startFromAftMm = 0f, lengthMm = 200f, diaMm = 150f),
                Body(id = "big", startFromAftMm = 200f, lengthMm = 200f, diaMm = 200f, blendAftMm = 40f),
            ),
            liners = listOf(Liner(startFromAftMm = 100f, lengthMm = 60f, odMm = 300f)),
        )
        val b = blendsOf(spec).single()
        assertEquals(150f, b.neighbourDiaMm, eps)
    }

    @Test
    fun `both faces can blend independently`() {
        val spec = ShaftSpec(
            overallLengthMm = 600f,
            bodies = listOf(
                Body(id = "a", startFromAftMm = 0f, lengthMm = 200f, diaMm = 150f),
                Body(
                    id = "mid", startFromAftMm = 200f, lengthMm = 200f, diaMm = 200f,
                    blendAftMm = 30f, blendFwdMm = 40f,
                ),
                Body(id = "c", startFromAftMm = 400f, lengthMm = 200f, diaMm = 120f),
            ),
        )
        val blends = blendsOf(spec)
        assertEquals(2, blends.size)
        val aft = blends.single { it.end == LinerAuthoredReference.AFT }
        val fwd = blends.single { it.end == LinerAuthoredReference.FWD }
        assertEquals(150f, aft.neighbourDiaMm, eps)
        assertEquals(120f, fwd.neighbourDiaMm, eps)
        assertEquals(400f, fwd.faceMm, eps)
    }

    /** A blend is machined out of its own body — no other component span may move. */
    @Test
    fun `a blend moves no other component`() {
        val plain = resolveComponents(steppedSpec(), overallIsManual = true)
        val blended = resolveComponents(steppedSpec(blendAftMm = 50f), overallIsManual = true)
        assertEquals(plain.size, blended.size)
        plain.zip(blended).forEach { (a, b) ->
            assertEquals(a.startMmPhysical, b.startMmPhysical, eps)
            assertEquals(a.endMmPhysical, b.endMmPhysical, eps)
        }
    }

    @Test
    fun `a stored length longer than the body is clamped without touching what was typed`() {
        val spec = steppedSpec(blendAftMm = 900f)
        val b = blendsOf(spec).single()
        assertEquals(200f, b.lengthMm, eps)                // clamped for drawing
        assertEquals(900f, spec.bodies[1].blendAftMm, eps) // stored value untouched
    }

    // ───────── bodyDrawEdges ─────────

    private fun edges(spec: ShaftSpec, runId: String, minWidthPx: Float = 7f): BodyDrawEdges {
        val comps = resolveComponents(spec, overallIsManual = true)
        val run = comps.filterIsInstance<ResolvedBody>().single { it.id == runId }
        return bodyDrawEdges(
            runId = runId,
            runStartMm = run.startMmPhysical,
            runEndMm = run.endMmPhysical,
            runDiaMm = run.diaMm,
            blends = bodyBlends(spec, comps),
            xAt = { mm -> mm },        // 1 px per mm keeps the arithmetic readable
            rAt = { dia -> dia / 2f },
            minWidthPx = minWidthPx,
        )
    }

    @Test
    fun `an unblended body decomposes to exactly its rectangle`() {
        val e = edges(steppedSpec(), "big")
        assertEquals(200f, e.flatX0, eps)
        assertEquals(400f, e.flatX1, eps)
        assertEquals(100f, e.flatR, eps)
        assertEquals(100f, e.capAftR, eps)
        assertEquals(100f, e.capFwdR, eps)
        assertTrue(e.aftCurve.isEmpty() && e.fwdCurve.isEmpty())
    }

    @Test
    fun `a blended face shortens the flat span and caps at the neighbour radius`() {
        val e = edges(steppedSpec(blendAftMm = 50f), "big")
        assertEquals(250f, e.flatX0, eps)  // 50 mm of the run became the curve
        assertEquals(400f, e.flatX1, eps)  // the fwd face is untouched
        assertEquals(75f, e.capAftR, eps)  // neighbour at 150 -> radius 75
        assertEquals(100f, e.capFwdR, eps) // its own 200 -> radius 100
        assertTrue(e.aftCurve.isNotEmpty())
        assertEquals(75f, e.aftCurve.first().rPx, eps)
        assertEquals(100f, e.aftCurve.last().rPx, eps)
    }

    @Test
    fun `a sub-pixel blend is floored so the curve still reads on a compressed sheet`() {
        // Half a mm of blend at 1 px per mm would vanish; the floor widens the DRAWN curve only.
        val e = edges(steppedSpec(blendAftMm = 0.5f), "big", minWidthPx = 7f)
        assertEquals(207f, e.flatX0, eps)
        assertEquals(7f, e.aftCurve.last().xPx - e.aftCurve.first().xPx, eps)
    }

    /**
     * Two blends can never swallow the run between them: each is capped at 40% of its host,
     * so a flat span always survives and the silhouette can never invert. The guard inside
     * [bodyDrawEdges] is belt-and-braces behind this cap.
     */
    @Test
    fun `two blends on a short run always leave a flat span between them`() {
        val spec = ShaftSpec(
            overallLengthMm = 420f,
            bodies = listOf(
                Body(id = "a", startFromAftMm = 0f, lengthMm = 200f, diaMm = 150f),
                Body(
                    id = "mid", startFromAftMm = 200f, lengthMm = 20f, diaMm = 200f,
                    blendAftMm = 20f, blendFwdMm = 20f,
                ),
                Body(id = "c", startFromAftMm = 220f, lengthMm = 200f, diaMm = 120f),
            ),
        )
        val e = edges(spec, "mid")
        assertTrue("flat span inverted: ${e.flatX0}..${e.flatX1}", e.flatX1 > e.flatX0)
        // 20 mm run, each blend capped at 40% -> 8 mm of curve per face, 4 mm of flat left.
        assertEquals(208f, e.flatX0, eps)
        assertEquals(212f, e.flatX1, eps)
        assertTrue(e.aftCurve.isNotEmpty() && e.fwdCurve.isNotEmpty())
    }

    /** A split body draws as several runs; the blend belongs to the one holding the face. */
    @Test
    fun `a body split by a liner blends only on the run carrying the stored face`() {
        val spec = ShaftSpec(
            overallLengthMm = 600f,
            bodies = listOf(
                Body(id = "small", startFromAftMm = 0f, lengthMm = 100f, diaMm = 150f),
                Body(id = "big", startFromAftMm = 100f, lengthMm = 400f, diaMm = 200f, blendAftMm = 40f),
            ),
            liners = listOf(Liner(startFromAftMm = 250f, lengthMm = 80f, odMm = 260f)),
        )
        val comps = resolveComponents(spec, overallIsManual = true)
        val runs = comps.filterIsInstance<ResolvedBody>().filter { resolvedBodyBaseId(it.id) == "big" }
        assertTrue("expected the liner to split the body", runs.size > 1)

        val blends = bodyBlends(spec, comps)
        val b = blends.single()
        val owner = runs.single { it.id == b.bodyId }
        assertEquals(100f, owner.startMmPhysical, eps)
        assertNotNull(runs.firstOrNull { it.id != b.bodyId })
        assertNull(blends.firstOrNull { it.bodyId != owner.id })
    }
}
