package com.android.shaftschematic.util

/**
 * A drawn dimension value that may carry a converted second unit.
 *
 * A dual sheet prints both units on every dimension (`CLAUDE.md` §"Mixed units and dual display
 * are a DISPLAY AXIS"). Two renderings exist, chosen by [DualUnitLayout]:
 *
 * - **inline** — `1 1/2" [38.1 mm]`, one line, width-only growth;
 * - **stacked** — the primary over the secondary, two lines.
 *
 * Stacked is NARROWER (its width is `max(primary, secondary)`, not their sum plus brackets), so
 * it seats in a dimension line's break far more often — which is what pays for its extra height.
 * See `docs/DualUnitStacking_PLAN.md`.
 *
 * The two terms therefore have to survive as separate strings all the way to the draw site: a
 * pre-joined `String` cannot be stacked. Formatting stays in `pdf/UnitFormat.kt`; this type only
 * carries the result.
 *
 * Pure — no Android, no `pdf`/`ui` dependency.
 *
 * @property primary The value in the unit this dimension prints in (its component's override, or
 *   the document unit). Always present, always carries its unit suffix.
 * @property secondary The converted courtesy value, `null` on a single-unit sheet. Also always
 *   carries its unit suffix: the moment a drawing mixes units, a bare number is how a shaft gets
 *   machined wrong.
 */
data class DualLabel(val primary: String, val secondary: String? = null) {

    /** True when there are two terms to set — the only case any stacking applies to. */
    val isDual: Boolean get() = secondary != null

    /** The one-line rendering, `primary [secondary]`. A single-unit label is just its primary. */
    fun inline(): String = if (secondary == null) primary else "$primary [$secondary]"

    /** Both terms, primary first — the draw order of a stack, and of nothing else. */
    fun lines(): List<String> = if (secondary == null) listOf(primary) else listOf(primary, secondary)

    companion object {
        /** A single-unit label; every rendering of it is byte-identical to the plain string. */
        fun single(text: String): DualLabel = DualLabel(text, null)
    }
}

/**
 * How a dual value is SET — Settings → Drawing → "Dual-unit layout", and both PDF options sheets.
 *
 * A layout choice, not document state: the same job printed by two shops may want different
 * layouts, exactly like fraction style and arrowhead size. It is threaded explicitly to the
 * composers (never through a process-wide mirror like `FractionTypography.active`) because it
 * moves LAYOUT, and layout inputs are passed by hand in this codebase so a preview's re-render
 * key list can name them.
 *
 * [INLINE] remains the default: it is what shipped, and it never changes a vertical budget.
 */
enum class DualUnitLayout {
    /** `1 1/2" [38.1 mm]` on one line. */
    INLINE,

    /** Primary over secondary, two lines — narrower, taller. */
    STACKED;

    fun uiLabel(): String = when (this) {
        INLINE -> "Inline"
        STACKED -> "Stacked"
    }

    companion object {
        /** The shipped rendering — one source for the pref default and [fromName]'s fallback. */
        val Default: DualUnitLayout = INLINE

        /** Tolerant decode for a persisted name; an unknown value falls back to the default. */
        fun fromName(raw: String?): DualUnitLayout =
            if (raw.isNullOrBlank()) Default
            else runCatching { valueOf(raw) }.getOrDefault(Default)
    }
}
