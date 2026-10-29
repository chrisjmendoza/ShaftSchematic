package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.ui.util.SpecIssue
import com.android.shaftschematic.ui.util.SpecIssueKind
import com.android.shaftschematic.ui.util.specIssues
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [previewIssueMarks] — the editor preview's red marks are built from the banner's own
 * `specIssues`, so the drawing marks exactly what the banner names.
 */
class PreviewIssueMarksTest {

    @Test
    fun `an overlap marks the shared stretch and an overhang marks past the end`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 800f, lengthMm = 300f, diaMm = 50f)),
            tapers = listOf(Taper(startFromAftMm = 100f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 60f)),
            liners = listOf(Liner(startFromAftMm = 250f, lengthMm = 200f, odMm = 90f)),
        )

        val marks = previewIssueMarks(specIssues(spec, UnitSystem.MILLIMETERS))

        assertEquals(
            listOf(
                PreviewIssueMark(250f, 300f, PreviewIssueMarkKind.OVERLAP, null),
                PreviewIssueMark(1000f, 1100f, PreviewIssueMarkKind.OUT_OF_BOUNDS, 1000f),
            ),
            marks,
        )
        assertEquals(250f to 1100f, marks.axialExtentMm())
    }

    @Test
    fun `a part before the AFT end marks its stretch below zero`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(Liner(startFromAftMm = -40f, lengthMm = 200f, odMm = 90f)),
        )
        val mark = previewIssueMarks(specIssues(spec, UnitSystem.MILLIMETERS)).single()
        assertEquals(PreviewIssueMark(-40f, 0f, PreviewIssueMarkKind.OUT_OF_BOUNDS, 0f), mark)
    }

    @Test
    fun `tiny segments and empty stretches mark nothing`() {
        val issues = listOf(
            SpecIssue(SpecIssueKind.TINY_SEGMENT, listOf("b"), "Body #1 is shorter than 1 mm", 10f, 10f),
            SpecIssue(SpecIssueKind.OVERLAP, listOf("a", "b"), "x", 50f, 50f),
        )
        assertTrue(previewIssueMarks(issues).isEmpty())
    }

    @Test
    fun `a clean spec marks nothing, so every other canvas stays as it was`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(Liner(startFromAftMm = 100f, lengthMm = 200f, odMm = 90f)),
        )
        val marks = previewIssueMarks(specIssues(spec, UnitSystem.MILLIMETERS))
        assertTrue(marks.isEmpty())
        assertNull(marks.axialExtentMm())
        assertTrue(RenderOptions().issueMarks.isEmpty())
    }
}
