package com.android.shaftschematic.pdf

import com.android.shaftschematic.geom.RunoutBubbleGeometry
import com.android.shaftschematic.settings.PDF_RUNOUT_BUBBLE_DROP_SCALE_MAX
import com.android.shaftschematic.settings.PDF_RUNOUT_BUBBLE_DROP_SCALE_MIN
import com.android.shaftschematic.settings.PDF_RUNOUT_BUBBLE_SCALE_MAX
import com.android.shaftschematic.settings.PDF_RUNOUT_BUBBLE_SCALE_MIN
import com.android.shaftschematic.settings.PdfPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Bubble size" / "Bubble height": the two multipliers the runout sheet and the Runout tab's
 * canvas apply to the bubble radius and the drop below the shaft.
 *
 * The point of the defaults is that they change NOTHING: an untouched install must build the
 * exact geometry the sheet shipped with, so this pins the base constants against the product of
 * base × default. It also pins what is deliberately NOT scaled — the minimum gap between
 * circles, which is a clearance floor rather than part of a bubble's size.
 */
class RunoutBubbleScalePrefsTest {

    private fun geomFor(prefs: PdfPrefs) = RunoutBubbleGeometry(
        radius = BUBBLE_RADIUS_PT * prefs.runoutBubbleScale,
        minGap = BUBBLE_MIN_GAP_PT,
        shortLeader = SHORT_LEADER_PT * prefs.runoutBubbleDropScale,
        contentLeft = 0f,
        contentRight = 720f,
    )

    @Test
    fun `defaults are neutral multipliers`() {
        val p = PdfPrefs()
        assertEquals(1f, p.runoutBubbleScale, 1e-6f)
        assertEquals(1f, p.runoutBubbleDropScale, 1e-6f)
    }

    @Test
    fun `default prefs reproduce the shipped bubble geometry`() {
        val g = geomFor(PdfPrefs())
        assertEquals(23f, g.radius, 1e-4f)
        assertEquals(18f, g.shortLeader, 1e-4f)
        assertEquals(5f, g.minGap, 1e-4f)
    }

    @Test
    fun `radius and drop scale independently, the gap not at all`() {
        val g = geomFor(PdfPrefs(runoutBubbleScale = 1.5f, runoutBubbleDropScale = 0.5f))
        assertEquals(34.5f, g.radius, 1e-4f)
        assertEquals(9f, g.shortLeader, 1e-4f)
        assertEquals(5f, g.minGap, 1e-4f)
    }

    @Test
    fun `out-of-range values are clamped to the stored bounds`() {
        val low = PdfPrefs(runoutBubbleScale = 0.01f, runoutBubbleDropScale = 0.01f).clamped()
        assertEquals(PDF_RUNOUT_BUBBLE_SCALE_MIN, low.runoutBubbleScale, 1e-6f)
        assertEquals(PDF_RUNOUT_BUBBLE_DROP_SCALE_MIN, low.runoutBubbleDropScale, 1e-6f)

        val high = PdfPrefs(runoutBubbleScale = 99f, runoutBubbleDropScale = 99f).clamped()
        assertEquals(PDF_RUNOUT_BUBBLE_SCALE_MAX, high.runoutBubbleScale, 1e-6f)
        assertEquals(PDF_RUNOUT_BUBBLE_DROP_SCALE_MAX, high.runoutBubbleDropScale, 1e-6f)
    }
}
