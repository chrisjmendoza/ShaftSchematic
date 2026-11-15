package com.android.shaftschematic.util

/**
 * US Letter landscape, in PDF points — the default page every document prints on
 * ([PdfPageOrientation.LANDSCAPE]'s size).
 */
const val PDF_PAGE_WIDTH_PT = 792
const val PDF_PAGE_HEIGHT_PT = 612

/**
 * The US Letter page a document is composed on, in PDF points. [LANDSCAPE] is the default for
 * every document; the undercut drawing chooses [PORTRAIT] when it stacks three or more sections
 * (`resolveUndercutPageOrientation`). The composers read the size off `page.info`, so the shape
 * is decided once, by whoever starts the page.
 */
enum class PdfPageOrientation(val widthPt: Int, val heightPt: Int) {
    LANDSCAPE(PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT),
    PORTRAIT(PDF_PAGE_HEIGHT_PT, PDF_PAGE_WIDTH_PT),
}
