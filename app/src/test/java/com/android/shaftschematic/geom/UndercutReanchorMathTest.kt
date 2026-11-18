package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * An undercut always keeps its reference position (on-device ruling): when an edit moves the
 * datum a cut's Distance was typed against, its canonical start follows so the Distance still
 * reads as typed; a cut whose datum did not move is left as the same instance.
 */
class UndercutReanchorMathTest {

    private val eps = 1e-3f

    private fun datums(aft: Float = 0f, fwd: Float = 1000f, vararg liners: UndercutLinerSpan) =
        UndercutDatums(aft, fwd, liners.toList())

    private fun cut(
        start: Float,
        length: Float,
        ref: UndercutReference,
        id: String = "c",
        liner: String = "",
        parent: String = "",
    ) = Undercut(
        id = id, startFromAftMm = start, lengthMm = length, diaMm = 150f,
        authoredReference = ref, referenceLinerId = liner, referenceCutId = parent,
    )

    @Test
    fun `AFT_SET follows the AFT S E T`() {
        val u = cut(300f, 50f, UndercutReference.AFT_SET)
        val out = reanchorUndercuts(listOf(u), datums(aft = 100f), datums(aft = 140f)).single()
        assertEquals(340f, out.startFromAftMm, eps)
        assertEquals(50f, out.lengthMm)
        assertEquals(150f, out.diaMm)
    }

    @Test
    fun `FWD_SET keeps its FWD end's distance from the FWD S E T`() {
        val u = cut(800f, 50f, UndercutReference.FWD_SET)
        val out = reanchorUndercuts(listOf(u), datums(fwd = 1000f), datums(fwd = 1200f)).single()
        assertEquals(1000f, out.startFromAftMm, eps)
        assertEquals(
            "the Distance against the new datum is the typed 150",
            150f,
            canonicalToUndercutStartMm(UndercutReference.FWD_SET, out.startFromAftMm, out.lengthMm, 0f, 1200f),
            eps,
        )
    }

    @Test
    fun `LINER_AFT follows the liner's AFT edge and LINER_FWD its FWD edge`() {
        val before = datums(liners = arrayOf(UndercutLinerSpan("L", 200f, 400f)))
        // Moved 100 FWD and lengthened 50: AFT edge 200 → 300, FWD edge 400 → 550.
        val after = datums(liners = arrayOf(UndercutLinerSpan("L", 300f, 550f)))
        val aftRef = cut(250f, 20f, UndercutReference.LINER_AFT, id = "a", liner = "L")
        val fwdRef = cut(350f, 20f, UndercutReference.LINER_FWD, id = "f", liner = "L")

        val out = reanchorUndercuts(listOf(aftRef, fwdRef), before, after)

        assertEquals(350f, out[0].startFromAftMm, eps)
        assertEquals("FWD end stays 30 from the liner's FWD edge", 500f, out[1].startFromAftMm, eps)
    }

    @Test
    fun `a datum that did not move leaves the cut the same instance`() {
        val aftRef = cut(300f, 50f, UndercutReference.AFT_SET, id = "a")
        val linerAft = cut(250f, 20f, UndercutReference.LINER_AFT, id = "l", liner = "L")
        val list = listOf(aftRef, linerAft)
        // FWD S.E.T. moved and the liner grew FWD-ward: neither of these cuts' datums moved.
        val out = reanchorUndercuts(
            list,
            datums(fwd = 1000f, liners = arrayOf(UndercutLinerSpan("L", 200f, 400f))),
            datums(fwd = 1300f, liners = arrayOf(UndercutLinerSpan("L", 200f, 600f))),
        )
        assertSame("nothing moved: the very list comes back", list, out)
    }

    @Test
    fun `float noise below the epsilon rewrites nothing`() {
        val u = cut(300f, 50f, UndercutReference.AFT_SET)
        val list = listOf(u)
        assertSame(list, reanchorUndercuts(list, datums(aft = 100f), datums(aft = 100.0004f)))
    }

