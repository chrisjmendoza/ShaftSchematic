package com.android.shaftschematic.ui.input

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget

/**
 * File: FocusParking.kt
 * Layer: UI → Input
 *
 * The app's root focus rule: when focus is let go, it parks on the screen itself, never on the
 * first text field. See `docs/contracts/NumberField.md` §"Focus release".
 */

/**
 * Wraps the window's content in a focus target that catches the platform's refocus, so
 * `FocusManager.clearFocus()` — and every dialog dismissal that follows one — ends with NO text
 * field focused.
 *
 * Out of touch mode (a hardware or Bluetooth keyboard, or any key event the IME sends as a raw
 * key), Android's `View.clearFocus()` immediately asks the window's root to take focus again;
 * the Compose view answers with an "enter" focus search that hands focus to the top-left
 * focusable composable. On the editor that is the Overall Length field, so cancelling the
 * auto-body "Apply diameter to…" dialog left the OAL focused with the keyboard up, the next
 * digits typed landed in the shaft length, and a blur would have committed them (emulator QA).
 * A focusable box around everything is the enter search's first candidate, so the refocus
 * lands here instead: focused, but no field, no cursor and no keyboard. In touch mode the
 * platform does not refocus at all and this box is never focused.
 *
 * Layout-neutral: the box passes its constraints straight to [content].
 */
@Composable
fun FocusParkingRoot(content: @Composable () -> Unit) {
    Box(Modifier.focusTarget(), propagateMinConstraints = true) { content() }
}
