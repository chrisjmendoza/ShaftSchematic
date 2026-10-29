package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.drawing.renderSpanSpec
import com.android.shaftschematic.ui.util.BOUNDS_EPS_MM
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// Body length rail — ONE decision behind the card and both dimensioned sheets
// ─────────────────────────────────────────────────────────────────────────────

/**
 * True when [body]'s STORED span touches a shaft end face: its start is the AFT face (0) or its
 * end is the FWD face — the typed OAL, or, while no OAL is typed, the coverage end the renderer
 * draws to ([renderSpanSpec], the one 0-OAL fallback). Both comparisons take the shaft-span
 * bounds slop [BOUNDS_EPS_MM], so `Float` noise on a long shaft never moves a body off its end.
 *
 * An end body is where a coupling goes, so its own length is a number the shop needs; a
 * mid-shaft body's length is usually implied by its neighbours' rails. A zero-length body is
 * never at an end — it has no span to dimension.
 */
fun bodyAtShaftEnd(body: Body, spec: ShaftSpec): Boolean {
    if (body.lengthMm <= 0f) return false
    if (abs(body.startFromAftMm) <= BOUNDS_EPS_MM) return true
    val shaftEndMm = spec.renderSpanSpec().overallLengthMm
    return shaftEndMm > 0f && abs(body.startFromAftMm + body.lengthMm - shaftEndMm) <= BOUNDS_EPS_MM
}

/**
 * Whether [body]'s length prints as a dimension span: the authored
 * [Body.showDimensionRail] when set, else the position default — on at a shaft end, off
 * mid-shaft ([bodyAtShaftEnd]). Explicit bodies only: an auto span has no stored body to carry
 * the flag and never gets the rail.
 */
fun effectiveBodyDimensionRail(body: Body, spec: ShaftSpec): Boolean =
    body.showDimensionRail ?: bodyAtShaftEnd(body, spec)
