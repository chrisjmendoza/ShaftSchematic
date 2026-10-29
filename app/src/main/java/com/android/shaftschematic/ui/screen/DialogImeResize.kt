// file: app/src/main/java/com/android/shaftschematic/ui/screen/DialogImeResize.kt
package com.android.shaftschematic.ui.screen

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.doOnLayout

/**
 * An [AlertDialog] that keeps its whole box ABOVE the keyboard — every field reachable by
 * scrolling and the buttons always in view — and whose scroll position survives typing.
 *
 * A Compose dialog window leaves its soft-input mode unspecified, and with no platform
 * ScrollView in the hierarchy the system pans: the window slides up only far enough to show
 * the cursor, so the fields below the focused one and the confirm buttons stay under the
 * keyboard and a `verticalScroll` column cannot reach them (on-device report: the keyway
 * calculator's cutter fields). Three pieces fix that, and each one is load-bearing:
 *
 *  • **The keyboard's own inset lifts the box.** The dialog's root view is padded at the bottom
 *    by the IME inset this window receives ([WindowInsets.ime]), so the platform's centering
 *    places the box in the space above the keyboard and the box's scroll column gets a viewport
 *    it can scroll the rest into. The inset is what the keyboard ACTUALLY covers of this
 *    window: zero when the system has already shrunk the window for the keyboard, the full
 *    overlap when it has not. `SOFT_INPUT_ADJUST_RESIZE` alone is not enough — on API 36 the
 *    dialog opened first, or right after a Save, often kept its full-height layout while the
 *    keyboard inset was delivered on top of it, so Save sat under the keyboard and a tap aimed
 *    at it typed into the IME instead (emulator QA: a runout reading lost that way).
 *  • **`SOFT_INPUT_ADJUST_RESIZE` stays set** so the system never pans the window, and so the
 *    API levels whose resize is reliable keep doing it (the inset is then zero and the padding
 *    adds nothing).
 *  • **A fixed `MATCH_PARENT` window.** A `WRAP_CONTENT` dialog window is re-measured by the
 *    platform at the FULL DISPLAY height before every re-layout — every keystroke — and for that
 *    one pass the scroll column's viewport is taller than its content, so `ScrollState` clamps
 *    the scroll to 0 and the focused field drops back under the keyboard (on-device report: "the
 *    whole dialog keeps moving down" with each key). The window is switched to `MATCH_PARENT`
 *    once the platform has chosen the dialog's width, and that width is pinned on the content,
 *    so the dialog box looks exactly as a plain [AlertDialog] does, centered in the window.
 *
 * Every dialog that takes typed input goes through here — the Add-component dialogs, the
 * calculators, the runout reading editor, the worn-section editor. Content taller than the
 * room above the keyboard must sit in a `verticalScroll` column in [text]. Only the
 * parameters its callers use are exposed; add others as a caller needs them.
 */
@Composable
internal fun ImeResizingAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    var pinnedWidth by remember { mutableStateOf<Dp?>(null) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.then(pinnedWidth?.let { Modifier.width(it) } ?: Modifier),
        title = title,
        text = {
            DialogImeResize(onWidthPinned = { pinnedWidth = it })
            text()
        },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}

/**
 * The window-level half of [ImeResizingAlertDialog]: sets the soft-input mode at once, keeps
 * the dialog root's bottom padding equal to the keyboard inset on every composition the inset
 * changes, and, after the first layout has settled the platform width, reports that width and
 * switches the window to `MATCH_PARENT`. Call from inside the dialog's content; a no-op
 * anywhere else.
 */
@Composable
internal fun DialogImeResize(onWidthPinned: (Dp) -> Unit = {}) {
    val view = LocalView.current
    val density = LocalDensity.current
    // Read in composition so every inset change — the keyboard opening, closing, or animating —
    // recomposes this node and re-applies the padding.
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    SideEffect {
        // The dialog's root lays its one child out centered inside its padding, so a bottom
        // padding of the covered height centers the box in the room above the keyboard.
        val root = view.parent as? View
        if (root is DialogWindowProvider && root.paddingBottom != imeBottomPx) {
            root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, imeBottomPx)
        }
    }
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            // The dialog's Compose root is the dialog box itself; its laid-out width is the
            // platform's choice (the wrap-content window settles it on the first traversal).
            view.doOnLayout {
                if (it.width > 0) {
                    onWidthPinned(with(density) { it.width.toDp() })
                    window.setLayout(MATCH_PARENT, MATCH_PARENT)
                }
            }
        }
        onDispose { }
    }
}
