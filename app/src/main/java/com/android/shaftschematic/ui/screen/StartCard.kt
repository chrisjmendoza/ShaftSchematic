package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SheetDateMode
import com.android.shaftschematic.ui.input.shouldCommitOnBlur
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.isLengthText
import com.android.shaftschematic.util.toMmOrNull

/**
 * The editor's start card — ALWAYS the carousel's first page, on the Schematic and the Final tab,
 * at every width. It starts a job: the overall length, the shaft's bare-shaft diameter, and the
 * project information, after which the user builds the shaft out of components (on-device
 * request: the OAL standing on screen above the carousel all the time nagged, when it is a
 * one-and-done measurement like the rest).
 *
 * It is not a component: no resolved id, nothing selects it but a null selection (a new or
 * opened document, a swipe onto it, the toolbar's Project information button), and it carries no
 * remove button, rename editor or unit chip.
 *
 * **Overall Length** commits the way every card field commits — IME Done, a blur after the text
 * changed (`BlurCommitPolicy`), or the card Save bar while it holds an unsaved value — and never
 * per keystroke. Whatever it commits goes to [onSetOverallLengthMm], whose ONE path
 * (`ShaftViewModel.onSetOverallLengthMm` → `withNewOal`) re-anchors FWD-measured parts and says
 * so. An empty or unreadable entry commits nothing: the field restores the stored length and
 * names what it dropped. A length of 0 is "not typed yet" and is never an error; a set length
 * shorter than the parts on it is.
 *
 * **Shaft diameter** is the shaft-wide bare-shaft Ø ([ShaftSpec.autoBodyDiaMm]), typed in the
 * document unit and handed to [onSetShaftDiaMm] verbatim; a blank or zero entry hands 0, which
 * returns the shaft to the derived Ø. The ViewModel keeps every per-section override, so a
 * section given its own Ø on its card stays as typed — the note under the field says where that
 * is done.
 *
 * **Project information** is the document's, shared by both tabs: free text that commits on blur
 * or IME and registers with the card Save bar like the numeric fields, so a value typed and left
 * on screen is never lost to a swipe (the card-change prompt asks first).
 *
 * **Date on sheets** picks the date every printed sheet carries ([SheetDateMode], on-device
 * request: the work is not always done the day the sheet is printed): Today (the caption says what
 * that prints), Custom — a plain-text "Date" field, committed like the fields above and printed
 * exactly as typed — or None, which leaves the date off. The chips commit on tap. Whether a given
 * sheet prints that date is its own per-job switch on its PDF options ([SheetDateOnSheetRow]).
 *
 * The geometry is whichever drawing the hosting editor names; the caller binds [spec] and the two
 * geometry callbacks to that target, never to a "current target" flag.
 */
