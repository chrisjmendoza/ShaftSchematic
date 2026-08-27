package com.android.shaftschematic.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.DocumentsContract

/**
 * PdfSafExport — the ONE hardened SAF write path for every PDF export surface
 * (schematic, runout, wear, undercut, consolidated output, batch export).
 *
 * Hardening rule: a composer throw must never leave a truncated/unopenable file behind.
 * The page is repainted as a valid error page (white background, "PDF export failed" +
 * the exception line) and still finished/written, so the output always opens and shows
 * what failed. Callers get `false` back to skip success-only follow-ups (achievements,
 * auto-open).
 *
 * That recovery is also why every branch here leaves a breadcrumb: the user is shown a page
 * saying the export failed, and the throwable behind it would otherwise be swallowed whole. It
 * goes to [AppLog] (evidence a tester can mail back) and to [CrashReporter] as a non-fatal.
 */

/** US Letter landscape, in PDF points — the app's single page format. */
const val PDF_PAGE_WIDTH_PT = 792
const val PDF_PAGE_HEIGHT_PT = 612

/**
 * Compose one landscape-Letter page via [composePage] and write the document to [uri].
 *
 * @return `true` when [composePage] succeeded and the real document was written; `false`
 *   when the error page was written in its place, or the stream/write itself failed.
 */
fun writeShaftPdfToUri(
    context: Context,
    uri: Uri,
    composePage: (PdfDocument.Page) -> Unit,
): Boolean {
    // Breadcrumbs name the document only by its last path segment: enough to line an export up
    // with what the user was doing, without carrying the rest of a provider URI into a shared log.
    val label = uri.lastPathSegment ?: "(unnamed)"
    var composed = false
    AppLog.i(PDF_EXPORT_TAG, "export start $label")
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            val doc = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT, 1).create()
                val page = doc.startPage(pageInfo)
                composed = try {
                    composePage(page)
                    true
                } catch (t: Throwable) {
                    // The error page is all the user sees; without these two lines the throw that
                    // caused it leaves no trace anywhere.
                    AppLog.e(PDF_EXPORT_TAG, "composer threw, error page written for $label", t)
                    CrashReporter.recordNonFatal(t)
                    drawPdfErrorPage(page, t)
                    false
                }
                doc.finishPage(page)
                doc.writeTo(out)
            } finally {
                try { out.flush() } catch (_: Throwable) {}
                doc.close()
            }
        } ?: run {
            AppLog.e(PDF_EXPORT_TAG, "export failed, no output stream for $label")
            return false
        }
    }.onFailure { t ->
        AppLog.e(PDF_EXPORT_TAG, "export write failed for $label", t)
        return false
    }
    if (composed) AppLog.i(PDF_EXPORT_TAG, "export ok $label")
    return composed
}

private const val PDF_EXPORT_TAG = "PdfExport"

/** Paint the never-truncated error page: the file opens and says what failed. */
fun drawPdfErrorPage(page: PdfDocument.Page, t: Throwable) {
    val canvas = page.canvas
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 14f
    }
    canvas.drawText("PDF export failed", 48f, 96f, paint)
    canvas.drawText("${t.javaClass.simpleName}: ${t.message ?: "(no message)"}", 48f, 120f, paint)
}

/**
 * Create a new PDF document inside a SAF tree (the folder picked via
 * `ActivityResultContracts.OpenDocumentTree`) — the batch "Export all" path. The provider
 * may uniquify [displayName] (e.g. append "(1)") when a file already exists; the returned
 * uri is the actual document. Null when creation fails.
 */
fun createPdfInTree(context: Context, treeUri: Uri, displayName: String): Uri? {
    val created = runCatching {
        val parentDoc = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri),
        )
        DocumentsContract.createDocument(
            context.contentResolver, parentDoc, "application/pdf", displayName,
        )
    }.onFailure { t ->
        AppLog.e(PDF_EXPORT_TAG, "could not create $displayName in the picked folder", t)
        return null
    }.getOrNull()

    if (created == null) AppLog.e(PDF_EXPORT_TAG, "the folder refused to create $displayName")
    return created
}
