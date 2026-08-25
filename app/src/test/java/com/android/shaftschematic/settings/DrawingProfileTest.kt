package com.android.shaftschematic.settings

import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.geom.WEAR_TRACE_MIN_DEPTH_FRAC
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.FractionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The named drawing-look presets: what a profile captures, that a saved one survives the app
 * gaining new drawing prefs, and that an unreadable stored map costs the user their presets
 * rather than the Settings screen.
 *
 * Pure — the payload, its codec and the name rule carry no Android dependency, which is what
 * lets the DataStore layer be a thin call into them.
 */
class DrawingProfileTest {

    // ── What a fresh profile is ─────────────────────────────────────────────────

    @Test
    fun `a default profile is the fresh-install drawing look`() {
        val shipped = PdfPrefs()
        val prefs = DrawingProfile().toPdfPrefs()

        assertEquals(shipped, prefs)
        assertEquals(1.0f, DrawingProfile().lineThicknessScale, 1e-6f)
    }

    @Test
    fun `line thickness default and range are the ones the setters clamp to`() {
        assertEquals(1.0f, DRAWING_LINE_THICKNESS_DEFAULT, 1e-6f)
        assertEquals(0.5f, DRAWING_LINE_THICKNESS_MIN, 1e-6f)
        assertEquals(2.0f, DRAWING_LINE_THICKNESS_MAX, 1e-6f)
    }

    // ── Capture → apply round trip ──────────────────────────────────────────────

    @Test
    fun `capturing tuned prefs and reading them back preserves every field`() {
        val tuned = PdfPrefs(
            tieringMode = PdfTieringMode.FWD,
            showComponentTitles = false,
            shadedBodies = true,
            shadedTapers = true,
            shadedLiners = true,
            curveLoHeightIn = 0.75f,
            curveHiHeightIn = 1.25f,
            sBreakThresholdFrac = 0.35f,
            arrowSizePt = PDF_ARROW_SIZE_LARGE_PT,
            fractionStyle = FractionStyle.STACKED,
            dualUnitLayout = DualUnitLayout.STACKED,
            wearTraceDepthFrac = 0.11f,
            wearBandShadeFrac = 0.22f,
            wearJoinGapMaxMm = 152.4f,
        )

        val profile = DrawingProfile.of(tuned, lineThicknessScale = 1.4f)

        assertEquals(tuned, profile.toPdfPrefs())
        assertEquals(1.4f, profile.clampedLineThicknessScale, 1e-6f)
    }

    // ── Serialization round trip ────────────────────────────────────────────────

    @Test
    fun `a saved map round-trips through JSON unchanged`() {
        val saved = mapOf(
            "Shop standard" to DrawingProfile(),
            "Customer A" to DrawingProfile.of(
                PdfPrefs(
                    shadedLiners = true,
                    arrowSizePt = PDF_ARROW_SIZE_MEDIUM_PT,
                    fractionStyle = FractionStyle.STACKED,
                    curveHiHeightIn = 1.25f,
                ),
                lineThicknessScale = 1.75f,
            ),
        )

        val decoded = decodeDrawingProfiles(encodeDrawingProfiles(saved))

        assertEquals(saved, decoded)
        assertEquals(
            FractionStyle.STACKED,
            decoded.getValue("Customer A").toPdfPrefs().fractionStyle,
        )
    }

    @Test
    fun `every field is written even when it equals a shipped default`() {
        // encodeDefaults is what keeps a later change of a PdfPrefs default from silently
        // restyling profiles saved before it.
        val json = encodeDrawingProfiles(mapOf("Plain" to DrawingProfile()))

        listOf(
            "tieringMode", "showComponentTitles", "shadedBodies", "shadedTapers", "shadedLiners",
            "curveLoHeightIn", "curveHiHeightIn", "sBreakThresholdFrac", "arrowSizePt",
            "fractionStyle", "dualUnitLayout", "wearTraceDepthFrac", "wearBandShadeFrac",
            "wearJoinGapMaxMm", "lineThicknessScale",
        ).forEach { field ->
            assertTrue("missing field in encoded profile: $field", json.contains("\"$field\""))
        }
    }

    // ── Old and unknown payloads ────────────────────────────────────────────────

    @Test
    fun `a profile saved before a pref existed takes that pref's default`() {
        // The additive-decode posture: only two fields were stored, the rest come from
        // the shipped values, so an old preset stays loadable rather than being dropped.
        val raw = """{"Legacy":{"shadedLiners":true,"lineThicknessScale":1.5}}"""

        val profile = decodeDrawingProfiles(raw).getValue("Legacy")

        assertTrue(profile.shadedLiners)
        assertEquals(1.5f, profile.lineThicknessScale, 1e-6f)
        assertEquals(PdfPrefs().sBreakThresholdFrac, profile.sBreakThresholdFrac, 1e-6f)
        assertEquals(PdfPrefs().wearJoinGapMaxMm, profile.wearJoinGapMaxMm, 1e-6f)
        assertEquals(FractionStyle.Default, profile.toPdfPrefs().fractionStyle)
    }

