// file: app/src/main/java/com/android/shaftschematic/util/TaperCalcPresentation.kt
package com.android.shaftschematic.util

/**
 * What the taper calculator SHOWS for a given set of entries — pure, so every state the dialog
 * can be in is asserted without a Compose harness (the calculator dialogs cannot be hosted
 * under the Robolectric harness; a calculator whose display rules live in the composable is a
 * calculator with no tests).
 *
 * The calculator is button-driven: nothing is derived until the user taps Calculate, and the
 * result is shown IN the empty field — as a preview the user reads, never as text written into
 * it (the golden rule: a field holds what the user typed and nothing else). Because a derived
 * number that no longer follows from the fields would be a lie on screen, the dialog drops the
 * calculated state the moment any entry or the unit changes, and this function simply receives
 * `calculated = false` again.
 *
 * Feedback splits by what it is about:
 * - A field whose TEXT cannot be read (`"abc"`, `"1:"`) is red immediately — that is about the
 *   keystrokes, and waiting for a button to say so would let a typo hide behind a blank result.
 * - Everything about the SET of values — too few, a small end larger than the large end, a rate
 *   that consumes the diameter, a typed rate that disagrees — waits for Calculate. An empty form
 *   is not wrong; only a form that was asked to solve and could not is.
 */

enum class TaperCalcField(val shortName: String) {
    LARGE_DIA("Large end Ø"),
    SMALL_DIA("Small end Ø"),
    LENGTH("Length"),
    RATE("Taper rate"),
}

/** The raw text of the four fields plus the entry unit — what the user has typed, verbatim. */
data class TaperCalcEntries(
    val largeDia: String = "",
    val smallDia: String = "",
    val length: String = "",
    val rate: String = "",
    val unit: UnitSystem = UnitSystem.INCHES,
) {
    fun text(field: TaperCalcField): String = when (field) {
        TaperCalcField.LARGE_DIA -> largeDia
        TaperCalcField.SMALL_DIA -> smallDia
        TaperCalcField.LENGTH -> length
        TaperCalcField.RATE -> rate
    }

    fun with(field: TaperCalcField, text: String): TaperCalcEntries = when (field) {
        TaperCalcField.LARGE_DIA -> copy(largeDia = text)
        TaperCalcField.SMALL_DIA -> copy(smallDia = text)
        TaperCalcField.LENGTH -> copy(length = text)
        TaperCalcField.RATE -> copy(rate = text)
    }
}

/**
 * One field's display state.
 *
 * @param derivedText The calculated value for this field, in the entry unit WITHOUT its suffix
 *   (the field already shows one) — shown as an italic preview in the empty field. `null` when
 *   this field was typed, or nothing has been calculated.
 * @param supportingText A line under the field. Only the rate carries one: the inches-per-foot
 *   reading and the common rate it lands on.
 * @param isError Red outline — a parse failure at any time, or, after Calculate, a value that
 *   is missing or cannot make a taper with the others.
 */
data class TaperCalcFieldState(
    val derivedText: String? = null,
    val supportingText: String? = null,
    val isError: Boolean = false,
)

/**
 * The whole dialog's display state. [message] is the one line under the Calculate button;
 * [messageIsError] colours it. [isSolved] is true whenever a full taper exists on screen.
 */
data class TaperCalcPresentation(
    val fields: Map<TaperCalcField, TaperCalcFieldState>,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val isSolved: Boolean = false,
) {
    fun field(f: TaperCalcField): TaperCalcFieldState = fields.getValue(f)
}

/**
 * Reads one length entry in the selected unit, as canonical mm. Blank and unreadable both come
 * back `null` — "not entered" for the solve; [isUnreadable] is what separates them.
 */
private fun entryToMm(raw: String, unit: UnitSystem): Double? {
    if (raw.isBlank()) return null
    val v = parseFractionOrDecimal(raw) ?: return null
    return unit.toMillimeters(v)
}

/** Typed something, and it does not read as a number (or, for the rate, as a ratio). */
private fun isUnreadable(field: TaperCalcField, raw: String): Boolean {
    if (raw.isBlank()) return false
    return when (field) {
        // Bare "1" stays blocked as ambiguous, the same rule the taper card applies.
        TaperCalcField.RATE -> parseTaperRateText(raw, allowAmbiguousBareOne = false) == null
        else -> parseFractionOrDecimal(raw) == null
    }
}

