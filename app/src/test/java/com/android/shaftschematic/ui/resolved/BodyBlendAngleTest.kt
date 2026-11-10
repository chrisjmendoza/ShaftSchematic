package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.geom.MIN_BLEND_WIDTH_PT
import com.android.shaftschematic.geom.chamferShape
import com.android.shaftschematic.geom.outerDiaAt
import com.android.shaftschematic.model.AutoBlend
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Angle-mode blend faces through the ONE resolution point (`blendAt`): the length derives from the
 * diameter step the face already climbs — liner rules included — and the stored length is never
 * read for geometry nor rewritten.
 */
class BodyBlendAngleTest {

    private val eps = 1e-2f
    private val inch = 25.4f

    /** The shop sketch: a 9.750" liner, then a 9.030" body whose AFT face chamfers down from it. */
    private fun sketchSpec(
        angleDeg: Float? = 45f,
        radiusMm: Float = 0f,
        toLinerOd: Boolean = true,
        storedLenMm: Float = 50.8f,
    ) = ShaftSpec(
        overallLengthMm = 1000f,
        bodies = listOf(
            Body(
                id = "body", startFromAftMm = 300f, lengthMm = 700f, diaMm = 9.030f * inch,
                blendAftMm = storedLenMm, blendAftToLinerOd = toLinerOd,
                blendAftAngleDeg = angleDeg, blendAftRadiusMm = radiusMm,
            ),
        ),
        liners = listOf(Liner(id = "liner", startFromAftMm = 0f, lengthMm = 300f, odMm = 9.750f * inch)),
    )

    private fun blendsOf(spec: ShaftSpec) = bodyBlends(spec, resolveComponents(spec))

    @Test
    fun `angle mode derives the length from the step to the liner OD`() {
        val b = blendsOf(sketchSpec()).single()
        assertEquals(9.750f * inch, b.neighbourDiaMm, eps)
        // rise 0.360" at 45° → 0.360" of cone.
        assertEquals(0.360f * inch, b.lengthMm, eps)
        assertEquals(45f, b.angleDeg!!, eps)
    }

    @Test
    fun `angle mode follows the derived seat midpoint by default`() {
        val b = blendsOf(sketchSpec(toLinerOd = false)).single()
        val seat = (9.750f + 9.030f) / 2f * inch
        assertEquals(seat, b.neighbourDiaMm, eps)
        assertEquals(0.180f * inch, b.lengthMm, eps)
    }

    @Test
    fun `the stored length plays no part in angle mode and is never rewritten`() {
        val spec = sketchSpec(storedLenMm = 123f)
        val b = blendsOf(spec).single()
        assertEquals(0.360f * inch, b.lengthMm, eps)
        assertEquals(123f, spec.bodies.single().blendAftMm, 0f)
        // Back in Length mode the same stored value is the curve length again.
        val lengthMode = blendsOf(sketchSpec(angleDeg = null, storedLenMm = 123f)).single()
        assertEquals(123f, lengthMode.lengthMm, eps)
        assertNull(lengthMode.angleDeg)
    }

    @Test
    fun `a corner radius extends the curve by its setback`() {
        val r = 0.375f * inch
        val b = blendsOf(sketchSpec(radiusMm = r)).single()
        val shape = chamferShape(0.360f * inch, 45f, r)!!
        assertEquals(shape.spanMm, b.lengthMm, eps)
        assertEquals(r, b.cornerRadiusMm, 0f)
        assertNotNull(b.chamfer())
    }

    @Test
    fun `a step change re-derives the length from the stored angle`() {
        val bigger = sketchSpec().let { s ->
            s.copy(liners = listOf(s.liners.single().copy(odMm = 10.030f * inch)))
        }
        assertEquals(0.500f * inch, blendsOf(bigger).single().lengthMm, eps)
    }

    @Test
    fun `no step across the face draws no blend in angle mode`() {
        val flush = sketchSpec().let { s ->
            s.copy(liners = listOf(s.liners.single().copy(odMm = 9.030f * inch)))
        }
        assertTrue(blendsOf(flush).isEmpty())
        val bare = ShaftSpec(
            overallLengthMm = 700f,
            bodies = listOf(Body(id = "b", startFromAftMm = 0f, lengthMm = 700f, diaMm = 200f, blendAftMm = 20f, blendAftAngleDeg = 45f)),
        )
        assertTrue(blendsOf(bare).isEmpty())
    }

