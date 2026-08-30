package com.android.shaftschematic.ui.input

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.keywayAbsSpanMm
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addTaperAt
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Add-path taper orientation.
 *
 * The S.E.T. the user types must land on the face nearest that end of the shaft — whichever
 * end the Start was measured from. The measure-from chip and the taper's physical half are
 * independent choices; keying the stored diameter order on the chip stores SET at the wrong
 * face whenever they disagree (taper drawn small-end-inboard, card labels reading swapped
 * against the typed values, a keyway "from SET" starting at the far face).
 *
 * These tests drive the pieces `AddTaperDialog` and `ShaftViewModel.addTaperAt` wire together:
 * the dialog's FWD start arithmetic, [taperAddDiameterOrder] over [classifyTaperSideByMidpoint]
 * against the shaft's authored OAL, and [ShaftViewModel.deriveTaperDiameters]. The overhang case
 * additionally drives the real add through a Robolectric-hosted [ShaftViewModel], because the
 * face assertions alone cannot tell the authored-OAL rule from the retired grown-OAL one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaperAddOrientationTest {

    private val oalMm = 2540f       // 100"
    private val lengthMm = 381f     // 15"
    private val setDiaMm = 152.4f   // 6"  — Small End of Taper
    private val letDiaMm = 177.8f   // 7"  — Large End of Taper

    private val aftHalfStartMm = 127f    //  5" from the AFT face — midpoint in the AFT half
    private val fwdHalfStartMm = 2032f   // 80" from the AFT face — midpoint in the FWD half

    /** AddTaperDialog: a FWD-measured start resolves to the canonical AFT-origin start. */
    private fun physStartMm(enteredMm: Float, measureFromFwd: Boolean): Float =
        if (measureFromFwd) (oalMm - enteredMm - lengthMm).coerceAtLeast(0f) else enteredMm

    /** The taper AddTaperDialog + addTaperAt actually store for this placement. */
    private fun storedTaper(
        enteredStartMm: Float,
        measureFromFwd: Boolean,
        currentOalMm: Float = oalMm,
    ): Taper {
        val start = physStartMm(enteredStartMm, measureFromFwd)
        val side = classifyTaperSideByMidpoint(
            startFromAftMm = start,
            lengthMm = lengthMm,
            overallLengthMm = currentOalMm,
        )
        val (startDia, endDia) = taperAddDiameterOrder(setDiaMm, letDiaMm, side)
        return Taper(
            id = "t1",
            startFromAftMm = start,
            lengthMm = lengthMm,
            startDiaMm = startDia,
            endDiaMm = endDia,
            // A keyway so the renderer's SET-face rule can be checked on the same taper.
            keywayWidthMm = 12f,
            keywayDepthMm = 6f,
            keywayLengthMm = 100f,
        )
    }

    /** The value the carousel card prints under its "S.E.T." label. */
    private fun setDiaShownOnCard(t: Taper, overallLengthMm: Float = oalMm): Float {
        val m = taperSetLetMapping(t, overallLengthMm)
        return if (m.leftCode == "S.E.T.") t.startDiaMm else t.endDiaMm
    }

    private fun assertSetFacesAft(t: Taper) {
        assertEquals("SET stored at the AFT face", setDiaMm, t.startDiaMm, 0.001f)
        assertEquals("LET stored at the FWD face", letDiaMm, t.endDiaMm, 0.001f)
        assertEquals("card prints the typed SET under S.E.T.", setDiaMm, setDiaShownOnCard(t), 0.001f)
        val (kwLo, _) = t.keywayAbsSpanMm()!!
        assertEquals("keyway runs from the AFT face", t.startFromAftMm, kwLo, 0.001f)
    }

    private fun assertSetFacesFwd(t: Taper) {
        assertEquals("LET stored at the AFT face", letDiaMm, t.startDiaMm, 0.001f)
        assertEquals("SET stored at the FWD face", setDiaMm, t.endDiaMm, 0.001f)
        assertEquals("card prints the typed SET under S.E.T.", setDiaMm, setDiaShownOnCard(t), 0.001f)
        val (_, kwHi) = t.keywayAbsSpanMm()!!
        assertEquals("keyway runs from the FWD face", t.startFromAftMm + t.lengthMm, kwHi, 0.001f)
    }

    // ── The four (physical half × measure-from) combinations ────────────────────

    @Test
    fun `aft half taper measured from AFT stores SET at the AFT face`() {
        assertSetFacesAft(storedTaper(enteredStartMm = aftHalfStartMm, measureFromFwd = false))
    }

    @Test
    fun `aft half taper measured from FWD stores SET at the AFT face`() {
        // Same placement, entered as 80" back from the FWD face.
        val t = storedTaper(enteredStartMm = oalMm - aftHalfStartMm - lengthMm, measureFromFwd = true)
        assertEquals("same physical placement", aftHalfStartMm, t.startFromAftMm, 0.001f)
        assertSetFacesAft(t)
    }

    @Test
    fun `fwd half taper measured from AFT stores SET at the FWD face`() {
        // The repro: direction chip left on AFT (the default), taper placed in the FWD half.
        assertSetFacesFwd(storedTaper(enteredStartMm = fwdHalfStartMm, measureFromFwd = false))
    }

    @Test
    fun `fwd half taper measured from FWD stores SET at the FWD face`() {
        val t = storedTaper(enteredStartMm = oalMm - fwdHalfStartMm - lengthMm, measureFromFwd = true)
        assertEquals("same physical placement", fwdHalfStartMm, t.startFromAftMm, 0.001f)
        assertSetFacesFwd(t)
    }

    // ── Single diameter + rate: the derived end must be the LET ─────────────────

    @Test
    fun `single typed SET in the fwd half derives LET at the AFT face`() {
        val start = fwdHalfStartMm
        val side = classifyTaperSideByMidpoint(start, lengthMm, oalMm)
        // Only S.E.T. typed; the missing end arrives as the dialog's "not entered" sentinel.
        val (startDia, endDia) = taperAddDiameterOrder(setDiaMm, -1f, side)

        val (resolvedStart, resolvedEnd) = ShaftViewModel.deriveTaperDiameters(
            startDiaMm = startDia,
            endDiaMm = endDia,
            lengthMm = lengthMm,
            rateText = "1:12",
            smallEndAtStart = ShaftViewModel.taperSmallEndAtStart(start, lengthMm, oalMm),
        )

        assertEquals("typed SET kept verbatim at the FWD face", setDiaMm, resolvedEnd, 0.001f)
        assertEquals("LET derived at the AFT face", setDiaMm + lengthMm / 12f, resolvedStart, 0.001f)
    }

    @Test
    fun `single typed SET in the aft half derives LET at the FWD face`() {
        val start = aftHalfStartMm
        val side = classifyTaperSideByMidpoint(start, lengthMm, oalMm)
        val (startDia, endDia) = taperAddDiameterOrder(setDiaMm, -1f, side)

        val (resolvedStart, resolvedEnd) = ShaftViewModel.deriveTaperDiameters(
            startDiaMm = startDia,
            endDiaMm = endDia,
            lengthMm = lengthMm,
            rateText = "1:12",
            smallEndAtStart = ShaftViewModel.taperSmallEndAtStart(start, lengthMm, oalMm),
        )

        assertEquals("typed SET kept verbatim at the AFT face", setDiaMm, resolvedStart, 0.001f)
        assertEquals("LET derived at the FWD face", setDiaMm + lengthMm / 12f, resolvedEnd, 0.001f)
    }

    // ── The half is judged against the authored OAL ─────────────────────────────

    @Test
    fun `an overhanging taper is classified against the authored OAL`() {
        // The OAL stands as authored, so a taper running past the FWD end is still read as a
        // FWD-half taper against that length.
        val t = storedTaper(enteredStartMm = oalMm - 100f, measureFromFwd = false)
        assertSetFacesFwd(t)
    }

    @Test
    fun `nothing grows the shaft around an overhanging taper`() {
        // The half-classification alone cannot separate the authored-OAL rule from the retired
        // grow-the-shaft one — both put SET/LET on the same faces here. What separates them is
        // the shaft: an oversized taper is a legal, advisory state and the OAL stays as typed.
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.onSetOverallLengthMm(oalMm)

        vm.addTaperAt(
            startMm = 2440f,           // 96" — 15" of taper runs 36" past a 100" shaft
            lengthMm = lengthMm,
            startDiaMm = letDiaMm,
            endDiaMm = setDiaMm,
        )

        assertEquals("the taper is stored where it was placed",
            2440f, vm.spec.value.tapers.single().startFromAftMm, 0.001f)
        assertEquals("the authored OAL stands", oalMm, vm.spec.value.overallLengthMm, 0.001f)
    }

    @Test
    fun `a taper on a not-yet-set OAL falls back to the AFT half`() {
        // OAL 0 = not typed yet; [classifyTaperSideByMidpoint] has no frame and defaults AFT.
        val t = storedTaper(
            enteredStartMm = fwdHalfStartMm,
            measureFromFwd = false,
            currentOalMm = 0f,
        )
        assertEquals("SET stored at the AFT face", setDiaMm, t.startDiaMm, 0.001f)
        assertEquals("LET stored at the FWD face", letDiaMm, t.endDiaMm, 0.001f)
    }
}
