package com.android.shaftschematic.geom

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.settings.RunoutConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tapers ride the "Liner & taper compression" request with the liners — one control, one
 * λ, so the two measured kinds keep the SAME fraction of true length and the sheet reads
 * even (on-device request: a slider that walked liners up to true length while tapers
 * stayed at their baseline printed one measured kind at full scale beside another at 70%).
 *
 * Pins both halves of the rule:
 * - UPWARD coupling — above [PROFILE_TAPER_MIN_FRAC_OF_TRUE] the taper floor IS the liner
 *   request, and the two kinds keep equal fractions of true width under a squeeze.
 * - The baseline holds BELOW it — tapers never follow the liners down to a bare request,
 *   because they carry no flat floor to land on (a flat floor equalizes unequal tapers).
 */
class TaperLinerCompressionTest {

    private fun spec() = ShaftSpec(
        overallLengthMm = 3000f,
        tapers = listOf(Taper(id = "t", startFromAftMm = 0f, lengthMm = 400f)),
        liners = listOf(Liner(id = "l", startFromAftMm = 1000f, lengthMm = 400f)),
    )

    private fun taperFracIn(spec: ShaftSpec, request: Float): Float =
        profileFeatureSpans(
            spec,
            linerFloorPt = PROFILE_MIN_LINER_PT,
            threadFloorPt = PROFILE_MIN_THREAD_PT,
            linerMinFracOfTrue = request,
        ).single { it.startMm == 0f && it.endMm == 400f }.minWidthFracOfTrue

    @Test
    fun `taper floor is the liner request above the baseline`() {
        assertEquals(0.8f, taperMinFracOfTrue(0.8f), 1e-6f)
        assertEquals(1f, taperMinFracOfTrue(1f), 1e-6f)
    }

    @Test
    fun `taper floor never drops below its baseline`() {
        assertEquals(PROFILE_TAPER_MIN_FRAC_OF_TRUE, taperMinFracOfTrue(0f), 1e-6f)
        assertEquals(PROFILE_TAPER_MIN_FRAC_OF_TRUE, taperMinFracOfTrue(0.3f), 1e-6f)
        // Out-of-range requests are clamped, never smuggled into the floor.
        assertEquals(PROFILE_TAPER_MIN_FRAC_OF_TRUE, taperMinFracOfTrue(-1f), 1e-6f)
        assertEquals(1f, taperMinFracOfTrue(5f), 1e-6f)
    }

    @Test
    fun `the default job leaves tapers exactly where they were`() {
        // Default RunoutConfig = full compression allowed → request 0. Sheets that never
        // touch the control must print byte-identically to before the coupling existed.
        assertEquals(0f, RunoutConfig().linerMinFracOfTrue, 1e-6f)
        assertEquals(PROFILE_TAPER_MIN_FRAC_OF_TRUE, taperFracIn(spec(), 0f), 1e-6f)
    }

    @Test
    fun `the shared builder hands tapers the coupled fraction`() {
        val spec = spec()
        assertEquals(0.9f, taperFracIn(spec, 0.9f), 1e-6f)
        // Checking "Keep liners and tapers proportional lengthwise" asks BOTH for true width.
        val proportional = RunoutConfig(linersProportional = true).linerMinFracOfTrue
        assertEquals(1f, taperFracIn(spec, proportional), 1e-6f)
    }

    @Test
    fun `taper and liner keep the same fraction of true length under a squeeze`() {
        // 3000mm window at 1 pt/mm true into 700pt — a hard squeeze, so the λ fit bites
        // and neither kind gets its full request. The point is that they land TOGETHER.
        val spec = spec()
        val features = profileFeatureSpans(
            spec,
            linerFloorPt = PROFILE_MIN_LINER_PT,
            threadFloorPt = PROFILE_MIN_THREAD_PT,
            linerMinFracOfTrue = 0.9f,
        )
        val map = buildCompressedProfileXMap(
            windowStartMm = 0f, windowEndMm = 3000f,
            features = features,
            contentLeft = 0f, contentRight = 700f,
            diaPtPerMm = 1f,
        )
        val taperKept = (map.xAt(400f) - map.xAt(0f)) / 400f
        val linerKept = (map.xAt(1400f) - map.xAt(1000f)) / 400f
        assertEquals("the two measured kinds must read even", linerKept, taperKept, 1e-2f)
        assertTrue("both were squeezed below the request ($taperKept)", taperKept < 0.9f)
        assertEquals(700f, map.x1, 0.1f)
    }

    @Test
    fun `raising the request lengthens the tapers`() {
        // The visible effect of the change: dragging toward proportional grows the taper,
        // where before it sat at the baseline whatever the slider said.
        val spec = spec()
        fun taperWidth(request: Float): Float {
            val map = buildCompressedProfileXMap(
                windowStartMm = 0f, windowEndMm = 3000f,
                features = profileFeatureSpans(
                    spec,
                    linerFloorPt = PROFILE_MIN_LINER_PT,
                    threadFloorPt = PROFILE_MIN_THREAD_PT,
                    linerMinFracOfTrue = request,
                ),
                contentLeft = 0f, contentRight = 700f,
                diaPtPerMm = 1f,
            )
            return map.xAt(400f) - map.xAt(0f)
        }
        assertTrue(
            "a proportional request must draw the taper longer than a bare one",
            taperWidth(1f) > taperWidth(0f) + 1f,
        )
    }

    @Test
    fun `unequal tapers still never equalize at any request`() {
        // The standing invariant: the coupling raises a FRACTION of true width, so two
        // very different tapers keep their true ratio at every setting.
        val spec = ShaftSpec(
            overallLengthMm = 3000f,
            tapers = listOf(
                Taper(id = "a", startFromAftMm = 0f, lengthMm = 495f),
                Taper(id = "b", startFromAftMm = 2500f, lengthMm = 292f),
            ),
        )
        listOf(0f, 0.5f, 0.85f, 1f).forEach { request ->
            val map = buildCompressedProfileXMap(
                windowStartMm = 0f, windowEndMm = 3000f,
                features = profileFeatureSpans(
                    spec,
                    linerFloorPt = PROFILE_MIN_LINER_PT,
                    threadFloorPt = PROFILE_MIN_THREAD_PT,
                    linerMinFracOfTrue = request,
                ),
                contentLeft = 0f, contentRight = 700f,
                diaPtPerMm = 1f,
            )
            val wAft = map.xAt(495f) - map.xAt(0f)
            val wFwd = map.xAt(2792f) - map.xAt(2500f)
            assertEquals("true ratio at request $request", 495f / 292f, wAft / wFwd, 2e-2f)
        }
    }

    @Test
    fun `the coupled taper floor still never lowers the drawn height`() {
        // Height precedence (on-device direction): frac raises are scale-blind, and the
        // taper's raise is one of them — a fully proportional request must not drag the
        // solved scale down the way a keyway pin does.
        val spec = spec()
        fun solved(request: Float) = solveMaxProfileScale(
            windowStartMm = 0f, windowEndMm = 3000f,
            features = profileFeatureSpans(
                spec,
                linerFloorPt = PROFILE_MIN_LINER_PT,
                threadFloorPt = PROFILE_MIN_THREAD_PT,
                linerMinFracOfTrue = request,
            ),
            contentWidth = 700f, scaleHi = 1f,
        )
        assertEquals("frac raises must stay scale-blind", solved(0f), solved(1f), 1e-4f)
    }
}
