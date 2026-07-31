// file: app/src/main/java/com/android/shaftschematic/geom/KeywaySilhouetteMath.kt
package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.KeywayClocking
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.hasKeyway
import com.android.shaftschematic.model.keywayAbsSpanMm
import kotlin.math.max
import kotlin.math.min

/**
 * Silhouette-notch pure math for keyways clocked **90° apart** — the true engineering
 * projection of a keyway seen edge-on, where the slot's **depth** is what shows in profile.
 *
 * Kept as small, dependency-free top-level functions (no Compose/Android imports, `geom`
 * package, no `pdf → ui` dependency) so the geometry is directly unit-testable on a plain JVM —
 * the same posture as `geom/KeywaySpoonMath.kt` and `geom/WearPitMath.kt`.
 *
 * The notch is drawn **identically** in both draw sites, which must stay in lockstep:
 * - canvas: `ShaftRenderer` (`drawKeywaySilhouetteNotch`, Compose `DrawScope`),
 * - PDF: `ShaftPdfComposer` (`drawKeywaySilhouetteNotchPdf`, `android.graphics.Canvas`/`Paint`).
 *
 * Side convention comes from [KeywayClocking] (see its KDoc for the derivation): CW viewed from
 * aft → the drawing's **bottom** edge, CCW → the **top** edge.
 *
 * The `keywaySpooned` flag has no effect here: the spoon bowl is a face-view construct with no
 * edge-on projection, so a spooned secondary keyway draws as a plain notch.
 */

/** Axial/radial tolerance for span and depth degeneracy checks (mm). */
private const val SILHOUETTE_EPS_MM = 1e-3f

/** Which silhouette edge of the profile a 90°-clocked keyway is cut into. */
enum class KeywaySilhouetteSide { TOP, BOTTOM }

/** One notch outline vertex: axial position and the **radius** (not Y) it sits at, both mm. */
data class KeywaySilhouettePoint(val xMm: Float, val radiusMm: Float)

/**
 * A 90°-clocked keyway projected onto one silhouette edge, in **mm** — the draw site maps to
 * px/pt and picks Y from [side] via [yFor].
 *
 * The surface radii track the host's outer surface across the notch (constant on a body, linear
 * on a taper), so the floor runs parallel to a sloped silhouette.
 *
 * @property startMm            AFT-side end of the notch, clamped to the host span
 * @property endMm              FWD-side end of the notch, clamped to the host span
 * @property surfaceRadiusStartMm host outer radius at [startMm]
 * @property surfaceRadiusEndMm   host outer radius at [endMm]
 * @property floorRadiusStartMm   notch floor radius at [startMm] (surface − depth)
 * @property floorRadiusEndMm     notch floor radius at [endMm]
 * @property side                 which silhouette edge the notch is cut into
 */
data class KeywaySilhouetteNotch(
    val startMm: Float,
    val endMm: Float,
    val surfaceRadiusStartMm: Float,
    val surfaceRadiusEndMm: Float,
    val floorRadiusStartMm: Float,
    val floorRadiusEndMm: Float,
    val side: KeywaySilhouetteSide,
)

/**
 * Silhouette edge for a clocking mode, or null when no notch is drawn — [KeywayClocking.NONE]
 * has no secondaries and [KeywayClocking.DEG_180] draws hidden plan-view slots instead.
 */
fun keywaySilhouetteSideFor(clocking: KeywayClocking): KeywaySilhouetteSide? = when (clocking) {
    KeywayClocking.DEG_90_CW  -> KeywaySilhouetteSide.BOTTOM
    KeywayClocking.DEG_90_CCW -> KeywaySilhouetteSide.TOP
    KeywayClocking.DEG_180, KeywayClocking.NONE -> null
}

/**
 * Map a radius (mm) to a canvas/PDF Y on this silhouette edge. The single place either draw
 * site turns radii into Y, so top/bottom placement can't drift between them.
 */
fun KeywaySilhouetteSide.yFor(centerlineY: Float, radiusMm: Float, scale: Float): Float =
    when (this) {
        KeywaySilhouetteSide.TOP    -> centerlineY - radiusMm * scale
        KeywaySilhouetteSide.BOTTOM -> centerlineY + radiusMm * scale
    }

/**
 * The notch's closed fill polygon, in draw order:
 * surface at [KeywaySilhouetteNotch.startMm] → floor at start → floor at end → surface at end.
 *
 * Fill this with the void/background colour to erase the host outline segment crossing the
 * notch, then stroke the two walls and the floor ([wallStartMm]/[wallEndMm]/[floorMm]). The
 * original surface line is **not** stroked — the notch is open at the surface.
 */
fun KeywaySilhouetteNotch.fillPolygonMm(): List<KeywaySilhouettePoint> = listOf(
    KeywaySilhouettePoint(startMm, surfaceRadiusStartMm),
    KeywaySilhouettePoint(startMm, floorRadiusStartMm),
    KeywaySilhouettePoint(endMm, floorRadiusEndMm),
    KeywaySilhouettePoint(endMm, surfaceRadiusEndMm),
)

/** AFT-side wall of the notch, surface → floor. */
fun KeywaySilhouetteNotch.wallStartMm(): Pair<KeywaySilhouettePoint, KeywaySilhouettePoint> =
    KeywaySilhouettePoint(startMm, surfaceRadiusStartMm) to KeywaySilhouettePoint(startMm, floorRadiusStartMm)