    @Test
    fun `an unknown field from a newer build is ignored, not fatal`() {
        val raw = """{"Newer":{"shadedBodies":true,"someFuturePref":"loud"}}"""

        val decoded = decodeDrawingProfiles(raw)

        assertEquals(1, decoded.size)
        assertTrue(decoded.getValue("Newer").shadedBodies)
    }

    @Test
    fun `an unknown enum name falls back to the shipped look`() {
        val raw = """{"Odd":{"fractionStyle":"CURSIVE","dualUnitLayout":"DIAGONAL","tieringMode":"SIDEWAYS"}}"""

        val prefs = decodeDrawingProfiles(raw).getValue("Odd").toPdfPrefs()

        assertEquals(FractionStyle.Default, prefs.fractionStyle)
        assertEquals(DualUnitLayout.Default, prefs.dualUnitLayout)
        assertEquals(PdfTieringMode.AUTO, prefs.tieringMode)
    }

    @Test
    fun `an unreadable stored value degrades to no saved profiles`() {
        assertTrue(decodeDrawingProfiles(null).isEmpty())
        assertTrue(decodeDrawingProfiles("").isEmpty())
        assertTrue(decodeDrawingProfiles("   ").isEmpty())
        assertTrue(decodeDrawingProfiles("{ not json").isEmpty())
        assertTrue(decodeDrawingProfiles("[1,2,3]").isEmpty())
        assertTrue(decodeDrawingProfiles("""{"Broken":"a string, not a profile"}""").isEmpty())
    }

    // ── Out-of-range payloads ───────────────────────────────────────────────────

    @Test
    fun `applying a profile clamps values outside the settable ranges`() {
        val wild = DrawingProfile(
            curveLoHeightIn = -3f,
            curveHiHeightIn = 9f,
            sBreakThresholdFrac = 4f,
            arrowSizePt = 99f,
            wearTraceDepthFrac = 0f,
            wearBandShadeFrac = 1f,
            wearJoinGapMaxMm = -20f,
            lineThicknessScale = 12f,
        )

        val prefs = wild.toPdfPrefs()

        assertEquals(PDF_CURVE_HEIGHT_MIN_IN, prefs.curveLoHeightIn, 1e-6f)
        assertEquals(PDF_CURVE_HEIGHT_MAX_IN, prefs.curveHiHeightIn, 1e-6f)
        assertEquals(1f, prefs.sBreakThresholdFrac, 1e-6f)
        assertEquals(PDF_ARROW_SIZE_LARGE_PT, prefs.arrowSizePt, 1e-6f)
        assertEquals(WEAR_TRACE_MIN_DEPTH_FRAC, prefs.wearTraceDepthFrac, 1e-6f)
        assertEquals(PDF_WEAR_BAND_SHADE_MAX, prefs.wearBandShadeFrac, 1e-6f)
        assertEquals(PDF_WEAR_JOIN_GAP_MIN_MM, prefs.wearJoinGapMaxMm, 1e-6f)
        assertEquals(DRAWING_LINE_THICKNESS_MAX, wild.clampedLineThicknessScale, 1e-6f)
    }

    @Test
    fun `capturing an out-of-range line thickness stores it clamped`() {
        assertEquals(
            DRAWING_LINE_THICKNESS_MIN,
            DrawingProfile.of(PdfPrefs(), lineThicknessScale = 0.1f).lineThicknessScale,
            1e-6f,
        )
    }

    @Test
    fun `the trace depth high end survives a round trip`() {
        val profile = DrawingProfile.of(
            PdfPrefs(wearTraceDepthFrac = WEAR_TRACE_MAX_DEPTH_FRAC),
            lineThicknessScale = 1f,
        )
        val decoded = decodeDrawingProfiles(encodeDrawingProfiles(mapOf("x" to profile)))

        assertEquals(
            WEAR_TRACE_MAX_DEPTH_FRAC,
            decoded.getValue("x").toPdfPrefs().wearTraceDepthFrac,
            1e-6f,
        )
    }

    // ── Names ───────────────────────────────────────────────────────────────────

    @Test
    fun `a profile name is trimmed and length-capped`() {
        assertEquals("Shop standard", normalizeDrawingProfileName("  Shop standard  "))
        assertEquals(
            DRAWING_PROFILE_NAME_MAX_CHARS,
            normalizeDrawingProfileName("x".repeat(200))!!.length,
        )
    }

    @Test
    fun `a blank name is not a profile name`() {
        assertNull(normalizeDrawingProfileName(""))
        assertNull(normalizeDrawingProfileName("   "))
        assertNull(normalizeDrawingProfileName("\t\n"))
    }

    @Test
    fun `a name that is only spaces past the cap trims away`() {
        // Cap first, then trim: the surviving prefix must not keep a trailing space.
        val name = normalizeDrawingProfileName("A" + " ".repeat(DRAWING_PROFILE_NAME_MAX_CHARS))
        assertEquals("A", name)
        assertFalse(name!!.endsWith(" "))
    }
}
