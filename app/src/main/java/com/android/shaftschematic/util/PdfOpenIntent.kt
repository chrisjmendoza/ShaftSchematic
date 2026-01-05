package com.android.shaftschematic.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Builds an Intent for viewing a PDF URI.
 *
 * Kept as a small helper so we can unit/instrumentation-test the exact flags and
 * ClipData required by common PDF viewers.
 */
internal fun buildOpenPdfIntent(context: Context, uri: Uri): Intent {
    return Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .apply {
            // Some viewers require ClipData to honor URI grant flags.
            clipData = ClipData.newUri(context.contentResolver, "Exported PDF", uri)
        }
}
