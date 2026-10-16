// file: app/src/main/java/com/android/shaftschematic/geom/BreakGlyphMath.kt
package com.android.shaftschematic.geom

import kotlin.math.sqrt

/**
 * Reach of the round-stock S-break glyph, as pure numbers.
 *
 * The glyph itself is drawn in `pdf/BreakSymbol.kt` (`drawBreakEdge`, PDF) and
 * `ui/screen/LinerWearDetail.kt` (`drawBreakEdgeCompose`, canvas port); these constants describe
 * how far its curves reach horizontally, so layout code in any package — `geom` included, which
 * must never import `pdf` or `ui` — can reserve room for it. They are derived from the glyph's
 * cubic control geometry: changing that geometry changes these fractions.
 */

/**
 * Return-sweep width relative to the S's own lobe; 1 = exact mirror. Public because both glyph
 * draw sites (`pdf/BreakSymbol.kt`'s `drawBreakEdge` and its Compose port,
 * `ui/screen/LinerWearDetail.kt`'s `drawBreakEdgeCompose`) read it rather than keeping a copy
 * that can be tuned out of step.
 */
const val RETURN_SWEEP_FULLNESS = 1.5f

/**
 * How far a break edge's curves reach horizontally INWARD toward its partner, as a
 * fraction of the amplitude — the pair's combined reach in each half of the shaft.
 * Derived from the cubics in `drawBreakEdge`: the main S peaks at √3/6 of its ±amplitude
 * controls, and the return sweep (controls at [RETURN_SWEEP_FULLNESS]·amp/2 and /4) at
 * [RETURN_SWEEP_FULLNESS]·√3/6; one edge contributes its S while the other contributes
 * its sweep in the same half, so the pair closes (1 + fullness)·√3/6 of the amplitude.
 * Changing the glyph's control geometry changes this fraction.
 */
val BREAK_PAIR_REACH_FRAC = (1f + RETURN_SWEEP_FULLNESS) * (sqrt(3f) / 6f)

/**
 * How far a break edge's curves reach horizontally OUTWARD past its x — into the void
 * side — as a fraction of the amplitude. The return sweep is the far reacher: its cubic
 * (controls at [RETURN_SWEEP_FULLNESS]·amp/2 and /4) simplifies to
 * `(3k/4)·t(1−t)(2−t)·amp`, which peaks at `k·√3/6·amp` — past the main S's own `√3/6`.
 * Two packed wear strips' facing break stubs each bulge this far into their shared
 * gutter, so the gutter must host both reaches plus daylight — see
 * `spreadWearStripRowGutters` / `wearStripBreakAmplitudePt` (`pdf/WearStripLayout.kt`). It is
 * also the bound on how far a lone break reaches to EITHER side of its line, which is what
 * the undercut window-end break room reserves (`undercutBreakRoomMm`, `geom/UndercutMath.kt`).
 * Changing the glyph's control geometry changes this fraction.
 */
val BREAK_EDGE_OUTWARD_REACH_FRAC = RETURN_SWEEP_FULLNESS * (sqrt(3f) / 6f)

/**
 * The undercut window-end break's amplitude as a fraction of the drawn radius at that end; the
 * ONE value both window-end draw sites (`ui/screen/UndercutDetail.kt`, canvas;
 * `pdf/UndercutPdfComposer.kt`'s `drawUndercutWindowEnd`, PDF) and the break-room math
 * (`undercutBreakRoomMm`) read. The PDF additionally caps the result
 * (`UNDERCUT_BREAK_AMP_MAX_PT`).
 */
const val UNDERCUT_BREAK_AMP_FRAC_OF_R = 0.6f
