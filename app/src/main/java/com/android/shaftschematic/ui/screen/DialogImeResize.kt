// file: app/src/main/java/com/android/shaftschematic/ui/screen/DialogImeResize.kt
package com.android.shaftschematic.ui.screen

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
 * An [AlertDialog] whose window RESIZES above the keyboard instead of panning, and whose
 * scroll position survives typing.
 *
 * A Compose dialog window leaves its soft-input mode unspecified, and with no platform
 * ScrollView in the hierarchy the system pans: the window slides up only far enough to show
 * the cursor, so the fields below the focused one and the confirm buttons stay under the
 * keyboard and a `verticalScroll` column cannot reach them (on-device report: the keyway
 * calculator's cutter fields). `SOFT_INPUT_ADJUST_RESIZE` shrinks the window to the space
 * above the keyboard, which hands the scroll column a viewport it can scroll the rest into.
 *
 * Resizing alone is not enough. A `WRAP_CONTENT` dialog window is re-measured by the platform
 * at the FULL DISPLAY height before every re-layout — every keystroke — and for that one pass
 * the scroll column's viewport is taller than its content, so `ScrollState` clamps the scroll
 * to 0 and the focused field drops back under the keyboard (on-device report: "the whole dialog
 * keeps moving down" with each key). The window is therefore switched to a fixed
 * `MATCH_PARENT` size (the platform never pre-measures a fixed window at another size) once the
 * platform has chosen the dialog's width, and that width is pinned on the content so the
 * dialog looks exactly as it did: the window fills the area above the keyboard and the
 * dialog box sits centered in it, as before.
 *
 * Only the parameters the calculators use are exposed; add others as a caller needs them.
 */
@Composable
internal fun ImeResizingAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
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
 * The window-level half of [ImeResizingAlertDialog]: sets the soft-input mode at once and,
 * after the first layout has settled the platform width, reports that width and switches the
 * window to `MATCH_PARENT`. Call from inside the dialog's content; a no-op anywhere else.
 */
@Composable
internal fun DialogImeResize(onWidthPinned: (Dp) -> Unit = {}) {
    val view = LocalView.current
    val density = LocalDensity.current
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
