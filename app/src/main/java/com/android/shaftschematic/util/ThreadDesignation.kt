package com.android.shaftschematic.util

/**
 * Parser/formatter for ISO metric thread designations (e.g. `M20×2.5`).
 *
 * A metric thread is self-declaring on a drawing: `M20×2.5` names both the major
 * diameter (20 mm) and the pitch (2.5 mm/turn). Once converted to decimal inches the
 * designation stops meaning anything, so metric threads keep their native units — this
 * type is the round-trip between the printed designation and the canonical **mm** fields
 * ([Threads.majorDiaMm], [Threads.pitchMm]).
 *
 * Pure: no Android, no formatting-preference state. Values are stored verbatim (golden
 * rule); this only reshapes them for entry/display.
 */
data class ThreadDesignation(
    /** Major (nominal) diameter in mm. */
    val majorDiaMm: Float,
    /** Pitch in mm/turn, or null for a coarse designation with the pitch omitted (`M20`). */
    val pitchMm: Float?,
) {
    /** Renders the canonical `M<dia>×<pitch>` form, trailing zeros trimmed. */
    fun format(): String = ThreadDesignation.format(majorDiaMm, pitchMm)

    companion object {
        /** Multiplication signs accepted between diameter and pitch: `×`, `x`, `X`, `*`. */
        private val SEPARATORS = charArrayOf('×', 'x', 'X', '*')

        /**
         * Parses `M20×2.5`, `M20x2.5`, `M20` (coarse, pitch omitted), tolerating surrounding
         * whitespace and either separator glyph. Returns null when the text is not a metric
         * designation (empty, missing `M`, non-numeric) so callers can fall back to imperial.
         */
        fun parse(raw: String?): ThreadDesignation? {
            val t = raw?.trim() ?: return null
            if (t.length < 2) return null
            if (t[0] != 'M' && t[0] != 'm') return null
            val body = t.substring(1).trim()

            val sepIndex = body.indexOfAny(SEPARATORS)
            val diaText: String
            val pitchText: String?
            if (sepIndex < 0) {
                diaText = body
                pitchText = null
            } else {
                diaText = body.substring(0, sepIndex).trim()
                pitchText = body.substring(sepIndex + 1).trim().ifEmpty { null }
            }

            val dia = diaText.toFloatOrNull() ?: return null
            if (dia <= 0f) return null
            val pitch = pitchText?.toFloatOrNull()
            if (pitchText != null && (pitch == null || pitch <= 0f)) return null
            return ThreadDesignation(dia, pitch)
        }

        /** `M<dia>×<pitch>` with trailing zeros trimmed; pitch term dropped when null. */
        fun format(majorDiaMm: Float, pitchMm: Float?): String {
            val dia = trim(majorDiaMm)
            val pitch = pitchMm?.let { trim(it) }
            return if (pitch == null) "M$dia" else "M$dia×$pitch"
        }

        private fun trim(value: Float): String {
            val s = String.format(java.util.Locale.US, "%.3f", value)
            return s.trimEnd('0').trimEnd('.')
        }
    }
}
