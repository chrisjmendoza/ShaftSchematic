package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.geom.SEAL_GROOVE_COUNT
import com.android.shaftschematic.model.AutoDiaOverride
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.withAutoBlend
import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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

    // ───────── seal areas: a liner butting the face ─────────

    /**
     * The shaft IS cut down under a liner, but that seat is never drawn and its depth varies job
     * to job, so the blend leaves from the midpoint of the liner OD and the body Ø — a shoulder
     * that reads without claiming a measurement.
     */
    @Test
    fun `a liner butting the face supplies a derived midpoint seat`() {
        val spec = ShaftSpec(
            overallLengthMm = 800f,
            bodies = listOf(
                Body(id = "run", startFromAftMm = 0f, lengthMm = 300f, diaMm = 200f, blendFwdMm = 25.4f),
            ),
            liners = listOf(Liner(startFromAftMm = 300f, lengthMm = 400f, odMm = 220f)),
        )
        val b = blendsOf(spec).single()
        assertEquals(LinerAuthoredReference.FWD, b.end)
        assertEquals(300f, b.faceMm, eps)
        assertEquals(210f, b.neighbourDiaMm, eps) // halfway between Ø220 liner and Ø200 body
        assertEquals(200f, b.bodyDiaMm, eps)
    }

    @Test
    fun `the derived seat works on an aft face too`() {
        val spec = ShaftSpec(
            overallLengthMm = 800f,
            bodies = listOf(
                Body(id = "run", startFromAftMm = 400f, lengthMm = 400f, diaMm = 200f, blendAftMm = 25.4f),
            ),
            liners = listOf(Liner(startFromAftMm = 100f, lengthMm = 300f, odMm = 260f)),
        )
        val b = blendsOf(spec).single()
        assertEquals(LinerAuthoredReference.AFT, b.end)
        assertEquals(230f, b.neighbourDiaMm, eps)
    }

    @Test
    fun `a liner flush with the body diameter leaves no shoulder to blend`() {
        val spec = ShaftSpec(
            overallLengthMm = 800f,
            bodies = listOf(
                Body(id = "run", startFromAftMm = 0f, lengthMm = 300f, diaMm = 200f, blendFwdMm = 25.4f),
            ),
            liners = listOf(Liner(startFromAftMm = 300f, lengthMm = 400f, odMm = 200f)),
        )
        assertTrue(blendsOf(spec).isEmpty())
    }

    /** Drawn shaft surface always wins: a visible component across the face is never a seat. */
    @Test
    fun `a drawn body across the face beats the derived seat`() {
        val spec = ShaftSpec(
            overallLengthMm = 800f,
            bodies = listOf(
                Body(id = "run", startFromAftMm = 0f, lengthMm = 300f, diaMm = 200f, blendFwdMm = 25.4f),
                Body(id = "next", startFromAftMm = 300f, lengthMm = 300f, diaMm = 150f),
            ),
            // Liner starts 100 mm along "next", so "next" still draws across the face.
            liners = listOf(Liner(startFromAftMm = 400f, lengthMm = 200f, odMm = 260f)),
        )
        val b = blendsOf(spec).single()
        assertEquals(150f, b.neighbourDiaMm, eps) // the body, not (260+200)/2
    }

    /**
     * A seat authored as its own body under a liner is NOT consulted: the resolve layer trims a
     * fully-covered body out of the drawing, so there is nothing on the sheet for the curve to
     * arrive at and the derived midpoint applies just as if the seat had never been authored.
     */
    @Test
    fun `a seat body hidden under a liner still yields the derived midpoint`() {
        val spec = ShaftSpec(
            overallLengthMm = 800f,
            bodies = listOf(
                Body(id = "run", startFromAftMm = 0f, lengthMm = 300f, diaMm = 200f, blendFwdMm = 25.4f),
                Body(id = "seat", startFromAftMm = 300f, lengthMm = 300f, diaMm = 150f),
            ),
            liners = listOf(Liner(startFromAftMm = 300f, lengthMm = 300f, odMm = 260f)),
        )
        val comps = resolveComponents(spec, overallIsManual = true)
        assertNull(
            "a fully covered body should not draw",
            comps.filterIsInstance<ResolvedBody>().firstOrNull { it.id == "seat" },
        )
        assertEquals(230f, blendsOf(spec).single().neighbourDiaMm, eps) // (260 + 200) / 2
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

    // ───────── auto-body spans ─────────

    /**
     * The fiberglass-between-liners case: bare shaft carrying its own Ø, blended into the liner
     * at each end. Anchored in shaft space, so it survives the liners moving under it — which is
     * the whole reason it is not a promoted body.
     */
    @Test
    fun `an auto span blends from a shaft-space anchor`() {
        fun spec(linerEndMm: Float, bigLinerStartMm: Float) = ShaftSpec(
            overallLengthMm = 900f,
            liners = listOf(
                Liner(id = "aft", startFromAftMm = 0f, lengthMm = linerEndMm, odMm = 220f),
                Liner(id = "fwd", startFromAftMm = bigLinerStartMm, lengthMm = 900f - bigLinerStartMm, odMm = 220f),
            ),
            autoDiaOverrides = listOf(
                AutoDiaOverride(anchorMm = (linerEndMm + bigLinerStartMm) / 2f, diaMm = 180f),
            ),
        )
            .withAutoBlend(linerEndMm, bigLinerStartMm, LinerAuthoredReference.AFT, 25.4f)
            .withAutoBlend(linerEndMm, bigLinerStartMm, LinerAuthoredReference.FWD, 25.4f)

        val blends = blendsOf(spec(200f, 600f))
        assertEquals(2, blends.size)
        val aft = blends.single { it.end == LinerAuthoredReference.AFT }
        val fwd = blends.single { it.end == LinerAuthoredReference.FWD }
        assertEquals(200f, aft.faceMm, eps)
        assertEquals(600f, fwd.faceMm, eps)
        assertEquals(180f, aft.bodyDiaMm, eps)
        assertEquals(200f, aft.neighbourDiaMm, eps) // derived seat: (220 liner + 180 shaft) / 2

        // Both liners move and resize; the anchors stay inside the bare span, so the seal
        // areas follow instead of being stranded.
        val moved = blendsOf(spec(260f, 640f))
        assertEquals(2, moved.size)
        assertEquals(260f, moved.single { it.end == LinerAuthoredReference.AFT }.faceMm, eps)
        assertEquals(640f, moved.single { it.end == LinerAuthoredReference.FWD }.faceMm, eps)
    }

    @Test
    fun `an auto blend anchored under a component goes dormant, never pruned`() {
        val spec = ShaftSpec(
            overallLengthMm = 900f,
            liners = listOf(
                Liner(id = "aft", startFromAftMm = 0f, lengthMm = 200f, odMm = 220f),
                Liner(id = "fwd", startFromAftMm = 600f, lengthMm = 300f, odMm = 220f),
            ),
            autoDiaOverrides = listOf(AutoDiaOverride(anchorMm = 400f, diaMm = 180f)),
        ).withAutoBlend(200f, 600f, LinerAuthoredReference.AFT, 25.4f)
        assertEquals(1, blendsOf(spec).size)

        // The aft liner grows to swallow the span the anchor sat in.
        val covered = spec.copy(liners = listOf(spec.liners[0].copy(lengthMm = 600f), spec.liners[1]))
        assertTrue(blendsOf(covered).isEmpty())
        assertEquals("the anchor must survive", 1, covered.autoBlends.size)

        // Restoring the span resurrects it unchanged.
        assertEquals(1, blendsOf(spec).size)
    }

    @Test
    fun `clearing an auto blend drops only that face`() {
        val spec = ShaftSpec(overallLengthMm = 900f)
            .withAutoBlend(200f, 400f, LinerAuthoredReference.AFT, 20f)
            .withAutoBlend(200f, 400f, LinerAuthoredReference.FWD, 25.4f)
        assertEquals(2, spec.autoBlends.size)

        val cleared = spec.withAutoBlend(200f, 400f, LinerAuthoredReference.AFT, 0f)
        assertEquals(1, cleared.autoBlends.size)
        assertEquals(LinerAuthoredReference.FWD, cleared.autoBlends.single().end)
    }

    /**
     * An explicit body never absorbs the bare-shaft gap beside it (the gap survives as its
     * own auto run — see [normalizeBodies]), so the run's drawn edge IS the stored face and
     * the blend curves from there, stepping to whatever the surviving gap run draws at.
     */
    @Test
    fun `a blend curves from the stored face when a gap survives beside the run`() {
        val spec = ShaftSpec(
            overallLengthMm = 600f,
            tapers = listOf(
                Taper(id = "t", startFromAftMm = 0f, lengthMm = 160f, startDiaMm = 120f, endDiaMm = 150f),
            ),
            bodies = listOf(
                // Stored start 200; the [160, 200) gap stays its own auto run.
                Body(id = "big", startFromAftMm = 200f, lengthMm = 400f, diaMm = 200f, blendAftMm = 25.4f),
            ),
        )
        val b = blendsOf(spec).single()
        assertEquals(200f, b.faceMm, eps)   // the stored face — the gap is not absorbed
        assertEquals(200f, b.bodyDiaMm, eps)
    }

    // ───────── seal areas: the radius cuts ─────────

    @Test
    fun `a seal area draws its cuts across the blend, inside its span`() {
        val spec = ShaftSpec(
            overallLengthMm = 800f,
            bodies = listOf(
                Body(
                    id = "run", startFromAftMm = 0f, lengthMm = 300f, diaMm = 200f,
                    blendFwdMm = 50f, blendFwdSeal = true,
                ),
            ),
            liners = listOf(Liner(startFromAftMm = 300f, lengthMm = 400f, odMm = 240f)),
        )
        val comps = resolveComponents(spec, overallIsManual = true)
        val run = comps.filterIsInstance<ResolvedBody>().single { it.id == "run" }
        val e = bodyDrawEdges(
            runId = run.id,
            runStartMm = run.startMmPhysical, runEndMm = run.endMmPhysical, runDiaMm = run.diaMm,
            blends = bodyBlends(spec, comps),
            xAt = { mm -> mm }, rAt = { dia -> dia / 2f }, minWidthPx = 7f,
        )
        assertEquals(SEAL_GROOVE_COUNT, e.fwdSeal.size)
        assertTrue("no cuts belong on the unblended aft face", e.aftSeal.isEmpty())

        // Every cut sits strictly inside the curve, never on its end faces.
        val x0 = e.fwdCurve.first().xPx
        val x1 = e.fwdCurve.last().xPx
        e.fwdSeal.forEach {
            assertTrue("cut at ${it.xPx} outside ($x0, $x1)", it.xPx > x0 && it.xPx < x1)
        }
        // Evenly spaced, ordered aft -> fwd.
        val gaps = e.fwdSeal.zipWithNext { a, b -> b.xPx - a.xPx }
        gaps.forEach { assertEquals(gaps.first(), it, eps) }

        // A groove is a cut INTO the surface: the silhouette carries a notch at each station,
        // and the line across ends exactly on that notch's floor — never at full silhouette
        // height, which is the glyph for a component face. Lockstep is the invariant: the
        // seal point must BE a vertex of the curve polyline.
        e.fwdSeal.forEach { g ->
            assertTrue(
                "no notch vertex under the line at x=${g.xPx}",
                e.fwdCurve.any { abs(it.xPx - g.xPx) < eps && abs(it.rPx - g.rPx) < eps },
            )
            // And the floor sits below the local surface on both sides of the cut.
            val flankR = e.fwdCurve
                .filter { abs(it.xPx - g.xPx) > eps && abs(it.xPx - g.xPx) < 5f }
                .minOf { it.rPx }
            assertTrue("floor ${g.rPx} not below flank $flankR", g.rPx < flankR)
        }
    }

    @Test
    fun `seal notches never merge and never touch the curve ends`() {
        // The 7 pt floored span is the tightest host a seal area can land on.
        val e = edgesWithSealedLiner(blendMm = 0.5f, minWidthPx = 7f)
        assertEquals(SEAL_GROOVE_COUNT, e.fwdSeal.size)
        val xs = e.fwdCurve.map { it.xPx }
        // Strictly increasing x — overlapping notch windows would fold the polyline back.
        xs.zipWithNext { a, b -> assertTrue("polyline folds at $a", b >= a - eps) }
        // Full surface height survives between and outside the notches.
        val surfaceMax = e.fwdCurve.maxOf { it.rPx }
        assertTrue(surfaceMax > e.fwdSeal.maxOf { it.rPx })
    }

    private fun edgesWithSealedLiner(blendMm: Float, minWidthPx: Float): BodyDrawEdges {
        val spec = ShaftSpec(
            overallLengthMm = 800f,
            bodies = listOf(
                Body(
                    id = "run", startFromAftMm = 0f, lengthMm = 300f, diaMm = 200f,
                    blendFwdMm = blendMm, blendFwdSeal = true,
                ),
            ),
            liners = listOf(Liner(startFromAftMm = 300f, lengthMm = 400f, odMm = 240f)),
        )
        val comps = resolveComponents(spec, overallIsManual = true)
        val run = comps.filterIsInstance<ResolvedBody>().single { it.id == "run" }
        return bodyDrawEdges(
            runId = run.id,
            runStartMm = run.startMmPhysical, runEndMm = run.endMmPhysical, runDiaMm = run.diaMm,
            blends = bodyBlends(spec, comps),
            xAt = { mm -> mm }, rAt = { dia -> dia / 2f }, minWidthPx = minWidthPx,
        )
    }

    @Test
    fun `a blend without a seal area draws no cuts`() {
        val e = edges(steppedSpec(blendAftMm = 50f), "big")
        assertTrue(e.aftSeal.isEmpty() && e.fwdSeal.isEmpty())
    }

    @Test
    fun `an auto span can carry a seal area too`() {
        val spec = ShaftSpec(
            overallLengthMm = 900f,
            liners = listOf(
                Liner(id = "aft", startFromAftMm = 0f, lengthMm = 200f, odMm = 220f),
                Liner(id = "fwd", startFromAftMm = 600f, lengthMm = 300f, odMm = 220f),
            ),
            autoDiaOverrides = listOf(AutoDiaOverride(anchorMm = 400f, diaMm = 180f)),
        ).withAutoBlend(200f, 600f, LinerAuthoredReference.AFT, 25.4f, BlendProfile.OGEE, seal = true)
        assertTrue(blendsOf(spec).single().seal)
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
