package com.android.shaftschematic.doc

/**
 * DocumentFormat
 *
 * Single source of truth for the shaft document file format.
 *
 * Contract:
 * - File contents remain JSON.
 * - Default/current extension is `.shaft`.
 * - Legacy extension(s) remain readable for backward compatibility.
 */
const val SHAFT_EXT = "shaft"
const val SHAFT_DOT_EXT = ".shaft"

// Seeded/bundled sample docs are tagged via notes so we can safely identify them later.
// This marker is intended to be stable across versions.
const val SEEDED_SAMPLE_NOTES_PREFIX = "[SAMPLE] "

/** Returns true when [notes] indicates a bundled/seeded sample document. */
fun isSeededSampleNotes(notes: String?): Boolean {
    val trimmed = notes?.trimStart() ?: return false
    // Accept the stable marker, plus any historical sample markers.
    return trimmed.startsWith("[SAMPLE]") || trimmed.startsWith("[SAMPLE_")
}

// Custom MIME for SAF create/open. Content is still JSON.
const val SHAFT_MIME = "application/x-shaftschematic"

val LEGACY_EXTS: Set<String> = setOf("json")

internal fun isLegacyExtension(ext: String): Boolean = LEGACY_EXTS.contains(ext.lowercase())

/** Returns the base name (no `.shaft` or legacy extension) for display/comparisons. */
fun stripShaftDocExtension(filename: String): String {
    val lower = filename.lowercase()
    if (lower.endsWith(SHAFT_DOT_EXT)) return filename.dropLast(SHAFT_DOT_EXT.length)

    for (ext in LEGACY_EXTS) {
        val dotExt = ".${ext.lowercase()}"
        if (lower.endsWith(dotExt)) return filename.dropLast(dotExt.length)
    }

    return filename
}
