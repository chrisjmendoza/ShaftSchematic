package com.android.shaftschematic.template

import com.android.shaftschematic.model.ShaftSpec
import kotlin.math.roundToInt

/**
 * TemplateBuckets — pure bucketing for the template browser.
 *
 * The browser groups saved templates by **liner size** and then by **liner count**. Both keys
 * are DERIVED from the stored spec at scan time; there is no sidecar index to keep in sync,
 * so a template that gets edited re-files itself and no orphan class of bug exists.
 *
 * Canonical units are mm throughout, per the model rule; the inch numbers here are bucket
 * IDENTITY (the size a shop says out loud), and the display layer formats headings in the
 * user's active unit.
 *
 * No Android or Compose dependencies — unit-testable as plain Kotlin.
 */

/** Smallest and largest nominal liner size that gets its own bucket. */
const val TEMPLATE_MIN_SIZE_IN = 4
const val TEMPLATE_MAX_SIZE_IN = 12

private const val MM_PER_IN = 25.4f

/** Liner-count buckets. Anything above three folds into [THREE_PLUS]. */
enum class TemplateLinerCount(val label: String) {
    NONE("No liners"),
    ONE("1 liner"),
    TWO("2 liners"),
    THREE("3 liners"),
    THREE_PLUS("4+ liners"),
}

/**
 * Nominal liner-size bucket.
 *
 * [NONE] holds linerless templates (a straight shaft template still deserves a home) and
 * [OTHER] catches sizes outside the 4"–12" range rather than dropping them.
 */
sealed interface TemplateSizeBucket {
    val label: String

    /** A nominal size in whole inches, 4..12. */
    data class Inches(val inches: Int) : TemplateSizeBucket {
        override val label: String get() = "$inches\" liners"
    }

    data object None : TemplateSizeBucket {
        override val label: String get() = "No liners"
    }

    data object Other : TemplateSizeBucket {
        override val label: String get() = "Other sizes"
    }
}

/**
 * Sort key placing "No liners" first, then 4"…12" ascending, then "Other sizes" last.
 */
fun TemplateSizeBucket.sortKey(): Int = when (this) {
    is TemplateSizeBucket.None -> -1
    is TemplateSizeBucket.Inches -> inches
    is TemplateSizeBucket.Other -> Int.MAX_VALUE
}

/**
 * The size bucket for a spec: the **largest** liner OD, rounded to the nearest whole inch.
 *
 * Largest wins when a shaft carries liners of different sizes — it is the number that
 * characterises the job, and the aft/fwd distinction is not something a template browser can
 * meaningfully sort on.
 */
fun templateSizeBucket(spec: ShaftSpec): TemplateSizeBucket {
    val maxOdMm = spec.liners.maxOfOrNull { it.odMm }?.takeIf { it > 0f }
        ?: return TemplateSizeBucket.None
    // Double division: an inch-authored OD stored as Float (e.g. 3.5" = 88.9mm) must land
    // exactly ON the round-half boundary, not a Float ULP below it, or the bucket flips.
    val inches = (maxOdMm.toDouble() / MM_PER_IN).roundToInt()
    return if (inches in TEMPLATE_MIN_SIZE_IN..TEMPLATE_MAX_SIZE_IN) {
        TemplateSizeBucket.Inches(inches)
    } else {
        TemplateSizeBucket.Other
    }
}

/** The liner-count bucket for a spec. Zero-OD liners are ignored — they are unfinished rows. */
fun templateLinerCount(spec: ShaftSpec): TemplateLinerCount =
    when (spec.liners.count { it.odMm > 0f }) {
        0 -> TemplateLinerCount.NONE
        1 -> TemplateLinerCount.ONE
        2 -> TemplateLinerCount.TWO
        3 -> TemplateLinerCount.THREE
        else -> TemplateLinerCount.THREE_PLUS
    }
