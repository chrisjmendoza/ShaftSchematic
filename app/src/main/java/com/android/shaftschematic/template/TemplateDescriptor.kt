package com.android.shaftschematic.template

import com.android.shaftschematic.doc.stripShaftDocExtension
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.coverageEndMm
import com.android.shaftschematic.model.maxOuterDiaMm
import com.android.shaftschematic.pdf.formatDiaWithUnit
import com.android.shaftschematic.pdf.formatLenWithUnit
import com.android.shaftschematic.util.UnitSystem

/**
 * TemplateDescriptor — the one-line summary a template card and a template's suggested name are
 * built from.
 *
 * Two templates can share a bucket, a size and a liner count and still be different shafts: the
 * liners sit in different PLACES. `Ø8 liners × 3` twice over is exactly the case a browser has to
 * disambiguate without leaning on whatever the user happened to type in the name field, so the
 * descriptor carries a **liner zone string** — each liner's centre mapped into the aft / middle /
 * forward third of the shaft, read AFT→FWD ("A·M·F", "A·A·F").
 *
 * Everything here is DERIVED from the stored spec, never stored — the `TemplateBuckets` rule, for
 * the same reason: an edited template re-describes itself and no index can fall out of sync.
 *
 * Pure Kotlin (the formatters it borrows from `pdf/UnitFormat.kt` are themselves Android-free), so
 * the whole thing is unit-testable.
 */

/** Zone marks, aft → forward. Single letters so three of them still fit a card's caption. */
const val TEMPLATE_ZONE_AFT = "A"
const val TEMPLATE_ZONE_MID = "M"
const val TEMPLATE_ZONE_FWD = "F"

/** Reads as a separator without reading as a minus sign or a decimal point. */
const val TEMPLATE_ZONE_SEPARATOR = "·"

/** The same separator for a FILENAME — `·` is legal on most filesystems but ugly in a picker. */
const val TEMPLATE_ZONE_NAME_SEPARATOR = "-"

/**
 * Where each liner sits along the shaft, AFT→FWD, e.g. `"A·M·F"` (one per third) or `"A·A·F"`
 * (two aft, one forward).
 *
 * A liner is placed by its **centre** rather than either edge: an edge sits in a neighbouring third
 * as often as not, and a shop describes a liner by where it is, not where it starts.
 *
 * The span is the shaft's overall length, falling back to [coverageEndMm] when OAL is unset — the
 * same order the drawing uses. Returns null when there is nothing to describe: no finished liners,
 * or no span to measure against (a spec with neither is not yet a shaft).
 */
fun linerZoneString(spec: ShaftSpec): String? {
    val centers = spec.liners
        .filter { it.odMm > 0f }
        .map { it.startFromAftMm + it.lengthMm / 2f }
    if (centers.isEmpty()) return null

    val spanMm = spec.overallLengthMm.takeIf { it > 0f }
        ?: spec.coverageEndMm().takeIf { it > 0f }
        ?: return null

    return centers.sorted().joinToString(TEMPLATE_ZONE_SEPARATOR) { zoneMark(it, spanMm) }
}

/**
 * The third [centerMm] falls in. Boundaries belong to the FORWARD side (a centre exactly on the
 * one-third mark reads M, not A) so the three zones tile the shaft with no gap and no overlap.
 */
private fun zoneMark(centerMm: Float, spanMm: Float): String {
    val frac = (centerMm / spanMm).coerceIn(0f, 1f)
    return when {
        frac < 1f / 3f -> TEMPLATE_ZONE_AFT
        frac < 2f / 3f -> TEMPLATE_ZONE_MID
        else -> TEMPLATE_ZONE_FWD
    }
}

/**
 * `94 1/2" OAL · Ø6" max · 3 liners (A·M·F)` — the template card's caption.
 *
 * Formatted in the app's ACTIVE [unit], not the template's authored one, so the caption reads in
 * the units the user is working in right now; the shop label formatters (`formatLenWithUnit` /
 * `formatDiaWithUnit`) are the ones the drawings and footers already use, so a caption and a sheet
 * never disagree about how six inches is written.
 *
 * The max Ø comes from the canonical [maxOuterDiaMm], which sees threads and tapers too — a
 * thread-dominated shaft used to advertise a smaller diameter than it has.
 *
 * A linerless shaft says "No liners" **once**: it has no zones to report, and the count label
 * already carries the whole story.
 */
fun templateDescriptor(spec: ShaftSpec, unit: UnitSystem): String {
    val parts = mutableListOf<String>()

    if (spec.overallLengthMm > 0f) {
        parts += "${formatLenWithUnit(spec.overallLengthMm.toDouble(), unit)} OAL"
    }
    val maxDia = spec.maxOuterDiaMm()
    if (maxDia > 0f) parts += "Ø${formatDiaWithUnit(maxDia.toDouble(), unit)} max"

    val count = templateLinerCount(spec)
    val zones = if (count == TemplateLinerCount.NONE) null else linerZoneString(spec)
    parts += if (zones == null) count.label else "${count.label} ($zones)"

    return parts.joinToString(" · ")
}

/**
 * "Files under: …" — the bucket path a template will be filed at.
 *
 * A linerless shaft buckets as `None` on BOTH axes, which spelled out longhand reads
 * "No liners · No liners". It is one fact, so it is said once.
 */
fun templateBucketPath(spec: ShaftSpec): String {
    val size = templateSizeBucket(spec)
    val count = templateLinerCount(spec)
    if (size is TemplateSizeBucket.None && count == TemplateLinerCount.NONE) return count.label
    return "${size.label} · ${count.label}"
}

/**
 * Names a new template after what it IS — its liner size, count and layout — rather than after the
 * job it came from, since job identity is exactly what a template drops. Falls back to the document
 * name the user was already typing when the shaft has no liners to describe.
 *
 * The zones use [TEMPLATE_ZONE_NAME_SEPARATOR], so the seed is a filename first and a caption
 * second: `6in 3 liners A-M-F`.
 */
fun suggestedTemplateName(spec: ShaftSpec, documentName: String): String {
    val size = templateSizeBucket(spec)
    val count = templateLinerCount(spec)
    return when {
        size is TemplateSizeBucket.Inches -> {
            val zones = linerZoneString(spec)
                ?.replace(TEMPLATE_ZONE_SEPARATOR, TEMPLATE_ZONE_NAME_SEPARATOR)
            listOfNotNull("${size.inches}in ${count.label.lowercase()}", zones).joinToString(" ")
        }
        count == TemplateLinerCount.NONE ->
            stripShaftDocExtension(documentName.trim()).ifBlank { "Straight shaft" }
        else -> stripShaftDocExtension(documentName.trim()).ifBlank { "Shaft template" }
    }
}

/**
 * `base`, or the first `base (n)` that no template already claims.
 *
 * A derived seed describes a SHAPE, so two templates of the same shape naturally seed the same
 * name — and the save dialog would then open pre-loaded with a name whose only outcome is an
 * overwrite prompt. Ordinals are compared case-insensitively and against the base names (extension
 * stripped) for the same reason the store's collision check is: one browser row must never fork
 * into two case-variant files.
 */
fun dedupeTemplateName(base: String, existingFilenames: Collection<String>): String {
    val taken = existingFilenames.map { stripShaftDocExtension(it).lowercase() }.toSet()
    if (base.lowercase() !in taken) return base
    var n = 2
    while ("$base ($n)".lowercase() in taken) n++
    return "$base ($n)"
}
