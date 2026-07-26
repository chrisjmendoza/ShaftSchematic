package com.android.shaftschematic.util

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.FileOutputStream

/**
 * Direct-print support for the app's single-page PDF documents (schematic, runout sheet,
 * wear record) via the Android print framework — no export-to-file step needed.
 *
 * The page is composed with the exact same composer call the PDF export path uses, at the
 * same fixed US Letter landscape size (792 × 612 pt), so a print and an export are always
 * pixel-identical. The print framework handles printer selection, scaling, and save-to-PDF.
 */
fun printShaftPdfPage(
    context: Context,
    jobName: String,
    composePage: (PdfDocument.Page) -> Unit,
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager

    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("$jobName.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, newAttributes != oldAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            val doc = PdfDocument()
            try {
                // US Letter landscape (pt) — same page box as every export path.
                val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
                val page = doc.startPage(pageInfo)
                try {
                    composePage(page)
                } finally {
                    doc.finishPage(page)
                }
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (t: Throwable) {
                VerboseLog.e(VerboseLog.Category.PDF, "PdfPrint") {
                    "print write failed: ${t.javaClass.simpleName}: ${t.message}"
                }
                callback.onWriteFailed(t.message)
            } finally {
                doc.close()
            }
        }
    }

    val attributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.NA_LETTER.asLandscape())
        .build()

    printManager.print(jobName, adapter, attributes)
}
