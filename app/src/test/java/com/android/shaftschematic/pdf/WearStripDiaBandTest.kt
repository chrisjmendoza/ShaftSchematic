package com.android.shaftschematic.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `computeWearStripInnerLayout` with the measured-Ø callout band (`diaBandPt`):
 * a zero band must reproduce the pre-callout layout exactly (regression pin), a positive
 * band comes out of the cylinder, and pathological inputs still keep every y inside the
 * strip — the same ordering guarantee the base layout makes.
 */
class WearStripDiaBandTest {

    private val titleH = 9f

    @Test
    fun `zero dia band reproduces the previous layout exactly`() {
        val base = computeWearStripInnerLayout(100f, 240f, titleHeightPt = titleH)
        val withZero = computeWearStripInnerLayout(100f, 240f, titleHeightPt = titleH, diaBandPt = 0f)
        assertEquals(base.cylTop, withZero.cylTop, 0f)
        assertEquals(base.cylBottom, withZero.cylBottom, 0f)
        assertEquals(base.railY, withZero.railY, 0f)
        assertEquals(base.railLabelRows, withZero.railLabelRows)
    }

    @Test
    fun `positive dia band raises the cylinder bottom by exactly the band height`() {
        val base = computeWearStripInnerLayout(100f, 240f, titleHeightPt = titleH)
        val banded = computeWearStripInnerLayout(100f, 240f, titleHeightPt = titleH, diaBandPt = 21f)
        assertEquals(base.cylBottom - 21f, banded.cylBottom, 0.001f)
        // The cylinder shrinks; the rail keeps its full label-row budget while there's room.
        assertTrue(banded.cylBottom - banded.cylTop < base.cylBottom - base.cylTop + 0.001f)
    }

    @Test
    fun `negative dia band is treated as zero`() {
        val base = computeWearStripInnerLayout(100f, 240f, titleHeightPt = titleH)
        val banded = computeWearStripInnerLayout(100f, 240f, titleHeightPt = titleH, diaBandPt = -5f)
        assertEquals(base.cylBottom, banded.cylBottom, 0f)
    }

    @Test
    fun `pathologically short strip still keeps ordering and stays in bounds`() {
        val l = computeWearStripInnerLayout(100f, 118f, titleHeightPt = titleH, diaBandPt = 30f)
        assertTrue(l.railY >= 100f)
        assertTrue(l.railY <= l.cylTop)
        assertTrue(l.cylTop <= l.cylBottom)
        assertTrue(l.cylBottom <= 118f)
    }
}
