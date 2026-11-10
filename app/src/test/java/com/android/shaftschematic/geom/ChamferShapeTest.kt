package com.android.shaftschematic.geom

import com.android.shaftschematic.model.BlendProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/**
 * An Angle-mode blend face (on-device request: "some blends have expressions in degrees") — a
 * straight cone at a stored angle from the shaft axis, its length derived from the step, with an
 * optional corner radius at the cone's small-diameter end, tangent to the cone and the small
 * surface with setback `R·tan(θ/2)` along each line.
 */
class ChamferShapeTest {

    private val eps = 1e-3f

    @Test
    fun `the cone length is the rise over the tangent of the angle`() {
        // The shop sketch: a 9.750 liner down to a 9.030 body at 45° climbs 0.360" in 0.360".
        val riseMm = (9.750f - 9.030f) / 2f * 25.4f
        assertEquals(riseMm, chamferConeLengthMm(riseMm, 45f)!!, eps)
        assertEquals(10f / tan(Math.toRadians(30.0)).toFloat(), chamferConeLengthMm(10f, 30f)!!, eps)
        assertEquals(10f / tan(Math.toRadians(60.0)).toFloat(), chamferConeLengthMm(10f, 60f)!!, eps)
    }

    @Test
    fun `no rise or an unusable angle derives nothing`() {
        assertNull(chamferConeLengthMm(0f, 45f))
        assertNull(chamferConeLengthMm(10f, 0f))
        assertNull(chamferConeLengthMm(10f, 90f))
        assertNull(chamferConeLengthMm(10f, -5f))
        assertNull(chamferConeLengthMm(10f, Float.NaN))
    }

    @Test
    fun `the valid range is the open interval from 0 to 90 degrees`() {
        assertTrue(isValidBlendAngle(0.5f))
        assertTrue(isValidBlendAngle(45f))
        assertTrue(isValidBlendAngle(89.9f))
        assertFalse(isValidBlendAngle(0f))
        assertFalse(isValidBlendAngle(90f))
        assertFalse(isValidBlendAngle(null))
    }

    @Test
    fun `a sharp chamfer is a straight line from end to end`() {
        val shape = chamferShape(riseMm = 10f, angleDeg = 45f, radiusMm = 0f)!!
        assertEquals(10f, shape.spanMm, eps)
        assertEquals(listOf(BlendFracPoint(0f, 0f), BlendFracPoint(1f, 1f)), shape.fracPoints())
        assertNull(shape.cornerMidFrac())
        assertEquals(BlendFracPoint(0.5f, 0.5f), shape.coneMidFrac())
    }

    /** True (axial, drop) coordinates of a normalized point, large end at the origin. */
    private fun ChamferShape.trueOf(p: BlendFracPoint) = Pair(p.t * spanMm, p.frac * riseMm)

    @Test
    fun `the corner radius is tangent to the cone and to the small surface`() {
        val angle = 30f
        val rise = 20f
        val r = 9.525f // 3/8"
        val shape = chamferShape(rise, angle, r)!!
        val theta = Math.toRadians(angle.toDouble())
        val cone = rise / tan(theta)
        val setback = r * tan(theta / 2.0)
        assertEquals(setback.toFloat(), shape.setbackMm, eps)
        assertEquals((cone + setback).toFloat(), shape.spanMm, eps)

        val pts = shape.fracPoints().map { shape.trueOf(it) }
        // Large end at the origin, small surface at the far end.
        assertEquals(0.0, pts.first().first.toDouble(), 1e-3)
        assertEquals(0.0, pts.first().second.toDouble(), 1e-3)
        assertEquals(shape.spanMm.toDouble(), pts.last().first.toDouble(), 1e-3)
        assertEquals(rise.toDouble(), pts.last().second.toDouble(), 1e-3)

        // The cone segment runs at the stored angle.
        val (x1, y1) = pts[1]
        assertEquals(tan(theta), (y1 / x1).toDouble(), 1e-3)
        // ...and stops `setback` short of the sharp corner, measured ALONG the cone.
        assertEquals(setback, hypot(cone - x1, (rise - y1).toDouble()), 1e-3)

        // Every arc vertex sits R from the round's centre: R above the small surface, at the
        // flat tangent point `setback` past the corner.
        val cx = cone + setback
        val cyDrop = rise - r // the centre's drop from the large radius
        pts.drop(1).forEach { (x, y) ->
            assertEquals(r.toDouble(), hypot(x - cx, (y - cyDrop).toDouble()), 1e-3)
        }

        // The arc's last step is flat (tangent to the small surface), its first along the cone.
        val (xa, ya) = pts[pts.size - 2]
        assertTrue("flat at the small surface", abs((rise - ya) / (shape.spanMm - xa)) < 0.2f)
        val (xb, yb) = pts[2]
        assertEquals("on the cone's slope at the tangent", tan(theta), ((yb - y1) / (xb - x1)).toDouble(), 0.1)
    }

