package com.android.shaftschematic.ui.drawing

import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.LinerDim
import com.android.shaftschematic.settings.PdfTieringMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DimDebugTextTest {

    private fun dim(id: String, anchor: LinerAnchor, offsetMm: Double) =
        LinerDim(id = id, anchor = anchor, offsetFromSetMm = offsetMm, lengthMm = 100.0)

    @Test
    fun `AFT mode prints origin as zero`() {
        val lines = dimDebugLines(PdfTieringMode.AFT, tierOriginMm = 0.0, dims = emptyList())
        assertEquals("dim: mode=AFT tierOrigin=0.0mm", lines.first())
    }

    @Test
    fun `FWD mode prints origin as the OAL value`() {
        val lines = dimDebugLines(PdfTieringMode.FWD, tierOriginMm = 2540.0, dims = emptyList())
        assertEquals("dim: mode=FWD tierOrigin=2540.0mm", lines.first())
    }

    @Test
    fun `AUTO mode prints the L-to-R placeholder regardless of a null origin`() {
        val lines = dimDebugLines(PdfTieringMode.AUTO, tierOriginMm = null, dims = emptyList())
        assertEquals("dim: mode=AUTO tierOrigin=auto (L-to-R)", lines.first())
    }

    @Test
    fun `one line per liner in stored order, 1-based, one decimal`() {
        val dims = listOf(
            dim("l1", LinerAnchor.AFT_SET, 123.456),
            dim("l2", LinerAnchor.FWD_SET, 7.0),
        )
        val lines = dimDebugLines(PdfTieringMode.AUTO, tierOriginMm = null, dims = dims)
        assertEquals(3, lines.size)
        assertEquals("L1 → AFT_SET +123.5mm", lines[1])
        assertEquals("L2 → FWD_SET +7.0mm", lines[2])
    }

    @Test
    fun `liner list at the cap prints no overflow line`() {
        val dims = (1..6).map { dim("l$it", LinerAnchor.AFT_SET, it.toDouble()) }
        val lines = dimDebugLines(PdfTieringMode.AUTO, tierOriginMm = null, dims = dims, maxLiners = 6)
        assertEquals(1 + 6, lines.size)
        assertTrue(lines.none { it.startsWith("…") })
    }

    @Test
    fun `liner list beyond the cap truncates with an overflow line`() {
        val dims = (1..9).map { dim("l$it", LinerAnchor.AFT_SET, it.toDouble()) }
        val lines = dimDebugLines(PdfTieringMode.AUTO, tierOriginMm = null, dims = dims, maxLiners = 6)
        // header + 6 liner lines + 1 overflow line
        assertEquals(1 + 6 + 1, lines.size)
        assertEquals("… (3 more)", lines.last())
        assertEquals("L6 → AFT_SET +6.0mm", lines[6])
    }

    @Test
    fun `empty liner list prints only the header line`() {
        val lines = dimDebugLines(PdfTieringMode.AFT, tierOriginMm = 0.0, dims = emptyList())
        assertEquals(1, lines.size)
    }
}