/** FWD-side wall of the notch, floor → surface. */
fun KeywaySilhouetteNotch.wallEndMm(): Pair<KeywaySilhouettePoint, KeywaySilhouettePoint> =
    KeywaySilhouettePoint(endMm, floorRadiusEndMm) to KeywaySilhouettePoint(endMm, surfaceRadiusEndMm)

/** Notch floor, AFT → FWD; parallel to the surface, so sloped on a taper. */
fun KeywaySilhouetteNotch.floorMm(): Pair<KeywaySilhouettePoint, KeywaySilhouettePoint> =
    KeywaySilhouettePoint(startMm, floorRadiusStartMm) to KeywaySilhouettePoint(endMm, floorRadiusEndMm)

/**
 * Build the silhouette notch for a keyway spanning [spanLoMm]..[spanHiMm] on a host whose outer
 * radius runs linearly from [hostStartRadiusMm] at [hostStartMm] to [hostEndRadiusMm] at
 * [hostEndMm] (a body passes the same radius twice).
 *
 * Clamps, in order:
 * - the keyway span is clamped to the host span; a span that survives as ≤ 0 long yields null,
 * - [depthMm] is clamped to stay strictly inside the surface radius, so the floor never reaches
 *   or crosses the centerline; a non-positive depth or a degenerate host radius yields null.
 *
 * Returns null for anything that would not draw — the caller skips it.
 */
fun buildKeywaySilhouetteNotch(
    spanLoMm: Float,
    spanHiMm: Float,
    hostStartMm: Float,
    hostEndMm: Float,
    hostStartRadiusMm: Float,
    hostEndRadiusMm: Float,
    depthMm: Float,
    side: KeywaySilhouetteSide,
): KeywaySilhouetteNotch? {
    if (depthMm <= 0f) return null

    val hostLo = min(hostStartMm, hostEndMm)
    val hostHi = max(hostStartMm, hostEndMm)
    if (hostHi - hostLo <= SILHOUETTE_EPS_MM) return null

    val lo = max(min(spanLoMm, spanHiMm), hostLo)
    val hi = min(max(spanLoMm, spanHiMm), hostHi)
    if (hi - lo <= SILHOUETTE_EPS_MM) return null

    // Host radius at an axial position; hostStartMm/hostEndMm carry the radii in host order,
    // so interpolate against those (not the sorted bounds) to keep a reversed host correct.
    fun radiusAt(xMm: Float): Float {
        val span = hostEndMm - hostStartMm
        if (span == 0f) return hostStartRadiusMm
        val t = ((xMm - hostStartMm) / span).coerceIn(0f, 1f)
        return hostStartRadiusMm + (hostEndRadiusMm - hostStartRadiusMm) * t
    }

    val r0 = radiusAt(lo)
    val r1 = radiusAt(hi)
    val minR = min(r0, r1)
    if (minR <= SILHOUETTE_EPS_MM) return null

    // Keep the floor strictly above the centerline: a cut deeper than the shaft radius is not a
    // notch, and drawing it would fold the polygon through the axis.
    val depth = min(depthMm, minR - SILHOUETTE_EPS_MM)
    if (depth <= 0f) return null

    return KeywaySilhouetteNotch(
        startMm = lo,
        endMm = hi,
        surfaceRadiusStartMm = r0,
        surfaceRadiusEndMm = r1,
        floorRadiusStartMm = r0 - depth,
        floorRadiusEndMm = r1 - depth,
        side = side,
    )
}

/**
 * Silhouette notch for this body's keyway under [clocking], or null when the body has no keyway,
 * the mode draws no notch, or the geometry is degenerate. Surface radius is constant (`diaMm/2`),
 * so the floor is parallel to the flat silhouette.
 */
fun Body.keywaySilhouetteNotch(clocking: KeywayClocking): KeywaySilhouetteNotch? {
    if (!hasKeyway) return null
    val side = keywaySilhouetteSideFor(clocking) ?: return null
    val (lo, hi) = keywayAbsSpanMm() ?: return null
    val r = diaMm * 0.5f
    return buildKeywaySilhouetteNotch(
        spanLoMm = lo, spanHiMm = hi,
        hostStartMm = startFromAftMm, hostEndMm = startFromAftMm + lengthMm,
        hostStartRadiusMm = r, hostEndRadiusMm = r,
        depthMm = keywayDepthMm, side = side,
    )
}

/**
 * Silhouette notch for this taper's keyway under [clocking], or null when the taper has no
 * keyway, the mode draws no notch, or the geometry is degenerate. Surface radius interpolates
 * between the taper's end radii, so the floor runs parallel to the sloped silhouette.
 */
fun Taper.keywaySilhouetteNotch(clocking: KeywayClocking): KeywaySilhouetteNotch? {
    if (!hasKeyway) return null
    val side = keywaySilhouetteSideFor(clocking) ?: return null
    val (lo, hi) = keywayAbsSpanMm() ?: return null
    return buildKeywaySilhouetteNotch(
        spanLoMm = lo, spanHiMm = hi,
        hostStartMm = startFromAftMm, hostEndMm = startFromAftMm + lengthMm,
        hostStartRadiusMm = startDiaMm * 0.5f, hostEndRadiusMm = endDiaMm * 0.5f,
        depthMm = keywayDepthMm, side = side,
    )
}
