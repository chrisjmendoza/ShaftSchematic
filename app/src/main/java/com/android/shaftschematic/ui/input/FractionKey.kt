package com.android.shaftschematic.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * File: FractionKey.kt
 * Layer: UI → Input
 *
 * The in-field fraction key: number fields ask for the keyboard's number pad, and a number pad
 * has digits, `.`, `-` and usually a space, but no fraction bar (Gboard's has none). The key types
 * the bar into the focused field, so `10 1/4` and `10-1/4` can be entered on any keyboard.
 *
 * Its face is a small stacked fraction — a grey "n" over a short bar over a grey "d" — because a
 * bare "/" in a field's trailing slot reads as a divider or a clear button, not as "fraction"
 * (on-device report). The glyph is built from text and a 1 dp bar, never a Unicode vulgar
 * fraction character, and it is not a drawn sheet fraction (`FractionText` does not apply).
 * See `docs/contracts/NumberField.md` §"Keyboard".
 */

/**
 * Whether fraction-capable number fields show the [FractionKey] while focused. The app root
 * provides `true` for an inch document and `false` for a millimetre one, where nobody types
 * fractions; the default (`true`) is what a field outside that provider — a test — gets.
 */
val LocalFractionKeyEnabled = compositionLocalOf { true }

/** testTag of every [FractionKey]. */
const val FRACTION_KEY_TAG = "fraction_key"

/**
 * [this] with its selection replaced by [insert] and the cursor placed after it — what typing
 * [insert] on the keyboard would leave.
 */
fun TextFieldValue.insertAtCursor(insert: String): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    val newText = text.substring(0, start) + insert + text.substring(end)
    return TextFieldValue(newText, TextRange(start + insert.length))
}

/** Accessibility label of every [FractionKey]; the glyph itself is hidden from semantics. */
const val FRACTION_KEY_DESCRIPTION = "Fraction bar — types /"

/**
 * The fraction key, sized for a text field's trailing slot: a stacked n-over-d glyph in the
 * field labels' grey (`onSurfaceVariant`) that types "/" at the cursor. The button can never take
 * focus — a touch-mode click never moves focus, and `canFocus = false` keeps a hardware-keyboard
 * or D-pad press from pulling it out of the field too — so the keyboard stays up and typing
 * carries on after the bar.
 */
@Composable
fun FractionKey(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .focusProperties { canFocus = false }
            .testTag(FRACTION_KEY_TAG)
            .semantics { contentDescription = FRACTION_KEY_DESCRIPTION },
    ) {
        StackedFractionGlyph()
    }
}

/**
 * "n" over a 1 dp bar over "d". The column takes its intrinsic width, so the bar spans exactly
 * the wider letter; at 11 sp the stack is under 30 dp tall and sits centred in the button's
 * 48 dp slot.
 */
@Composable
private fun StackedFractionGlyph() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("n", color = color, style = style)
        Box(
            Modifier
                .padding(vertical = 1.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(color),
        )
        Text("d", color = color, style = style)
    }
}

/**
 * The cursor bridge for a field whose state is a plain `String`: the [FractionKey] has to insert
 * at the cursor, and a `String` field forgets where its cursor was — replacing its text from
 * outside leaves the cursor before the inserted bar, so the next digit lands in front of it.
 * The field shows [valueFor] and reports every edit to [track]; [focused] gates the key.
 */
@Stable
class FieldCursor {
    private var last by mutableStateOf(TextFieldValue(""))

    /** Whether the field holds focus — the key is shown only then. */
    var focused by mutableStateOf(false)

    /**
     * The box value for [text]: the last edit (cursor and IME composition intact) while it still
     * holds [text], else [text] with the cursor at its end — the text moved under the field (a
     * revert, a load).
     */
    fun valueFor(text: String): TextFieldValue =
        if (last.text == text) last else TextFieldValue(text, TextRange(text.length))

    /** Records an edit the field reported. */
    fun track(value: TextFieldValue) {
        last = value
    }

    /** [text] with a fraction bar typed at the cursor; also recorded, as an edit would be. */
    fun insertBar(text: String): TextFieldValue = valueFor(text).insertAtCursor("/").also(::track)
}

@Composable
fun rememberFieldCursor(): FieldCursor = remember { FieldCursor() }
