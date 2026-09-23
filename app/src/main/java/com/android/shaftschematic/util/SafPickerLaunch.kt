package com.android.shaftschematic.util

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultLauncher

/**
 * Launches a Storage Access Framework picker — "save to…", "open…", "pick a folder" — without
 * letting a missing picker take the app down.
 *
 * `ActivityResultLauncher.launch` ends in `startActivityForResult`, so it throws
 * [ActivityNotFoundException] when nothing on the device handles the intent. On a normal phone
 * DocumentsUI always does; on the hardware this app is heading for it is not guaranteed —
 * enterprise-locked and stripped rugged tablets ship without it or with it disabled, and the
 * result there is that every export and backup button kills the app rather than saying it cannot
 * open a picker. Every `launch` call in the app goes through here for that reason.
 *
 * [what] is a **fixed label chosen at the call site** ("backup", "export", …), never a filename:
 * the picker inputs carry customer, vessel and job text, and [AppLog] records events, never
 * document content. Sites with a snackbar pass [onUnavailable] to say so on screen; the rest at
 * least leave the breadcrumb, which is the difference between a silent button and a mystery.
 */
internal fun <I> ActivityResultLauncher<I>.launchPicker(
    input: I,
    what: String,
    onUnavailable: () -> Unit = {},
) {
    try {
        launch(input)
    } catch (t: ActivityNotFoundException) {
        AppLog.e("Saf", "no document picker available for $what", t)
        onUnavailable()
    }
}

/** The message every surface shows when the device has no picker; one wording, one place. */
internal const val NO_PICKER_MESSAGE: String =
    "This device has no file picker, so there is nowhere to save to."