    @Test
    fun `a missing reference liner leaves the cut untouched`() {
        val u = cut(250f, 20f, UndercutReference.LINER_AFT, liner = "gone")
        val list = listOf(u)
        val out = reanchorUndercuts(
            list,
            datums(liners = arrayOf(UndercutLinerSpan("L", 200f, 400f))),
            datums(liners = arrayOf(UndercutLinerSpan("L", 300f, 500f))),
        )
        assertSame(list, out)

        // Present before, deleted by the edit: no datum to follow either.
        val stays = cut(250f, 20f, UndercutReference.LINER_AFT, liner = "L")
        val removed = listOf(stays)
        assertSame(
            removed,
            reanchorUndercuts(removed, datums(liners = arrayOf(UndercutLinerSpan("L", 200f, 400f))), datums()),
        )
    }

    @Test
    fun `a nested chain follows its parents, parents first whatever the record order`() {
        // grandchild (PARENT_FWD of child) → child (PARENT_AFT of parent) → parent (LINER_AFT).
        val grand = cut(270f, 5f, UndercutReference.PARENT_FWD, id = "g", parent = "c")
        val child = cut(260f, 20f, UndercutReference.PARENT_AFT, id = "c", parent = "p")
        val parent = cut(250f, 60f, UndercutReference.LINER_AFT, id = "p", liner = "L")
        val out = reanchorUndercuts(
            listOf(grand, child, parent),
            datums(liners = arrayOf(UndercutLinerSpan("L", 200f, 400f))),
            datums(liners = arrayOf(UndercutLinerSpan("L", 225f, 425f))),
        ).associateBy { it.id }

        assertEquals(275f, out.getValue("p").startFromAftMm, eps)
        assertEquals(285f, out.getValue("c").startFromAftMm, eps)
        assertEquals(295f, out.getValue("g").startFromAftMm, eps)
    }

    @Test
    fun `a missing parent or a parent loop leaves the cut untouched`() {
        val orphan = cut(260f, 20f, UndercutReference.PARENT_AFT, id = "o", parent = "gone")
        val loopA = cut(300f, 20f, UndercutReference.PARENT_AFT, id = "x", parent = "y")
        val loopB = cut(305f, 10f, UndercutReference.PARENT_FWD, id = "y", parent = "x")
        val self = cut(400f, 10f, UndercutReference.PARENT_AFT, id = "s", parent = "s")
        val list = listOf(orphan, loopA, loopB, self)
        assertSame(list, reanchorUndercuts(list, datums(aft = 0f), datums(aft = 50f, fwd = 1500f)))
    }

    @Test
    fun `the spec-level record re-anchor reads the S E T s and liners off the specs`() {
        val before = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(Liner(id = "L", startFromAftMm = 200f, lengthMm = 200f, odMm = 120f)),
        )
        val after = before.copy(
            overallLengthMm = 1200f,
            liners = listOf(Liner(id = "L", startFromAftMm = 260f, lengthMm = 200f, odMm = 120f)),
        )
        val record = UndercutRecord(
            undercuts = listOf(
                cut(800f, 50f, UndercutReference.FWD_SET, id = "f"),
                cut(100f, 50f, UndercutReference.AFT_SET, id = "a"),
                cut(250f, 20f, UndercutReference.LINER_AFT, id = "l", liner = "L"),
            ),
        )

        val out = record.reanchoredFor(before, after).undercuts.associateBy { it.id }

        assertEquals("no FWD taper: the FWD S.E.T. is the OAL", 1000f, out.getValue("f").startFromAftMm, eps)
        assertSame(record.undercuts[1], out.getValue("a"))
        assertEquals(310f, out.getValue("l").startFromAftMm, eps)
        assertSame("an unchanged spec returns the record itself", record, record.reanchoredFor(before, before))
    }
}
