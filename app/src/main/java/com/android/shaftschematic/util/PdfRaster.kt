package com.android.shaftschematic.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * PdfRaster — the ONE compose→raster path behind every in-app PDF preview (schematic,
 * runout, wear, undercut, consolidated output), the sibling of the one hardened SAF write
 * path in [PdfSafExport.kt][writeShaftPdfToUri] and the same `composePage` shape.
 *
 * Every preview shows the REAL composed PDF rasterized — there is no separate on-screen
 * draw path to keep in sync with the composers.
 */

/**
 * Preview raster resolution multiplier over the PDF's point size — 2× so dimension text
 * stays legible on high-density displays.
 */
const val PDF_PREVIEW_RENDER_SCALE = 2

/**
 * Compose one landscape-Letter page via [composePage], rasterize it to a [Bitmap] at
 * [renderScale], and drop the temp file.
 *
 * Must be called off the main thread (use `Dispatchers.IO`). Returns null on any failure —
 * compose, write, or render — so the caller can show an error message instead of crashing.
 * The temp file is created with `createTempFile` so concurrent preview renders never
 * collide on one path.
 *
 * [renderScale] defaults to [PDF_PREVIEW_RENDER_SCALE]. A preview that is being tuned live
 * passes 1 for its drag frames — a quarter of the pixels keeps the page reshaping under the
 * finger — and the pass after the release goes back to the default sharp raster.
 */
fun renderPdfPageBitmap(
    context: Context,
    renderScale: Int = PDF_PREVIEW_RENDER_SCALE,
    composePage: (PdfDocument.Page) -> Unit,
): Bitmap? = runCatching {
    val rasterScale = renderScale.coerceAtLeast(1)
    // Step 1 – compose the page into a temp PDF in the cache dir.
    val tempFile = File.createTempFile("pdf_preview_", ".pdf", context.cacheDir)
    val doc = PdfDocument()
    try {
        val pageInfo = PdfDocument.PageInfo.Builder(
            PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT, 1,
        ).create()
        val page = doc.startPage(pageInfo)
        composePage(page)
        doc.finishPage(page)
        tempFile.outputStream().buffered().use { doc.writeTo(it) }
    } finally {
        doc.close()
    }

    // Step 2 – rasterize page 0 via PdfRenderer onto a white-backed ARGB bitmap.
    val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(pfd)
    try {
        val pdfPage = renderer.openPage(0)
        try {
            val bmp = Bitmap.createBitmap(
                pdfPage.width * rasterScale,
                pdfPage.height * rasterScale,
                Bitmap.Config.ARGB_8888,
            )
            bmp.eraseColor(Color.WHITE)
            pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } finally {
            pdfPage.close()
        }
    } finally {
        renderer.close()
        pfd.close()
        tempFile.delete()
    }
}.getOrNull()
