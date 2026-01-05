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
     * Order: job number → customer → vessel.
     */
    fun suggestedBaseName(jobNumber: String, customer: String, vessel: String): String? {
        val parts = listOf(jobNumber, customer, vessel)
            .map(::sanitizePart)
            .filter { it.isNotBlank() }

        return parts.takeIf { it.isNotEmpty() }?.joinToString(" - ")
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
