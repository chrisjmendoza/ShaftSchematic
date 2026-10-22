// file: app/src/main/java/com/android/shaftschematic/ui/screen/DialogImeResize.kt
package com.android.shaftschematic.ui.screen

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Makes the hosting dialog window RESIZE above the keyboard instead of panning.
 *
 * A Compose dialog window leaves its soft-input mode unspecified, and with no platform
 * ScrollView in the hierarchy the system pans: the window slides up only far enough to show
 * the cursor, so the fields below the focused one and the confirm buttons stay under the
 * keyboard and a `verticalScroll` column cannot reach them (on-device report: the keyway
 * calculator's cutter fields). Resizing shrinks the centered dialog to the space above the
 * keyboard, which hands the scroll column a viewport it can scroll the rest into.
 *
 * Call from inside the dialog's content (any slot of an `AlertDialog`): it finds the window
 * through the dialog's [DialogWindowProvider] and is a no-op anywhere else.
 */
@Composable
internal fun DialogImeResize() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { }
    }
}