    @Test
    fun `an auto span carries the angle as an anchor and resolves the same way`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            autoBodyDiaMm = 9.030f * inch,
            liners = listOf(Liner(id = "liner", startFromAftMm = 0f, lengthMm = 300f, odMm = 9.750f * inch)),
            autoBlends = listOf(
                AutoBlend(
                    anchorMm = 650f, end = LinerAuthoredReference.AFT, lengthMm = 50.8f,
                    toLinerOd = true, angleDeg = 45f,
                ),
            ),
        )
        val b = blendsOf(spec).single()
        assertEquals(0.360f * inch, b.lengthMm, eps)
        assertEquals(45f, b.angleDeg!!, eps)
    }

    @Test
    fun `the surface envelope sees the chamfer at its true span`() {
        val spec = sketchSpec()
        val comps = resolveComponents(spec)
        val segs = surfaceSegsFrom(comps, bodyBlends(spec, comps))
        val face = 300f
        val cone = 0.360f * inch
        // Halfway down a 45° cone the diameter is halfway between the two.
        val mid = outerDiaAt(segs, face + cone / 2f)
        assertEquals((9.750f + 9.030f) / 2f * inch, mid, 0.05f)
        assertEquals(9.030f * inch, outerDiaAt(segs, face + cone + 1f), eps)
    }

    @Test
    fun `the readout a card shows matches the drawn cone`() {
        val spec = sketchSpec()
        val comps = resolveComponents(spec)
        val run = explicitBodyFaceRun(comps, "body", LinerAuthoredReference.AFT)!!
        assertEquals(
            0.360f * inch,
            angleBlendConeLengthMm(comps, run, LinerAuthoredReference.AFT, 45f, toLinerOd = true)!!,
            eps,
        )
        assertEquals(
            0.180f * inch,
            angleBlendConeLengthMm(comps, run, LinerAuthoredReference.AFT, 45f, toLinerOd = false)!!,
            eps,
        )
        assertNull(angleBlendConeLengthMm(comps, run, LinerAuthoredReference.FWD, 45f, toLinerOd = true))
    }

    @Test
    fun `drawn edges carry the callout anchors on the drawn chamfer`() {
        val r = 0.375f * inch
        val spec = sketchSpec(radiusMm = r)
        val comps = resolveComponents(spec)
        val blends = bodyBlends(spec, comps)
        val run = comps.filterIsInstance<ResolvedBody>().single { it.id == "body" }
        // A sheet-like map: x compressed, radius on its own scale.
        val xAt = { mm: Float -> mm * 0.5f }
        val rAt = { dia: Float -> dia * 0.2f }
        val edges = bodyDrawEdges(
            run.id, run.startMmPhysical, run.endMmPhysical, run.diaMm, blends, xAt, rAt, MIN_BLEND_WIDTH_PT,
        )
        val mark = edges.angleMarks.single()
        assertEquals(45f, mark.angleDeg, 0f)
        assertEquals(r, mark.radiusMm, 0f)
        val curve = edges.aftCurve
        assertTrue(mark.coneMid.xPx > curve.first().xPx && mark.coneMid.xPx < curve.last().xPx)
        val corner = mark.cornerMid!!
        assertTrue(corner.xPx > mark.coneMid.xPx)
        // Anchors sit between the two drawn radii.
        val rBig = rAt(9.750f * inch); val rSmall = rAt(9.030f * inch)
        assertTrue(mark.coneMid.rPx < rBig && mark.coneMid.rPx > rSmall)
        assertTrue(corner.rPx < rBig && corner.rPx >= rSmall)
    }

    @Test
    fun `a length-mode face carries no callout anchors`() {
        val spec = sketchSpec(angleDeg = null)
        val comps = resolveComponents(spec)
        val run = comps.filterIsInstance<ResolvedBody>().single { it.id == "body" }
        val edges = bodyDrawEdges(
            run.id, run.startMmPhysical, run.endMmPhysical, run.diaMm, bodyBlends(spec, comps),
            { it }, { it / 2f }, MIN_BLEND_WIDTH_PT,
        )
        assertTrue(edges.aftCurve.isNotEmpty())
        assertTrue(edges.angleMarks.isEmpty())
    }

    @Test
    fun `the profile is ignored for an angle-mode face`() {
        val ogee = blendsOf(sketchSpec()).single()
        val fillet = blendsOf(sketchSpec().let { s ->
            s.copy(bodies = listOf(s.bodies.single().copy(blendProfile = BlendProfile.FILLET)))
        }).single()
        assertEquals(ogee.lengthMm, fillet.lengthMm, eps)
        assertEquals(ogee.chamfer(), fillet.chamfer())
    }
}
