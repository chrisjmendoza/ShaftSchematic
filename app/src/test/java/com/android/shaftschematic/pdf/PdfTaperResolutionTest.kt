package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.TaperOrientation
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfTaperResolutionTest {

    @Test
    fun pdf_uses_resolved_taper_positions() {
        val spec = ShaftSpec(
            overallLengthMm = 100f,
            tapers = listOf(
                Taper(
                    id = "T1",
                    startFromAftMm = 10f,
                    lengthMm = 20f,
                    startDiaMm = 40f,
                    endDiaMm = 30f,
                    orientation = TaperOrientation.FWD
                )
            )
        )

        val resolved = ResolvedTaper(
            id = "T1",
            authoredSourceId = "T1",
            startMmPhysical = 70f,
            endMmPhysical = 90f,
            startDiaMm = 40f,
            endDiaMm = 30f,
            orientation = TaperOrientation.FWD
        )

        val pdfTapers = buildPdfTapers(spec, listOf(resolved))
        val taper = pdfTapers.single()

        assertEquals(70f, taper.startFromAftMm, 1e-4f)
        assertEquals(20f, taper.lengthMm, 1e-4f)
        assertEquals(40f, taper.startDiaMm, 1e-4f)
        assertEquals(30f, taper.endDiaMm, 1e-4f)
    }
}
