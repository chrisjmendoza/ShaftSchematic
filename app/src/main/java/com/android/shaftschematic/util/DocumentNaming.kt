package com.android.shaftschematic.util

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
