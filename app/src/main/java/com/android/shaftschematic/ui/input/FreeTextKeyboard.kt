package com.android.shaftschematic.ui.input

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

/*
 * Keyboards for free-text fields. Each is a keyboard hint only — the field stores whatever is
 * typed, and a letter the user un-capitalises stays lower case. Number fields, calculator
 * expressions and search boxes use neither.
 */

/**
 * A name (customer, vessel, component, file): every word starts with a capital — "Acme Boating".
 * A name field also raises shop codes whole ("bc" → "BC") through [raiseKnownCodes].
 */
fun nameKeyboard(imeAction: ImeAction = ImeAction.Default): KeyboardOptions =
    KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = imeAction)

/** A code whose letters are always capitals (a thread size, "1 1/4 NC", "M30"): every letter. */
fun allCapsKeyboard(imeAction: ImeAction = ImeAction.Default): KeyboardOptions =
    KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = imeAction)

/** Notes, written like a paragraph: only the first word of each sentence starts with a capital. */
fun notesKeyboard(imeAction: ImeAction = ImeAction.Default): KeyboardOptions =
    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction)
