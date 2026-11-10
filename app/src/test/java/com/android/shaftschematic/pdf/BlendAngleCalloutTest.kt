package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.notes.CalloutKind
import com.android.shaftschematic.pdf.notes.LeaderSide
import com.android.shaftschematic.pdf.notes.blendAngleCalloutText
import com.android.shaftschematic.pdf.notes.blendRadiusCalloutLabel
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.bodyBlends
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The below-shaft callouts an Angle-mode blend prints (`buildBlendAngleCallouts`), as on the shop
 * sketch: `45°` with a leader to the cone, `3/8" R` with a leader to the round — STORED values,
 * anchored on the drawn bottom outline. A Length-mode blend prints nothing, so a sheet without an
 * Angle-mode face carries exactly the callouts it always did.
 */
class BlendAngleCalloutTest {

    private val inch = 25.4f

    private fun spec(angleDeg: Float?, radiusMm: Float) = ShaftSpec(
        overallLengthMm = 1000f,
        bodies = listOf(
            Body(
                id = "body", startFromAftMm = 300f, lengthMm = 700f, diaMm = 9.030f * inch,
                blendAftMm = 50.8f, blendAftToLinerOd = true,
                blendAftAngleDeg = angleDeg, blendAftRadiusMm = radiusMm,
            ),
        ),
        liners = listOf(Liner(id = "liner", startFromAftMm = 0f, lengthMm = 300f, odMm = 9.750f * inch)),
    )

    private val xAt = { mm: Float -> 36f + mm * 0.6f }
    private val rPx = { dia: Float -> dia * 0.5f * 0.12f }
    private val cy = 300f

    private fun callouts(spec: ShaftSpec, units: DisplayUnits = DisplayUnits.single(UnitSystem.INCHES)) =
        resolveComponents(spec).let { comps ->
            buildBlendAngleCallouts(
                comps.filterIsInstance<ResolvedBody>().map { spec.bodyForPdf(it) },
                bodyBlends(spec, comps), xAt, rPx, cy, units,
            )
        }

    @Test
    fun `an angle-mode face prints its angle and radius below the shaft`() {
        val calls = callouts(spec(angleDeg = 45f, radiusMm = 0.375f * inch))
        assertEquals(listOf(CalloutKind.ANGLE, CalloutKind.RADIUS), calls.map { it.kind })
        assertTrue(calls.all { it.side == LeaderSide.BELOW })
        val angle = calls[0]
        assertEquals(45f, angle.angleDeg, 0f)
        val radius = calls[1]
        assertEquals((0.375f * inch).toDouble(), radius.valueMm, 1e-4)
        // Leaders leave from the chamfer's bottom outline: below the centreline, above the
        // liner's bottom edge, and the round further inboard than the cone's midpoint.
        val faceX = xAt(300f)
        assertTrue(angle.anchorXPt!! > faceX)
        assertTrue(radius.anchorXPt!! > angle.anchorXPt!!)
        assertTrue(angle.anchorYPt!! > cy && angle.anchorYPt!! < cy + rPx(9.750f * inch))
    }

    @Test
    fun `a sharp corner prints the angle alone`() {
        val calls = callouts(spec(angleDeg = 30f, radiusMm = 0f))
        assertEquals(listOf(CalloutKind.ANGLE), calls.map { it.kind })
    }

    @Test
    fun `a length-mode blend prints nothing`() {
        assertTrue(callouts(spec(angleDeg = null, radiusMm = 9.525f)).isEmpty())
    }

    @Test
    fun `an angle-mode face with no step prints nothing`() {
        val flush = spec(angleDeg = 45f, radiusMm = 3f).let { s ->
            s.copy(liners = listOf(s.liners.single().copy(odMm = 9.030f * inch)))
        }
        assertTrue(callouts(flush).isEmpty())
    }

    @Test
    fun `the radius takes the body's display unit and the dual setting`() {
        val units = DisplayUnits(UnitSystem.INCHES, overrides = mapOf("body" to UnitSystem.MILLIMETERS), dual = true)
        val radius = callouts(spec(angleDeg = 45f, radiusMm = 10f), units).single { it.kind == CalloutKind.RADIUS }
        assertEquals(UnitSystem.MILLIMETERS, radius.unit)
        assertTrue(radius.dual)
    }

    @Test
    fun `callout text prints the stored values`() {
        assertEquals("45°", blendAngleCalloutText(45f))
        assertEquals("30.5°", blendAngleCalloutText(30.5f))
        assertEquals("3/8\" R", blendRadiusCalloutLabel(0.375 * 25.4, UnitSystem.INCHES, dual = false).inline())
        assertEquals("10 mm R", blendRadiusCalloutLabel(10.0, UnitSystem.MILLIMETERS, dual = false).inline())
        val dual = blendRadiusCalloutLabel(0.375 * 25.4, UnitSystem.INCHES, dual = true)
        assertEquals("3/8\" R", dual.primary)
        assertEquals("9.5 mm", dual.secondary)
    }
}