    @Test
    fun `the corner midpoint is on the round, the cone midpoint on the cone`() {
        val shape = chamferShape(riseMm = 20f, angleDeg = 45f, radiusMm = 6f)!!
        val cornerMid = shape.cornerMidFrac()!!
        val coneMid = shape.coneMidFrac()
        val theta = Math.toRadians(45.0)
        val cone = 20.0 / tan(theta)
        val cx = cone + shape.setbackMm
        val (mx, my) = shape.trueOf(cornerMid)
        assertEquals(6.0, hypot(mx - cx, my - (20.0 - 6.0)), 1e-3)
        val (kx, ky) = shape.trueOf(coneMid)
        assertEquals(tan(theta), (ky / kx).toDouble(), 1e-3)
        // The round's midpoint bisects the corner: φ = −π/2 − θ/2 from the centre.
        assertEquals(cx + 6.0 * cos(-Math.PI / 2 - theta / 2), mx.toDouble(), 1e-3)
        assertEquals((20.0 - 6.0) - 6.0 * sin(-Math.PI / 2 - theta / 2), my.toDouble(), 1e-3)
    }

    @Test
    fun `a radius too large for the cone is clamped where drawn`() {
        val rise = 5f
        val shape = chamferShape(rise, 45f, radiusMm = 500f)!!
        val theta = Math.toRadians(45.0)
        val cone = rise / tan(theta)
        val maxR = cone / (cos(theta) * tan(theta / 2.0))
        assertEquals(maxR.toFloat(), shape.radiusMm, eps)
        // The round reaches the cone's large end but never past it.
        val pts = shape.fracPoints()
        assertTrue(pts.zipWithNext().all { (a, b) -> b.t > a.t })
        assertEquals(BlendFracPoint(1f, 1f), pts.last())
    }

    @Test
    fun `the oriented points mirror end for end when the small end is aft`() {
        val shape = chamferShape(riseMm = 10f, angleDeg = 45f, radiusMm = 3f)!!
        val fromLarge = blendFracPoints(BlendProfile.OGEE, shape, largerAtAft = true)
        val fromSmall = blendFracPoints(BlendProfile.OGEE, shape, largerAtAft = false)
        assertEquals(fromLarge.size, fromSmall.size)
        assertEquals(BlendFracPoint(0f, 0f), fromSmall.first())
        assertEquals(BlendFracPoint(1f, 1f), fromSmall.last())
        fromLarge.zip(fromSmall.asReversed()).forEach { (a, b) ->
            assertEquals(1f - a.t, b.t, eps)
            assertEquals(1f - a.frac, b.frac, eps)
        }
    }

    @Test
    fun `the mm polyline follows the chamfer and ignores the profile`() {
        val shape = chamferShape(riseMm = 10f, angleDeg = 45f, radiusMm = 0f)!!
        val pts = blendPolyline(0f, 10f, 110f, 100f, BlendProfile.OGEE, chamfer = shape)
        assertEquals(2, pts.size)
        assertEquals(220f, pts.first().diaMm, eps)
        assertEquals(200f, pts.last().diaMm, eps)
        assertNotNull(pts)
    }

    @Test
    fun `an angle prints trimmed`() {
        assertEquals("45", formatBlendAngleDeg(45f))
        assertEquals("30.5", formatBlendAngleDeg(30.5f))
        assertEquals("12.125", formatBlendAngleDeg(12.125f))
    }
}
