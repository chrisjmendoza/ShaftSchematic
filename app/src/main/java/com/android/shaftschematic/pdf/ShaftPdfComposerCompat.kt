// Package must match your existing imports
package com.android.shaftschematic.pdf

import android.content.Context
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import java.io.OutputStream

/**
 * Backwards‑compat shim so existing imports and the legacy call in ShaftRoute keep working.
 * Default page is **Letter, Landscape (792×612 pt)**. A future API allows portrait.
 */
object ShaftPdfComposer {
    enum class PdfOrientation { LANDSCAPE, PORTRAIT }

    // --- Legacy entry point used by ShaftRoute ---
    @JvmStatic
    fun exportToStream(
        context: Context,
        spec: ShaftSpec,
        unit: UnitSystem,
        showGrid: Boolean, // ignored; PDF grid is off by contract
        out: OutputStream,
        title: String? = null,
    ) {
        exportToStreamOptions(
            context = context,
            spec = spec,
            unit = unit,
            showGrid = showGrid,
            out = out,
            title = title,
            orientation = PdfOrientation.LANDSCAPE, // lock landscape by default
            pageWidthPt = 612,  // Letter portrait width
            pageHeightPt = 792, // Letter portrait height
        )
    }

    // --- Future‑ready API with orientation & size ---
    @JvmStatic
    fun exportToStreamOptions(
        context: Context,
        spec: ShaftSpec,
        unit: UnitSystem,
        showGrid: Boolean, // ignored
        out: OutputStream,
        title: String? = null,
        orientation: PdfOrientation = PdfOrientation.LANDSCAPE,
        pageWidthPt: Int = 612,   // default Letter portrait W
        pageHeightPt: Int = 792,  // default Letter portrait H
    ) {
        val appVersion = try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            (pi?.versionName ?: "").ifBlank { "" }
        } catch (_: Throwable) { "" }
        val filename = (title ?: "shaft_drawing").ifBlank { "shaft_drawing" }

        val (w, h) = when (orientation) {
            PdfOrientation.LANDSCAPE -> pageHeightPt to pageWidthPt // 792×612 for Letter
            PdfOrientation.PORTRAIT -> pageWidthPt to pageHeightPt   // 612×792 for Letter
        }

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(w, h, 1).create()
        val page = doc.startPage(pageInfo)

        // Delegate to the composer (v6 drawing: dim style + info near shaft)
        composeShaftPdf(
            page = page,
            spec = spec,
            unit = unit,
            project = ProjectInfo(), // thread actual project later if desired
            appVersion = appVersion,
            filename = filename,
        )

        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }
}

/*
Contract note (paste into §8 PDF Export Rules):
- Page orientation: **Landscape (Letter, 792×612 pt)** by default. Portrait support will be exposed via settings/API in a future release. The drawing and dimension system are orientation‑agnostic and scale to the page content rect.
*/