@Composable
internal fun ShaftStartCard(
    spec: ShaftSpec,
    unit: UnitSystem,
    customer: String,
    vessel: String,
    jobNumber: String,
    item: String,
    sheetDateMode: SheetDateMode,
    sheetDateText: String,
    shaftPosition: ShaftPosition,
    notes: String,
    onSetOverallLengthMm: (Float) -> Unit,
    onSetShaftDiaMm: (Float) -> Unit,
    onSetCustomer: (String) -> Unit,
    onSetVessel: (String) -> Unit,
    onSetJobNumber: (String) -> Unit,
    onSetItem: (String) -> Unit,
    onSetSheetDateMode: (SheetDateMode) -> Unit,
    onSetSheetDateText: (String) -> Unit,
    onSetShaftPosition: (ShaftPosition) -> Unit,
    onSetNotes: (String) -> Unit,
    showOalDebugLabel: Boolean = false,
    outerPaddingHorizontal: Dp = 8.dp,
) {
    Box(Modifier.fillMaxWidth().testTag(START_CARD_TAG)) {
        ComponentCard(title = START_CARD_TITLE, outerPaddingHorizontal = outerPaddingHorizontal) {
            val isOversized = spec.overallLengthMm > 0f &&
                spec.overallLengthMm < lastOccupiedEndMm(spec)

            // The two values are a few characters each, so they share a row at every width
            // (on-device request) — except at a large system font on a narrow card, where even
            // the short labels would not fit half a card and the fields stack as before.
            val fontScale = LocalDensity.current.fontScale
            val twoUp = LocalCardFieldsTwoUp.current
            val shareRow = startCardFieldsShareRow(twoUp, fontScale)
            // Half a phone card holds only the short labels; a tablet's two-up card has room
            // for the full ones.
            val shortLabels = shareRow && !twoUp
            CompositionLocalProvider(LocalCardFieldsTwoUp provides shareRow) {
                FieldPair(
                    first = {
                        CommitNum(
                            label = if (shortLabels) "OAL (${abbr(unit)})" else oalFieldLabel(unit, fontScale),
                            initialDisplay = formatDisplay(spec.overallLengthMm, unit),
                            modifier = Modifier.testTag(OAL_FIELD_TAG),
                            externalIssueText = if (isOversized) OAL_OVERSIZE_NOTICE else null,
                        ) { t -> toMmOrNull(t, unit)?.let(onSetOverallLengthMm) }
                    },
                    second = {
                        CommitNum(
                            label = shaftDiaFieldLabel(unit, shortLabels),
                            // Unset (0) shows an empty box: the derived Ø is not a typed value.
                            initialDisplay = if (spec.autoBodyDiaMm > 0f) formatDisplay(spec.autoBodyDiaMm, unit) else "",
                            modifier = Modifier.testTag(START_CARD_SHAFT_DIA_TAG),
                            // A cleared box is a deliberate clear, not an unreadable entry.
                            parseValid = { raw -> raw.isBlank() || isLengthText(raw) },
                        ) { t ->
                            val mm = if (t.isBlank()) 0f else toMmOrNull(t, unit) ?: return@CommitNum
                            onSetShaftDiaMm(mm)
                        }
                    },
                )
            }

            // Read-only: the dimensioned span, only when an excluded end thread makes it differ
            // from the physical length.
            val win = remember(spec.overallLengthMm, spec.threads, spec.tapers) { computeOalWindow(spec) }
            val effectiveOalWindowMm = win.oalMm
            if (kotlin.math.abs(effectiveOalWindowMm - spec.overallLengthMm.toDouble()) > OAL_EPS_MM) {
                Text(
                    text = "Dimensioned OAL: ${formatDisplay(effectiveOalWindowMm.toFloat(), unit)} ${abbr(unit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showOalDebugLabel) {
                Text(
                    text = "OAL debug • physical=${formatDisplay(spec.overallLengthMm, unit)} ${abbr(unit)} • effective=${formatDisplay(effectiveOalWindowMm.toFloat(), unit)} ${abbr(unit)} • covered=${formatDisplay(lastOccupiedEndMm(spec), unit)} ${abbr(unit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                SHAFT_DIA_SECTIONS_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(START_CARD_DIA_NOTE_TAG),
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Project information", style = MaterialTheme.typography.titleSmall)

            CommitTextField(
                label = "Job Number",
                initialText = jobNumber,
                onCommit = onSetJobNumber,
                modifier = Modifier.testTag(PROJECT_INFO_JOB_NUMBER_TAG),
            )
            CommitTextField(
                label = "Customer",
                initialText = customer,
                onCommit = onSetCustomer,
                modifier = Modifier.testTag(PROJECT_INFO_CUSTOMER_TAG),
            )
            CommitTextField(
                label = "Vessel",
                initialText = vessel,
                onCommit = onSetVessel,
                modifier = Modifier.testTag(PROJECT_INFO_VESSEL_TAG),
            )
            // Optional shaft designation ("Tail shaft", "Line shaft"); blank prints nothing.
            CommitTextField(
                label = "Item (optional)",
                initialText = item,
                onCommit = onSetItem,
                modifier = Modifier.testTag(PROJECT_INFO_ITEM_TAG),
            )
            ShaftPositionDropdown(
                selected = shaftPosition,
                onSelected = { if (it != shaftPosition) onSetShaftPosition(it) },
                modifier = Modifier.fillMaxWidth().testTag(PROJECT_INFO_POSITION_TAG),
            )
            SheetDateSection(
                mode = sheetDateMode,
                text = sheetDateText,
                onSetMode = onSetSheetDateMode,
                onSetText = onSetSheetDateText,
            )
            CommitTextField(
                label = "Notes",
                initialText = notes,
                onCommit = onSetNotes,
                modifier = Modifier.testTag(PROJECT_INFO_NOTES_TAG),
                singleLine = false,
                minHeight = 88.dp,
            )
        }
    }
}

/**
 * The start card's "Date on sheets" choice — the job's ONE date (`ProjectInfo.dateMode` /
 * `dateText`): Today | Custom | None with the typed date under Custom, then the line saying each
 * sheet's PDF options decide whether that sheet prints it ([SheetDateOnSheetRow]).
 */
@Composable
internal fun SheetDateSection(
    mode: SheetDateMode,
    text: String,
    onSetMode: (SheetDateMode) -> Unit,
    onSetText: (String) -> Unit,
) {
    ChipRow(
        label = SHEET_DATE_LABEL,
        options = SheetDateMode.values().toList(),
        selected = mode,
        labelOf = ::sheetDateModeLabel,
        tagOf = ::sheetDateModeTag,
        onSelect = { if (it != mode) onSetMode(it) },
    )
    // Plain text, verbatim: "11/6/2026" prints as typed (golden rule).
    if (mode == SheetDateMode.CUSTOM) CommitTextField(
        label = "Date",
        initialText = text,
        onCommit = onSetText,
        modifier = Modifier.testTag(START_DATE_TEXT_TAG),
    )
    if (mode == SheetDateMode.TODAY) Text(
        SHEET_DATE_TODAY_CAPTION,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Under None no sheet prints a date, so there is nothing for the sheets to choose.
    if (mode != SheetDateMode.NONE) Text(
        SHEET_DATE_SHEETS_NOTE,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(START_DATE_SHEETS_NOTE_TAG),
    )
}

/**
 * Free-text field with the card fields' commit discipline: typing only changes the box; the text
 * reaches [onCommit] on a blur after it changed since focus (`BlurCommitPolicy` — a tap-and-leave
 * commits nothing) or from the card Save bar. While it holds an edit that has not landed it
 * registers with [LocalPendingCardEdits], so the Save bar shows and a card change asks first —
 * a value typed into the last field is never dropped by swiping away. Save commits and releases
 * focus; Cancel puts the settled text back. Both move the focus baseline first, so the blur that
 * follows commits nothing more.
 *
 * A single-line field's IME action is Next: moving on blurs it, which commits.
 */
@Composable
internal fun CommitTextField(
    label: String,
    initialText: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Dp = Dp.Unspecified,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    var textWhenFocused by remember(initialText) { mutableStateOf<String?>(null) }
    // The text a walk-away would leave behind: the incoming value, then whatever a commit or a
    // revert settled on. The caller may normalise what it stores (trimming), so the settled text
    // is tracked here rather than read back, or the field would report itself dirty forever.
    var settledText by remember(initialText) { mutableStateOf(initialText) }
    val isDirty = text != settledText
    val focusManager = LocalFocusManager.current

    fun acceptEdit(): Boolean {
        settledText = text
        textWhenFocused = text
        onCommit(text)
        focusManager.clearFocus()
        return true
    }

    fun cancelEdit() {
        text = settledText
        textWhenFocused = settledText
        focusManager.clearFocus()
    }

    val pendingEdits = LocalPendingCardEdits.current
    val cardTitle = LocalCardTitle.current
    val fieldToken = remember { Any() }
    DisposableEffect(isDirty) {
        pendingEdits?.set(
            fieldToken,
            if (isDirty) PendingCardEdit(cardTitle, label, { acceptEdit() }, { cancelEdit() }) else null,
        )
        onDispose { pendingEdits?.set(fieldToken, null) }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 5,
        keyboardOptions = KeyboardOptions(
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
        ),
        modifier = modifier
            .fillMaxWidth()
            .let { if (minHeight != Dp.Unspecified) it.heightIn(min = minHeight) else it }
            .onFocusChanged { f ->
                if (f.isFocused) {
                    if (textWhenFocused == null) textWhenFocused = text
                } else if (shouldCommitOnBlur(textWhenFocused, text)) {
                    settledText = text
                    textWhenFocused = null
                    onCommit(text)
                } else {
                    textWhenFocused = null
                }
            }
    )
}

/**
 * Whether the start card's Overall Length and Shaft diameter share a row: always on a card that
 * is already two-up, and on a narrow card unless the system font is past
 * [OAL_SHORT_LABEL_FONT_SCALE], where half a card cannot hold even the short labels.
 */
internal fun startCardFieldsShareRow(cardFieldsTwoUp: Boolean, fontScale: Float): Boolean =
    cardFieldsTwoUp || fontScale <= OAL_SHORT_LABEL_FONT_SCALE

/** The shaft diameter field's label; [short] is the half-card form beside the OAL on a phone. */
internal fun shaftDiaFieldLabel(unit: UnitSystem, short: Boolean): String =
    if (short) "Shaft Ø (${abbr(unit)})" else "Shaft diameter (${abbr(unit)})"

/** The "Date on sheets" chips' label — the date every printed sheet carries. */
internal const val SHEET_DATE_LABEL = "Date on sheets"

/** The caption under the chips while Today is chosen. */
internal const val SHEET_DATE_TODAY_CAPTION = "Prints the day the sheet is made."

internal fun sheetDateModeLabel(mode: SheetDateMode): String = when (mode) {
    SheetDateMode.TODAY -> "Today"
    SheetDateMode.CUSTOM -> "Custom"
    SheetDateMode.NONE -> "None"
}

internal fun sheetDateModeTag(mode: SheetDateMode): String = when (mode) {
    SheetDateMode.TODAY -> START_DATE_MODE_TODAY_TAG
    SheetDateMode.CUSTOM -> START_DATE_MODE_CUSTOM_TAG
    SheetDateMode.NONE -> START_DATE_MODE_NONE_TAG
}

/** The start card's title — the card is the job's start: the shaft's size and whose job it is. */
internal const val START_CARD_TITLE = "Shaft & job"

/** The note under the shaft diameter — where a section of bare shaft gets its own Ø. */
internal const val SHAFT_DIA_SECTIONS_NOTE =
    "Sections of bare shaft can be given their own diameter on their Body cards; " +
        "a section set there keeps it."

/** The Overall Length field's notice while parts run past a set length (advisory; it still commits). */
internal const val OAL_OVERSIZE_NOTICE = "Parts run past this length"

internal const val START_CARD_TAG = "start_card"
internal const val START_CARD_SHAFT_DIA_TAG = "start_card_shaft_dia"
internal const val START_CARD_DIA_NOTE_TAG = "start_card_dia_note"
internal const val PROJECT_INFO_JOB_NUMBER_TAG = "project_info_job_number"
internal const val PROJECT_INFO_CUSTOMER_TAG = "project_info_customer"
internal const val PROJECT_INFO_VESSEL_TAG = "project_info_vessel"
internal const val PROJECT_INFO_ITEM_TAG = "project_info_item"
internal const val PROJECT_INFO_POSITION_TAG = "project_info_position"
internal const val PROJECT_INFO_NOTES_TAG = "project_info_notes"
internal const val START_DATE_MODE_TODAY_TAG = "start_date_mode_today"
internal const val START_DATE_MODE_CUSTOM_TAG = "start_date_mode_custom"
internal const val START_DATE_MODE_NONE_TAG = "start_date_mode_none"
internal const val START_DATE_TEXT_TAG = "start_date_text"
internal const val START_DATE_SHEETS_NOTE_TAG = "start_date_sheets_note"

private const val OAL_EPS_MM: Double = 1e-3
