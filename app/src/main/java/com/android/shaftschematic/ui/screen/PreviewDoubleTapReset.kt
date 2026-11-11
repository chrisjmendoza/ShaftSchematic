package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Double-tap on a PDF preview page puts its zoom and pan back to the fitted page — ONE gesture
 * behind both preview hosts, the schematic `PdfPreviewScreen` and the shared [PdfPreviewOverlay].
 *
 * The detector is created once and reads [onReset] through [rememberUpdatedState]: a preview
 * recomposes on every zoom frame, and a detector re-keyed on a fresh lambda would restart between
 * the two taps and never see a double. Both hosts chain it right after the page's pinch/pan
 * handler, which takes its first down whether or not it was consumed, so a pinch still zooms; a
 * pinch or pan consumes the moves, which cancels the tap. A single tap does nothing.
 */
@Composable
internal fun rememberPreviewDoubleTapReset(onReset: () -> Unit): Modifier {
    val reset by rememberUpdatedState(onReset)
    return remember {
        Modifier.pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { reset() })
        }
    }
}
