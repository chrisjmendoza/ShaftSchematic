package com.android.shaftschematic.geom

import com.android.shaftschematic.model.UndercutReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A cut typed on a liner's page that misses the liner ([undercutOffLiner]) and the mirrored-datum
 * fix for it ([undercutMirrorOntoLiner]); a duplicate span ([undercutDuplicatePartnerId]).
 * On-device report: a sketch drawn mirrored was entered from the FWD S.E.T. instead of the AFT one,
 * and the cuts landed on bare shaft clear of the liner.
 */
class UndercutOffLinerMathTest {

    private val liner = UndercutLinerSpan("L", 500f, 1000f)

    @Test
    fun `a span on the liner, across an edge or touching one is on it`() {
        assertNull(undercutOffLiner(600f, 50f, liner, emptyList()))
        assertNull(undercutOffLiner(450f, 100f, liner, emptyList()))
        assertNull(undercutOffLiner(950f, 100f, liner, emptyList()))
        // Ends exactly on the AFT edge, starts exactly on the FWD edge.
        assertNull(undercutOffLiner(450f, 50f, liner, emptyList()))
        assertNull(undercutOffLiner(1000f, 50f, liner, emptyList()))
        // Within the touch slop.
        assertNull(undercutOffLiner(1000f + UNDERCUT_TOUCH_EPS_MM / 2f, 50f, liner, emptyList()))
    }

    @Test
    fun `a span clear of the liner says which side and how far`() {
        val fwd = requireNotNull(undercutOffLiner(1172.6f, 25.4f, liner, emptyList()))
        assertEquals(UndercutLinerSide.FWD, fwd.side)
        assertEquals(172.6f, fwd.gapMm, 1e-3f)

        val aft = requireNotNull(undercutOffLiner(300f, 50f, liner, emptyList()))
        assertEquals(UndercutLinerSide.AFT, aft.side)
        assertEquals(150f, aft.gapMm, 1e-3f)
    }

    @Test
    fun `a run chained off the section's cuts is on the section`() {
        // A cut 1000–1050 runs off the liner's FWD edge; one butted on it, and one nested in it,
        // belong to the liner's section, as the strip builder assigns them.
        val section = listOf(UndercutSpanMm("a", 1000f, 1050f))
        assertNull(undercutOffLiner(1050f, 30f, liner, section))
        assertNull(undercutOffLiner(1010f, 20f, liner, section))
        assertNull(undercutOffLiner(1040f, 30f, liner, section))
        // Clear of every section cut: off it.
        assertNotNull(undercutOffLiner(1060f, 30f, liner, section))
        // A degenerate section span chains nothing.
        assertNotNull(undercutOffLiner(1200f, 30f, liner, listOf(UndercutSpanMm("z", 1230f, 1230f))))
    }

    @Test
    fun `a span with no length is not called off the liner`() {
        assertNull(undercutOffLiner(1500f, 0f, liner, emptyList()))
    }

    @Test
    fun `the mirror keeps the typed distance and reads it from the other SET`() {
        // AFT S.E.T. at 0, FWD S.E.T. at 3048. 24 in (609.6) from the FWD S.E.T., 2 in long:
        // canonical 3048 − 609.6 − 50.8 = 2387.6, off the 500–1000 liner. From the AFT S.E.T.
        // the same 24 in puts it at 609.6–660.4, on the liner.
        val m = requireNotNull(
            undercutMirrorOntoLiner(UndercutReference.FWD_SET, 2387.6f, 50.8f, 0f, 3048f, liner),
        )
        assertEquals(UndercutReference.AFT_SET, m.reference)
        assertEquals(609.6f, m.startMm, 1e-3f)
        assertEquals(609.6f, m.distanceMm, 1e-3f)

        // The other way round: 609.6 from the AFT S.E.T. read from the FWD one.
        val back = requireNotNull(undercutMirroredSetStart(UndercutReference.AFT_SET, 609.6f, 50.8f, 0f, 3048f))
        assertEquals(UndercutReference.FWD_SET, back.reference)
        assertEquals(2387.6f, back.startMm, 1e-3f)
        assertEquals(609.6f, back.distanceMm, 1e-3f)
    }

    @Test
    fun `no mirror off an S_E_T_ or when the mirror misses the liner too`() {
        assertNull(undercutMirroredSetStart(UndercutReference.LINER_AFT, 600f, 50f, 0f, 3048f))
        assertNull(undercutMirrorOntoLiner(UndercutReference.LINER_FWD, 1200f, 50f, 0f, 3048f, liner))
        // 100 from the AFT S.E.T. (100–150, off the liner) reads 2898–2948 from the FWD one: off too.
        assertNull(undercutMirrorOntoLiner(UndercutReference.AFT_SET, 100f, 50f, 0f, 3048f, liner))
    }

    @Test
    fun `a duplicate is the same span within the touch slop`() {
        val others = listOf(UndercutSpanMm("a", 200f, 300f), UndercutSpanMm("b", 400f, 500f))
        assertEquals("b", undercutDuplicatePartnerId(400f, 100f, others))
        assertEquals("a", undercutDuplicatePartnerId(200f + UNDERCUT_TOUCH_EPS_MM / 2f, 100f, others))
        assertNull(undercutDuplicatePartnerId(250f, 100f, others))
        assertNull(undercutDuplicatePartnerId(210f, 80f, others))
        assertEquals("Same span as cut 2 — entered twice?", undercutDuplicateMessage(2))
        assertEquals("Same span as another cut — entered twice?", undercutDuplicateMessage(null))
    }
}
