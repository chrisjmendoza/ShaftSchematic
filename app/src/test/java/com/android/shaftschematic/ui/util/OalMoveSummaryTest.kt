package com.android.shaftschematic.ui.util

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.withNewOal
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [oalMoveSummary] — the snackbar line that says what an OAL commit carried along with the FWD end
 * (on-device report: an OAL correction moved a FWD liner into a taper and nothing said so).
 */
class OalMoveSummaryTest {

    private val inch = UnitSystem.INCHES
    private val mm = UnitSystem.MILLIMETERS

    private fun fwdLiner(id: String, start: Float) =
        Liner(id = id, startFromAftMm = start, lengthMm = 100f, odMm = 90f, authoredReference = LinerAuthoredReference.FWD)

    @Test
    fun `the on-device correction names the liner, the distance and the direction`() {
        val before = ShaftDocCodec.decode(
            requireNotNull(javaClass.classLoader?.getResource("fixtures/mid_taper_pac_fish.shaft")).readText(),
        ).spec
        val after = before.withNewOal(234.1875f * 25.4f)

        assertEquals(
            "OAL 234 3/16\": moved FWD Liner 4 13/16\" aft (measured from FWD)",
            oalMoveSummary(before, after, inch),
        )
    }

    @Test
    fun `nothing moved, no summary`() {
        val before = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 500f, diaMm = 50f)),
            liners = listOf(Liner(startFromAftMm = 100f, lengthMm = 100f, odMm = 90f)),
        )
        assertNull(oalMoveSummary(before, before.withNewOal(1200f), mm))
        assertNull("the same OAL again", oalMoveSummary(before, before.withNewOal(1000f), mm))
    }

    @Test
    fun `a longer shaft carries FWD parts fwd`() {
        val before = ShaftSpec(overallLengthMm = 1000f, liners = listOf(fwdLiner("l", 800f)))
        assertEquals(
            "OAL 1050 mm: moved FWD Liner 50 mm fwd (measured from FWD)",
            oalMoveSummary(before, before.withNewOal(1050f), mm),
        )
    }

    @Test
    fun `two names, then a count`() {
        val taper = Taper(
            id = "t", startFromAftMm = 900f, lengthMm = 100f, startDiaMm = 80f, endDiaMm = 60f,
            authoredReference = LinerAuthoredReference.FWD,
        )
        val two = ShaftSpec(overallLengthMm = 1000f, tapers = listOf(taper), liners = listOf(fwdLiner("l1", 700f)))
        assertEquals(
            "OAL 990 mm: moved FWD Liner and FWD Taper 10 mm aft (measured from FWD)",
            oalMoveSummary(two, two.withNewOal(990f), mm),
        )

        val four = two.copy(liners = listOf(fwdLiner("l1", 500f), fwdLiner("l2", 650f), fwdLiner("l3", 780f)))
        assertEquals(
            "OAL 990 mm: moved MID Liner, FWD Liner #1 +2 more 10 mm aft (measured from FWD)",
            oalMoveSummary(four, four.withNewOal(990f), mm),
        )
    }

    @Test
    fun `an excluded thread following the end is not reported`() {
        val before = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(
                Threads(startFromAftMm = 1000f, lengthMm = 50f, majorDiaMm = 60f, pitchMm = 3f, excludeFromOAL = true, isAftEnd = false),
            ),
        )
        assertNull(oalMoveSummary(before, before.withNewOal(900f), mm))
    }
}
