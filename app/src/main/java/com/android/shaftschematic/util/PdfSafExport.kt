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
    var composed = false
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
                    drawPdfErrorPage(page, t)
                    false
                }
                doc.finishPage(page)
                doc.writeTo(out)
            } finally {
                try { out.flush() } catch (_: Throwable) {}
                doc.close()
            }
        } ?: return false
    }.onFailure { return false }
    return composed
}

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
fun createPdfInTree(context: Context, treeUri: Uri, displayName: String): Uri? = runCatching {
    val parentDoc = DocumentsContract.buildDocumentUriUsingTree(
        treeUri, DocumentsContract.getTreeDocumentId(treeUri),
    )
    DocumentsContract.createDocument(
        context.contentResolver, parentDoc, "application/pdf", displayName,
    )
}.getOrNull()
