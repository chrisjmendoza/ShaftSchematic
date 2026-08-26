package com.android.shaftschematic.geom

import com.android.shaftschematic.model.KeywaySpan
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.keywayAbsSpanMm

/**
 * ONE builder for the [ProfileFeatureSpan] list that feeds [solveMaxProfileScale] /
 * [buildCompressedProfileXMap]. The schematic composer, the runout/consolidated composer,
 * and the UI's liner-compression estimator all consume the same structure and previously
 * each built it by hand — the estimator mirroring the composers "by convention only", which
 * is exactly how a floor tweak on one sheet silently breaks the kept-% readout.
 *
 * The per-sheet differences are the parameters and nothing else:
 * - [linerFloorPt]/[threadFloorPt] — the schematic uses the LEAN `SCHEMATIC_MIN_*` floors
 *   (its values live on rails and callouts, so proportion wins), the runout/consolidated
 *   sheet and the estimator keep the writable `PROFILE_MIN_*` floors.
 * - [linerMinFracOfTrue] — the per-job "Liner compression" raise (best-effort, λ-fitted;
 *   the drawn height never yields to it).
 *
 * Structure shared by every consumer:
 * - Tapers: ratio-preserving fraction-of-true floor, NO flat floor (two very different
 *   taper lengths must never draw equal — on-device direction); the drawn height never
 *   yields to it.
 * - Liners: compress in SIZE only above their flat floor — proportional foreshortening,
 *   never a body-style S-break cutout (on-device clarification).
 * - Threads: flat floor (the hatched stub stays legible).
 * - Body-keyway windows pin at true scale ([keywayPinnedBodySpans]).
 *
 * Tapers/liners/threads read the same from a stored spec and a `withResolvedBodies` copy
 * (only bodies are replaced), so callers may pass either.
 */
fun profileFeatureSpans(
    spec: ShaftSpec,
    linerFloorPt: Float,
    threadFloorPt: Float,
    linerMinFracOfTrue: Float,
): List<ProfileFeatureSpan> = buildList {
    spec.tapers.forEach {
        add(
            ProfileFeatureSpan(
                it.startFromAftMm, it.startFromAftMm + it.lengthMm, 0f,
                minWidthFracOfTrue = PROFILE_TAPER_MIN_FRAC_OF_TRUE,
            )
        )
    }
    spec.liners.forEach {
        add(
            ProfileFeatureSpan(
                it.startFromAftMm, it.startFromAftMm + it.lengthMm, linerFloorPt,
                minWidthFracOfTrue = linerMinFracOfTrue,
            )
        )
    }
    spec.threads.forEach {
        add(ProfileFeatureSpan(it.startFromAftMm, it.startFromAftMm + it.lengthMm, threadFloorPt))
    }
    addAll(keywayPinnedBodySpans(spec))
}

/**
 * The protected axial window around each body keyway: the slot's own span
 * ([keywayAbsSpanMm]) padded by one keyway width at each end (mill arc + spoon-bowl
 * overhang), clamped to the host body. This window — NOT the whole body — is what pins at
 * true scale and what the S-break gap must steer clear of: a 95%-shaft body must stay free
 * to compress and break or a long shaft cannot render at all (on-device report), while the
 * slot itself must never foreshorten.
 *
 * Reads **STORED** bodies: a resolved body carries no keyway fields (`bodyForPdf` builds
 * drawable geometry only), so filtering a resolved list silently yields nothing.
 */
fun bodyKeywayProtectedSpansMm(spec: ShaftSpec): List<KeywaySpan> =
    spec.bodies.mapNotNull { b ->
        val span = b.keywayAbsSpanMm() ?: return@mapNotNull null
        val pad = b.keywayWidthMm
        KeywaySpan(
            loMm = (span.loMm - pad).coerceAtLeast(b.startFromAftMm),
            hiMm = (span.hiMm + pad).coerceAtMost(b.startFromAftMm + b.lengthMm),
        )
    }

/**
 * True-width pins for the body-keyway windows of [spec] — a slot drawn on a foreshortened
 * body is not real geometry, so the slot's padded window ([bodyKeywayProtectedSpansMm])
 * demands full width and the layout yields around it. Deliberately the WINDOW, never the
 * whole host body (see that helper's doc). Shared by the schematic, the runout/consolidated
 * sheet, and the UI's scale estimator so the span that pins is exactly the span the slot
 * draws in.
 */
fun keywayPinnedBodySpans(spec: ShaftSpec): List<ProfileFeatureSpan> =
    bodyKeywayProtectedSpansMm(spec).map {
        ProfileFeatureSpan(it.loMm, it.hiMm, Float.MAX_VALUE)
    }
