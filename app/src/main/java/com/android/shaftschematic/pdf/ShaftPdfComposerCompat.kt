// File: app/src/main/java/com/android/shaftschematic/pdf/ShaftPdfComposerCompat.kt
@file:Suppress("unused")

package com.android.shaftschematic.pdf

import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.settings.PdfPrefs

/**
 * Minimal, unambiguous wrapper for PDF export.
 * Calls [composeShaftPdf] directly from the same package.
 * This avoids overload collisions and synthetic Kt references.
 */
fun exportShaftPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    unit: UnitSystem,
    appVersion: String,
    filename: String,
    project: ProjectInfo = ProjectInfo(),
    pdfPrefs: com.android.shaftschematic.settings.PdfPrefs
) {
    // Since this file shares the same package, the top-level composeShaftPdf() is visible directly.
    composeShaftPdf(page, spec, unit, project, appVersion, filename, pdfPrefs)
}