fun presentTaperCalc(entries: TaperCalcEntries, calculated: Boolean): TaperCalcPresentation {
    val unit = entries.unit
    val unreadable = TaperCalcField.entries.filter { isUnreadable(it, entries.text(it)) }.toSet()
    val blank = TaperCalcField.entries.filter { entries.text(it).isBlank() }.toSet()

    // Parse errors are live: they are about the keystrokes, not about the solve.
    val states = TaperCalcField.entries.associateWith { f ->
        TaperCalcFieldState(isError = f in unreadable)
    }.toMutableMap()

    if (!calculated) return TaperCalcPresentation(states)

    val let = entryToMm(entries.largeDia, unit)
    val set = entryToMm(entries.smallDia, unit)
    val len = entryToMm(entries.length, unit)
    val slope = parseTaperRateText(entries.rate, allowAmbiguousBareOne = false)?.toDouble()

    return when (val result = solveTaperCalc(let, set, len, slope)) {
        TaperCalcResult.Incomplete -> {
            // Too few values: the fields that could complete the solve go red, and the message
            // names them — "enter one more value" is only half an instruction.
            val missing = TaperCalcField.entries.filter { it in blank || it in unreadable }
            missing.forEach { states[it] = states.getValue(it).copy(isError = true) }
            val message = when {
                unreadable.isNotEmpty() -> "Fix the highlighted value first."
                missing.size == 2 ->
                    "Enter one more value — ${missing[0].shortName} or ${missing[1].shortName}."
                else -> "Enter any three values to calculate the fourth."
            }
            TaperCalcPresentation(states, message = message, messageIsError = true)
        }

        is TaperCalcResult.Invalid -> {
            invalidFields(result.issue).forEach { states[it] = states.getValue(it).copy(isError = true) }
            TaperCalcPresentation(states, message = issueMessage(result.issue), messageIsError = true)
        }

        is TaperCalcResult.Solved -> {
            val derivedField = when (result.unknown) {
                TaperCalcUnknown.LARGE_DIA -> TaperCalcField.LARGE_DIA
                TaperCalcUnknown.SMALL_DIA -> TaperCalcField.SMALL_DIA
                TaperCalcUnknown.LENGTH -> TaperCalcField.LENGTH
                TaperCalcUnknown.RATE -> TaperCalcField.RATE
                null -> null
            }
            if (derivedField != null) {
                val text = when (derivedField) {
                    TaperCalcField.LARGE_DIA -> taperCalcNumberText(result.largeDiaMm, unit)
                    TaperCalcField.SMALL_DIA -> taperCalcNumberText(result.smallDiaMm, unit)
                    TaperCalcField.LENGTH -> taperCalcNumberText(result.lengthMm, unit)
                    TaperCalcField.RATE -> result.rate.exactText
                }
                states[derivedField] = states.getValue(derivedField).copy(derivedText = text)
            }
            // The rate's other spellings ride under the rate field whichever value was derived:
            // the per-foot line is inch-drawing shop notation, and the common rate is the name a
            // drawing would carry — said only when it differs from the exact ratio.
            states[TaperCalcField.RATE] = states.getValue(TaperCalcField.RATE)
                .copy(supportingText = rateSupportingText(result.rate, unit))

            // All four typed: a mismatch is information about what was entered, not a rejection.
            // Nothing is rewritten to reconcile the two.
            val message = when (result.typedSlopeAgrees) {
                false -> {
                    states[TaperCalcField.RATE] = states.getValue(TaperCalcField.RATE).copy(isError = true)
                    "The typed rate does not match these three values — they give ${result.rate.exactText}."
                }
                true -> "All four values agree."
                null -> null
            }
            TaperCalcPresentation(
                states,
                message = message,
                messageIsError = result.typedSlopeAgrees == false,
                isSolved = true,
            )
        }
    }
}

/** Which fields a solve-level issue is about — the ones that go red beside the message. */
private fun invalidFields(issue: TaperCalcIssue): List<TaperCalcField> = when (issue) {
    TaperCalcIssue.NON_POSITIVE_LENGTH -> listOf(TaperCalcField.LENGTH)
    TaperCalcIssue.NON_POSITIVE_DIA -> listOf(TaperCalcField.LARGE_DIA, TaperCalcField.SMALL_DIA)
    TaperCalcIssue.NON_POSITIVE_RATE -> listOf(TaperCalcField.RATE)
    TaperCalcIssue.SET_NOT_SMALLER -> listOf(TaperCalcField.LARGE_DIA, TaperCalcField.SMALL_DIA)
    TaperCalcIssue.RATE_CONSUMES_DIA -> listOf(TaperCalcField.SMALL_DIA, TaperCalcField.RATE)
}

private fun issueMessage(issue: TaperCalcIssue): String = when (issue) {
    TaperCalcIssue.NON_POSITIVE_LENGTH -> "Length must be greater than 0."
    TaperCalcIssue.NON_POSITIVE_DIA -> "Both diameters must be greater than 0."
    TaperCalcIssue.NON_POSITIVE_RATE -> "Enter a rate like 1:12, 1/12, or 12."
    TaperCalcIssue.SET_NOT_SMALLER ->
        "The small end must be smaller than the large end — equal ends are a straight shaft, " +
            "which has no taper rate."
    TaperCalcIssue.RATE_CONSUMES_DIA ->
        "That rate over that length removes the whole large end; the small end would be 0 or less."
}

/** `3/4"/ft · ≈ 1:16` on an inch entry; `≈ 1:16` alone on a metric one; `null` with nothing to add. */
internal fun rateSupportingText(rate: TaperCalcRate, unit: UnitSystem): String? {
    val parts = mutableListOf<String>()
    if (unit == UnitSystem.INCHES) parts += "${rate.inchesPerFootText}\"/ft"
    val common = rate.commonText
    if (common != null && common != rate.exactText) parts += "≈ $common (within 3%)"
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
