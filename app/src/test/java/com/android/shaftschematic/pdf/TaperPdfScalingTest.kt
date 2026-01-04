package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.maxOuterDiaMm
import org.junit.Assert.assertTrue
import org.junit.Test

class TaperPdfScalingTest {

    @Test
    fun `taper-only scale fits height and stays finite`() {
        // Detail specs should prefer a stable target schematic height (~1.25 in).
        val spec = ShaftSpec(
            overallLengthMm = 50f,
            tapers = listOf(
                Taper(
                    startFromAftMm = 0f,
                    lengthMm = 50f,
                    startDiaMm = 120f,
                    endDiaMm = 100f
                )
            )
        )

        val geomWidthPt = 720f
        val geomHeightPt = 240f

        val ptPerMm = computeDetailPtPerMm(spec, geomWidthPt, geomHeightPt)
        assertTrue(ptPerMm.isFinite())

        val renderedHeightPt = spec.maxOuterDiaMm() * ptPerMm
        val targetHeightPt = 1.25f * 72f
        assertTrue(
            "Rendered height must not exceed targetHeightPt (got=$renderedHeightPt, target=$targetHeightPt)",
            renderedHeightPt <= targetHeightPt + 0.01f
        )
        assertTrue(
            "Rendered height must fit within geomHeightPt (got=$renderedHeightPt, max=$geomHeightPt)",
            renderedHeightPt <= geomHeightPt + 0.01f
        )
    }
}
