package com.android.shaftschematic.ui.screen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A card field holding a typed value that has not landed, with the two answers its ✓ / ✗ pair
 * gives: [accept] commits it (the ✓), [cancel] puts the stored value back (the ✗).
 */
internal class PendingCardEdit(
    val cardTitle: String,
    val fieldLabel: String,
    val accept: () -> Unit,
    val cancel: () -> Unit,
)

/**
 * The editor's record of which card field holds an unsaved edit, so the Overall Length field can
 * ask about it before taking focus (on-device request): a tap on the OAL would otherwise blur
 * the card field and commit its value without a word, with the ✓ / ✗ beside it never answered.
 *
 * Fields register themselves ([CommitNum], [CommitDesignationField]) through
 * [LocalPendingCardEdits], keyed by an identity token each field instance owns, and drop the
 * entry when they settle or leave composition. View state only: never written to the document,
 * `EditState`, or undo history.
 */
@Stable
internal class PendingCardEdits {
    private val byField = mutableStateMapOf<Any, PendingCardEdit>()

    /** The pending edit, or null when every card field is settled. A blur commits, so at most
     *  one field is normally dirty at a time. */
    val current: PendingCardEdit? get() = byField.values.firstOrNull()

    fun set(token: Any, edit: PendingCardEdit?) {
        if (edit != null) byField[token] = edit else byField.remove(token)
    }
}

/** Null outside the editor screen: a card field there reports to nobody. */
internal val LocalPendingCardEdits = staticCompositionLocalOf<PendingCardEdits?> { null }

/** The title of the [ComponentCard] a field sits on, for the OAL's "keep your change?" prompt. */
internal val LocalCardTitle = staticCompositionLocalOf { "" }
