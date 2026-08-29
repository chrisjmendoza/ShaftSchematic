package com.android.shaftschematic.util

import com.android.shaftschematic.doc.stripShaftDocExtension

/**
 * DocumentNaming
 *
 * Purpose
 * Build safe, human-friendly default filenames from project information.
 */
object DocumentNaming {

    /**
     * Returns a base filename (without extension) derived from project information,
     * or null when all inputs are blank.
     *
     * Order: job number → customer → vessel → (optional) suffix.
     */
    fun suggestedBaseName(
        jobNumber: String,
        customer: String,
        vessel: String,
        suffix: String? = null
    ): String? {
        val core = listOf(jobNumber, customer, vessel)
            .map(::sanitizePart)
            .filter { it.isNotBlank() }

        // If the only thing we have is a suffix (e.g., PORT), don't use it as a filename.
        // Let callers fall back to a generated default like "Shaft_yyyyMMdd_HHmm".
        if (core.isEmpty()) return null

        val extra = sanitizePart(suffix ?: "")
        return (core + listOfNotNull(extra.takeIf { it.isNotBlank() }))
            .joinToString(" - ")
    }

    /**
     * Returns the base name (no extension) a saved document should be offered as, or null when
     * no rename is worth offering.
     *
     * Null when:
     * - [currentDocumentName] is null — an unnamed document is named by the save screen, which
     *   already suggests from the same project information.
     * - [suggestedBaseName] has nothing to build from (all project fields blank).
     * - the current name's base already equals the suggestion, compared case-insensitively —
     *   there is nothing to change.
     *
     * Otherwise the suggestion itself, verbatim as [suggestedBaseName] built it.
     */
    fun renameSuggestionBase(
        currentDocumentName: String?,
        jobNumber: String,
        customer: String,
        vessel: String,
        positionSuffix: String? = null,
    ): String? {
        if (currentDocumentName == null) return null

        val suggested = suggestedBaseName(
            jobNumber = jobNumber,
            customer = customer,
            vessel = vessel,
            suffix = positionSuffix,
        ) ?: return null

        val currentBase = stripShaftDocExtension(currentDocumentName)
        if (currentBase.equals(suggested, ignoreCase = true)) return null

        return suggested
    }

    /**
     * Returns [desired] if no name in [existing] takes it, otherwise the first free
     * "desired (2)", "desired (3)", … .
     *
     * [existing] is base names (no extension); the comparison is case-insensitive, because
     * the document store treats "Job 12" and "job 12" as one file — a match that only
     * differed in case would still overwrite.
     *
     * [desired] is returned verbatim when it is free: this suffixes a name, it never
     * sanitizes one. Callers normalize first (see `InternalStorage.normalizeShaftDocName`).
     */
    fun uniqueBaseName(existing: Collection<String>, desired: String): String {
        val taken = existing.mapTo(mutableSetOf()) { it.lowercase() }
        if (desired.lowercase() !in taken) return desired

        var n = 2
        while (true) {
            val candidate = "$desired ($n)"
            if (candidate.lowercase() !in taken) return candidate
            n++
        }
    }

    private fun sanitizePart(raw: String): String {
        val collapsed = raw.trim().replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty()) return ""

        // Avoid common illegal path characters (Windows + generally problematic).
        return collapsed
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("[\\u0000-\\u001F]"), "")
            .trim()
    }
}
