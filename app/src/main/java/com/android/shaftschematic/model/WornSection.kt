package com.android.shaftschematic.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A designated worn (measured) area on the shaft — the consolidated runout/wear sheet's
 * marked span whose measured diameters print **inside** the shaft profile (rotated, stacked
 * across the span), with drawing lines knocked out behind each value. The digital form of
 * the shop's hand-marked section: two boundary lines on the profile and the mic readings
 * written between them.
 *
 * A **pure reference feature**, same contract as [Undercut] / [WearPit] / [WearDiaReading]:
 * never affects `coverageEndMm`, `ensureOverall`, body resolution, collision/overlap
 * validation, or the Free-to-End badge, and lives outside [ShaftSpec] (in [WearRecord],
 * rides the existing `wear_record` envelope field — additive, no codec change).
 *
 * ## Coordinate rule
 * Canonical storage is **shaft-space** mm from the AFT face ([startFromAftMm]) — the
 * [Undercut] convention, deliberately NOT component-keyed: a worn area may cross a liner
 * edge or span components, and shaft-space keying means no orphans and no decode pruning.
 * The only staleness is a span past the current shaft extent (OAL shrank): render-layer
 * clamp, stored record never mutated.
 *
 * [authoredReference] is display metadata only — which S.E.T. the Distance field was
 * entered against ([UndercutReference.AFT_SET] / [UndercutReference.FWD_SET]; the enum is
 * shared with undercuts, worn sections use only the SET values). Switching it re-projects
 * the *displayed* distance; canonical never moves (the `WearSpotReference` pattern).
 *
 * [diaMm] holds the measured diameters, canonical mm, each stored **verbatim** (golden
 * rule: typed measurements are sacred — never snapped, rounded, or derived). An empty list
 * means the section is designated but not yet measured: boundaries draw, interior stays
 * clear (write-in space on a blank draft). Values print in list order.
 *
 * Units: mm.
 */
@Serializable
data class WornSection(
    val id: String = UUID.randomUUID().toString(),
    /** Shaft-space mm from the AFT face (x = 0) to the section's AFT boundary. */
    val startFromAftMm: Float = 0f,
    val lengthMm: Float = 0f,
    /** Measured diameters, canonical mm, verbatim, printed in order. Empty = unmeasured. */
    val diaMm: List<Float> = emptyList(),
    val authoredReference: UndercutReference = UndercutReference.AFT_SET,
)
