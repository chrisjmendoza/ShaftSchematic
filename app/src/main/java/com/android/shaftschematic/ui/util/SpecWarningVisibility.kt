package com.android.shaftschematic.ui.util

/**
 * Pure show/dismiss logic for the Schematic tab's spec-level warning banner
 * ([specWarningMessages]). Dismissal is keyed to the exact warning message set so a changed set
 * (a new or different warning) re-shows the banner even while an unchanged set stays hidden. This
 * is a view-state helper only -- the dismissed key itself lives in Compose `rememberSaveable` at
 * the call site, never in the document, `EditState`, or undo history.
 */

// Pipe cannot appear in a warning string (plain English advisories), so it is a safe,
// collision-free join delimiter for the dismissed-set key below.
private const val WARNING_KEY_SEPARATOR = "|"

/** Order-preserving join key for a warning message list, used to detect a changed warning set. */
fun warningSetKey(messages: List<String>): String = messages.joinToString(WARNING_KEY_SEPARATOR)

/**
 * True when the spec-level warning banner should be visible: there is at least one message and
 * its set differs from [dismissedKey] (null when nothing has been dismissed this session).
 */
fun bannerVisible(messages: List<String>, dismissedKey: String?): Boolean =
    messages.isNotEmpty() && warningSetKey(messages) != dismissedKey
