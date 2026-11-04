package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.MM_PER_IN
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.ui.input.TaperCalcValues
import com.android.shaftschematic.ui.input.TaperSide
import com.android.shaftschematic.ui.input.toApplyValues
import com.android.shaftschematic.ui.input.withTaperCalcValues
import com.android.shaftschematic.util.TaperCalcEntries
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.presentTaperCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaperCalcApplyTest {

    private fun inch(v: Float) = v * MM_PER_IN.toFloat()

    /** L.E.T. 8", S.E.T. 7", 16" long, so exactly 1:16. */
    private val values = TaperCalcValues(inch(8f), inch(7f), inch(16f), typedRateText = null)
    private val oal = inch(300f)

    private fun aftTaper(id: String = "aft") = Taper(
        id = id,
        startFromAftMm = 0f,
        lengthMm = inch(12f),
        startDiaMm = inch(6f),
        endDiaMm = inch(7f),
        taperRateText = "1:12",
        taperRateManual = false,
    )

    private fun fwdTaper(id: String = "fwd") = Taper(
        id = id,
        startFromAftMm = oal - inch(12f),
        lengthMm = inch(12f),
        startDiaMm = inch(7f),
        endDiaMm = inch(6f),
        taperRateText = "1:12",
        authoredReference = LinerAuthoredReference.FWD,
        taperRateManual = false,
    )

    // ── Writing onto an existing taper ───────────────────────────────────────────

    @Test
    fun `an AFT taper keeps its start and small end AFT`() {
        val out = aftTaper().withTaperCalcValues(values, oal)
        assertEquals(0f, out.startFromAftMm, 0f)
        assertEquals(inch(16f), out.lengthMm, 0f)
        assertEquals(inch(7f), out.startDiaMm, 0f)
        assertEquals(inch(8f), out.endDiaMm, 0f)
    }

    @Test
    fun `a FWD-measured taper keeps its FWD end and its large end stays at its AFT face`() {
        val before = fwdTaper()
        val out = before.withTaperCalcValues(values, oal)
        assertEquals(
            "the FWD end stays put",
            before.startFromAftMm + before.lengthMm,
            out.startFromAftMm + out.lengthMm,
            0.01f,
        )
        assertEquals(inch(8f), out.startDiaMm, 0f)
        assertEquals(inch(7f), out.endDiaMm, 0f)
    }

    @Test
    fun `a taper facing against its half keeps the way it faces`() {
        // AFT half, but authored with the large end at its AFT face.
        val backwards = aftTaper().copy(startDiaMm = inch(7f), endDiaMm = inch(6f))
        val out = backwards.withTaperCalcValues(values, oal)
        assertEquals(inch(8f), out.startDiaMm, 0f)
        assertEquals(inch(7f), out.endDiaMm, 0f)
    }

    @Test
    fun `a taper with no facing yet takes the small end from its half of the shaft`() {
        val blank = fwdTaper().copy(startDiaMm = 0f, endDiaMm = 0f)
        val out = blank.withTaperCalcValues(values, oal)
        assertEquals("FWD half: large end at the AFT face", inch(8f), out.startDiaMm, 0f)
        assertEquals(inch(7f), out.endDiaMm, 0f)
    }

    @Test
    fun `everything the calculator does not own survives`() {
        val before = aftTaper().copy(
            keywayWidthMm = 10f,
            keywayDepthMm = 5f,
            keywayLengthMm = 80f,
            keywayOffsetFromSetMm = 12f,
            keywaySpooned = true,
            label = "Coupling end",
            showNameOnDrawing = true,
            shadeOnDrawing = false,
        )
        val out = before.withTaperCalcValues(values, oal)
        assertEquals(before.id, out.id)
        assertEquals(before.authoredReference, out.authoredReference)
        assertEquals(before.keywayWidthMm, out.keywayWidthMm, 0f)
        assertEquals(before.keywayDepthMm, out.keywayDepthMm, 0f)
        assertEquals(before.keywayLengthMm, out.keywayLengthMm, 0f)
        assertEquals(before.keywayOffsetFromSetMm, out.keywayOffsetFromSetMm, 0f)
        assertTrue(out.keywaySpooned)
        assertEquals("Coupling end", out.label)
        assertEquals(true, out.showNameOnDrawing)
        assertEquals(false, out.shadeOnDrawing)
    }

    // ── The rate ─────────────────────────────────────────────────────────────────

    @Test
    fun `an Auto taper stays Auto and its rate follows the new geometry even over a typed rate`() {
        val out = aftTaper().withTaperCalcValues(values.copy(typedRateText = "3/4\"/ft"), oal)
        assertEquals(false, out.taperRateManual)
        assertEquals("1:16", out.taperRateText)
    }

    @Test
    fun `a Manual taper stays Manual and takes the typed rate verbatim`() {
        val manual = aftTaper().copy(taperRateManual = true, taperRateText = "1:10")
        val out = manual.withTaperCalcValues(values.copy(typedRateText = "3/4\"/ft"), oal)
        assertEquals(true, out.taperRateManual)
        assertEquals("3/4\"/ft", out.taperRateText)
    }

    @Test
    fun `a Manual taper with a derived rate drops the rate the new geometry contradicts`() {
        val manual = aftTaper().copy(taperRateManual = true, taperRateText = "1:10")
        val out = manual.withTaperCalcValues(values, oal)
        assertEquals(true, out.taperRateManual)
        assertEquals("1:16", out.taperRateText)
    }

    @Test
    fun `a taper saved before the mode existed resolves it once and stores it`() {
        val legacyAuto = aftTaper().copy(taperRateManual = null, taperRateText = "1:12")
        assertEquals(false, legacyAuto.withTaperCalcValues(values, oal).taperRateManual)
        val legacyManual = aftTaper().copy(taperRateManual = null, taperRateText = "1:9")
        assertEquals(true, legacyManual.withTaperCalcValues(values, oal).taperRateManual)
    }

    // ── From the calculator's entries ────────────────────────────────────────────

    @Test
    fun `a derived value applies exact, not as the rounded text on screen`() {
        // 8" less 11.7" at 1:16 is 7.26875", which the field shows as 7.269.
        val entries = TaperCalcEntries(largeDia = "8", length = "11.7", rate = "1:16", unit = UnitSystem.INCHES)
        val shown = presentTaperCalc(entries, calculated = true)
        val solved = assertNotNullSolved(shown.applicable)
        assertEquals("7.269", shown.field(com.android.shaftschematic.util.TaperCalcField.SMALL_DIA).derivedText)
        assertEquals(inch(7.26875f), solved.toApplyValues(entries).smallDiaMm, 0.001f)
        assertEquals("1:16", solved.toApplyValues(entries).typedRateText)
    }

    private fun <T> assertNotNullSolved(v: T?): T {
        assertNotNull(v)
        return v!!
    }

    @Test
    fun `nothing can be applied until Calculate, while values are missing, or while four disagree`() {
        val three = TaperCalcEntries(largeDia = "8", smallDia = "7", length = "16")
        assertNull(presentTaperCalc(three, calculated = false).applicable)
        assertNull(presentTaperCalc(three.copy(length = ""), calculated = true).applicable)
        assertNull(presentTaperCalc(three.copy(rate = "1:12"), calculated = true).applicable)
        assertNotNull(presentTaperCalc(three, calculated = true).applicable)
        assertNotNull("four that agree can apply", presentTaperCalc(three.copy(rate = "1:16"), calculated = true).applicable)
    }

    // ── The question ─────────────────────────────────────────────────────────────

    @Test
    fun `no taper offers both ends and opens on neither`() {
        val choices = taperCalcChoices(ShaftSpec(overallLengthMm = oal))
        assertEquals(
            listOf<TaperCalcChoice>(TaperCalcChoice.NewAtEnd(TaperSide.AFT), TaperCalcChoice.NewAtEnd(TaperSide.FWD)),
            choices,
        )
        assertNull(defaultTaperCalcChoice(choices, null))
    }

    @Test
    fun `no OAL offers only the AFT end, and opens on it`() {
        val choices = taperCalcChoices(ShaftSpec(overallLengthMm = 0f))
        assertEquals(listOf<TaperCalcChoice>(TaperCalcChoice.NewAtEnd(TaperSide.AFT)), choices)
        assertEquals(TaperCalcChoice.NewAtEnd(TaperSide.AFT), defaultTaperCalcChoice(choices, null))
    }

    @Test
    fun `one taper is the default, and the free end is still offered`() {
        val choices = taperCalcChoices(ShaftSpec(overallLengthMm = oal, tapers = listOf(aftTaper())))
        assertEquals(
            listOf(TaperCalcChoice.Existing("aft"), TaperCalcChoice.NewAtEnd(TaperSide.FWD)),
            choices,
        )
        assertEquals(TaperCalcChoice.Existing("aft"), defaultTaperCalcChoice(choices, null))
    }

    @Test
    fun `two tapers open on the selected one, or on nothing rather than a guess`() {
        val spec = ShaftSpec(overallLengthMm = oal, tapers = listOf(fwdTaper(), aftTaper()))
        val choices = taperCalcChoices(spec)
        assertEquals(
            "AFT → FWD, and no new-taper rows with both ends taken",
            listOf<TaperCalcChoice>(TaperCalcChoice.Existing("aft"), TaperCalcChoice.Existing("fwd")),
            choices,
        )
        assertNull(defaultTaperCalcChoice(choices, null))
        assertNull(defaultTaperCalcChoice(choices, "some-body"))
        assertEquals(TaperCalcChoice.Existing("fwd"), defaultTaperCalcChoice(choices, "fwd"))
    }

    // ── A new taper ──────────────────────────────────────────────────────────────

    @Test
    fun `a new AFT taper sits flush with its small end at the shaft end`() {
        val t = newTaperFromCalc(ShaftSpec(overallLengthMm = oal), values, TaperSide.AFT)
        assertEquals(0f, t.startFromAftMm, 0f)
        assertEquals(inch(7f), t.startDiaMm, 0f)
        assertEquals(inch(8f), t.endDiaMm, 0f)
        assertEquals(LinerAuthoredReference.AFT, t.authoredReference)
        assertEquals(false, t.taperRateManual)
        assertEquals("1:16", t.taperRateText)
    }

    @Test
    fun `a new FWD taper sits flush with its small end at the shaft end, measured from FWD`() {
        val t = newTaperFromCalc(ShaftSpec(overallLengthMm = oal), values, TaperSide.FWD)
        assertEquals(oal - inch(16f), t.startFromAftMm, 0.01f)
        assertEquals(inch(8f), t.startDiaMm, 0f)
        assertEquals(inch(7f), t.endDiaMm, 0f)
        assertEquals(LinerAuthoredReference.FWD, t.authoredReference)
    }

    @Test
    fun `a new AFT taper goes flush even with parts further along the shaft`() {
        val liner = Liner(id = "l", startFromAftMm = inch(100f), lengthMm = inch(20f), odMm = inch(9f))
        val start = taperCalcNewStartMm(ShaftSpec(overallLengthMm = oal, liners = listOf(liner)), inch(16f), TaperSide.AFT)
        assertEquals("not the Add dialog's end-of-the-last-component seed", 0f, start, 0f)
    }

    @Test
    fun `a new AFT taper butts a thread on the AFT end`() {
        val thread = Threads(id = "th", startFromAftMm = 0f, lengthMm = inch(4f), majorDiaMm = inch(4f), pitchMm = 2f)
        val start = taperCalcNewStartMm(ShaftSpec(overallLengthMm = oal, threads = listOf(thread)), inch(16f), TaperSide.AFT)
        assertEquals(inch(4f), start, 0.01f)
    }

    @Test
    fun `a new FWD taper butts a thread on the FWD end`() {
        val thread = Threads(id = "th", startFromAftMm = oal - inch(4f), lengthMm = inch(4f), majorDiaMm = inch(4f), pitchMm = 2f)
        val start = taperCalcNewStartMm(ShaftSpec(overallLengthMm = oal, threads = listOf(thread)), inch(16f), TaperSide.FWD)
        assertEquals(oal - inch(20f), start, 0.01f)
    }

    @Test
    fun `a free span in the other half is not at that end, so the flush start stands and warns`() {
        // A liner over the whole AFT half: the only room is past midship.
        val liner = Liner(id = "l", startFromAftMm = 0f, lengthMm = inch(170f), odMm = inch(9f))
        val spec = ShaftSpec(overallLengthMm = oal, liners = listOf(liner))
        assertEquals(0f, taperCalcNewStartMm(spec, inch(16f), TaperSide.AFT), 0f)
        val plan = planTaperCalcApply(
            spec,
            TaperCalcChoice.NewAtEnd(TaperSide.AFT),
            values,
            UnitSystem.INCHES,
            UnitSystem.INCHES,
        )
        assertTrue(plan!!.warnings.any { it.startsWith("Overlaps") })
    }

    // ── The confirmation ─────────────────────────────────────────────────────────

    private fun plan(
        spec: ShaftSpec,
        choice: TaperCalcChoice,
        v: TaperCalcValues = values,
    ) = requireNotNull(planTaperCalcApply(spec, choice, v, UnitSystem.INCHES, UnitSystem.INCHES))

    @Test
    fun `the confirmation shows before and after in the entry unit`() {
        val p = plan(ShaftSpec(overallLengthMm = oal, tapers = listOf(aftTaper())), TaperCalcChoice.Existing("aft"))
        assertEquals(
            listOf(
                TaperCalcChangeRow("Large end Ø (in)", "7", "8"),
                TaperCalcChangeRow("Small end Ø (in)", "6", "7"),
                TaperCalcChangeRow("Length (in)", "12", "16"),
                TaperCalcChangeRow("Rate (Auto)", "1:12", "1:16"),
            ),
            p.rows,
        )
    }

    @Test
    fun `the note says which end a length change moves`() {
        val aft = plan(ShaftSpec(overallLengthMm = oal, tapers = listOf(aftTaper())), TaperCalcChoice.Existing("aft"))
        assertTrue(aft.note, aft.note.startsWith("Measured from AFT, so its AFT end stays and its FWD end moves."))
        val fwd = plan(ShaftSpec(overallLengthMm = oal, tapers = listOf(fwdTaper())), TaperCalcChoice.Existing("fwd"))
        assertTrue(fwd.note, fwd.note.startsWith("Measured from FWD, so its FWD end stays and its AFT end moves."))
        val same = plan(
            ShaftSpec(overallLengthMm = oal, tapers = listOf(aftTaper())),
            TaperCalcChoice.Existing("aft"),
            values.copy(lengthMm = inch(12f)),
        )
        assertTrue(same.note, same.note.startsWith("Its position does not change."))
    }

    @Test
    fun `growth into a neighbour past the OAL or past a keyway warns and never blocks`() {
        val liner = Liner(id = "l", startFromAftMm = inch(14f), lengthMm = inch(20f), odMm = inch(9f))
        val overlapping = plan(
            ShaftSpec(overallLengthMm = oal, tapers = listOf(aftTaper()), liners = listOf(liner)),
            TaperCalcChoice.Existing("aft"),
        )
        assertTrue(overlapping.warnings.toString(), overlapping.warnings.any { it.startsWith("Overlaps") })

        val pastOal = plan(
            ShaftSpec(overallLengthMm = oal, tapers = listOf(aftTaper().copy(startFromAftMm = oal - inch(12f)))),
            TaperCalcChoice.Existing("aft"),
        )
        assertTrue(pastOal.warnings.toString(), pastOal.warnings.any { it.startsWith("Extends past shaft length") })

        val longKeyway = plan(
            ShaftSpec(overallLengthMm = oal, tapers = listOf(aftTaper().copy(keywayLengthMm = inch(20f)))),
            TaperCalcChoice.Existing("aft"),
        )
        assertTrue(longKeyway.warnings.toString(), longKeyway.warnings.contains("The keyway runs past this length"))
    }

    @Test
    fun `a clean write warns of nothing and a gone taper has no plan`() {
        val spec = ShaftSpec(overallLengthMm = oal, tapers = listOf(aftTaper()))
        assertFalse(plan(spec, TaperCalcChoice.Existing("aft")).warnings.isNotEmpty())
        assertNull(planTaperCalcApply(spec, TaperCalcChoice.Existing("gone"), values, UnitSystem.INCHES, UnitSystem.INCHES))
    }
}
