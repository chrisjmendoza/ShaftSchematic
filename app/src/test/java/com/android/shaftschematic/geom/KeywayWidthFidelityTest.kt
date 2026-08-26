package com.android.shaftschematic.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How often the keyway visibility floor actually fires on real shafts.
 *
 * The slot's drawn width is TRUE — proportional to its host to the pixel — and only lifts when
 * a true slot would be too thin to read. Unlike a blend, a keyway is roughly a quarter of its
 * shaft, so that corner should be small: this walks the realistic envelope (2"–14" shafts,
 * standard W ≈ D/4, the whole "Shaft height" slider range, both sheets' line weights at 100%
 * and 200% thickness) and pins where the drawing is true and where it is lifted.
 *
 * Guards the floor CONSTANTS as much as the math: raise [MIN_KEYWAY_WIDTH_PT] or
 * [KEYWAY_MIN_WIDTH_STROKES] far enough to start exaggerating ordinary shafts and this fails.
 */
class KeywayWidthFidelityTest {

    private val mmPerIn = 25.4f

    /** Schematic outline is 1.25 pt at 100% line thickness; the runout/consolidated sheet 2.0 pt. */
    private val schematicStrokePt = 1.25f
    private val runoutStrokePt = 2.0f

    /** Standard key width for a shaft diameter (ANSI square-key practice ≈ D/4). */
    private fun keyWidthIn(diaIn: Float) = diaIn / 4f

    /** Drawn ÷ true half-width: 1.0 = exactly proportional, > 1 = lifted by the floor. */
    private fun liftFactor(diaIn: Float, heightFrac: Float, strokePt: Float): Float {
        val diaMm = diaIn * mmPerIn
        // The composers' own scale, minus the page-budget cap (a lower solved scale only makes
        // the floor fire sooner, so this is the optimistic bound the table is read against).
        val scale = exaggeratedProfileScale(
            baseScale = defaultVisualScale(diaMm),
            heightFrac = heightFrac,
            budgetCapPt = Float.MAX_VALUE,
            maxDiaMm = diaMm,
        )
        val trueHalf = keyWidthIn(diaIn) * mmPerIn * scale / 2f
        val drawn = drawnKeywayHalfWidthPx(trueHalf, diaMm * 0.5f * scale, MIN_KEYWAY_WIDTH_PT, strokePt)
        return drawn / trueHalf
    }

    private val marineDiasIn = listOf(2f, 2.5f, 3f, 4f, 5f, 6f, 8f, 10f, 12f, 14f)

    @Test
    fun `every ordinary sheet draws the keyway exactly true`() {
        // Both sheets, default line thickness, the slider at standard and above.
        listOf(schematicStrokePt, runoutStrokePt).forEach { stroke ->
            listOf(1.0f, 1.5f, 2.0f, 3.0f).forEach { h ->
                marineDiasIn.forEach { dia ->
                    assertEquals(
                        "dia ${dia}in, height ${h}, stroke ${stroke}pt must draw true",
                        1f, liftFactor(dia, h, stroke), 1e-3f,
                    )
                }
            }
        }
    }

    /**
     * The floor's whole reach at default line weight: the slider's 50% floor, and only on shafts
     * of 3" and under — where the drawn shaft is 13 pt tall and any rendering is a compromise.
     */
    @Test
    fun `the floor only reaches the smallest shafts at the lowest height`() {
        val lifted = marineDiasIn.filter { liftFactor(it, 0.5f, runoutStrokePt) > 1.001f }
        assertTrue(
            "the floor must not reach ordinary shafts even at 50% height: lifted $lifted",
            lifted.all { it <= 3f },
        )
    }

    /**
     * The host-fraction ceiling bounds the exaggeration outright. A standard key is D/4 of its
     * shaft and the ceiling is [MAX_KEYWAY_FRAC_OF_HOST_DIA] of it, so no setting anywhere can
     * lift a standard keyway past that ratio — 1.6× true, and only where the true slot would be
     * unreadable.
     */
    @Test
    fun `no setting can lift a standard keyway past the host-fraction ceiling`() {
        val worst = MAX_KEYWAY_FRAC_OF_HOST_DIA / 0.25f
        listOf(0.5f, 1f, 2f, 3f).forEach { h ->
            listOf(schematicStrokePt, schematicStrokePt * 2f, runoutStrokePt, runoutStrokePt * 2f).forEach { stroke ->
                marineDiasIn.forEach { dia ->
                    assertTrue(
                        "dia ${dia}in, height $h, stroke ${stroke}pt lifted past the ceiling",
                        liftFactor(dia, h, stroke) <= worst + 1e-3f,
                    )
                }
            }
        }
    }

    /** A 200% line setting is what genuinely needs the floor — thin walls would merge. */
    @Test
    fun `heavy lines widen the floor's reach but not onto ordinary shafts`() {
        marineDiasIn.filter { it >= 4f }.forEach { dia ->
            assertEquals(
                "a 4in-plus shaft draws true even at 200% line thickness",
                1f, liftFactor(dia, 1.0f, runoutStrokePt * 2f), 1e-3f,
            )
        }
    }

    /** A keyway wider than the host fraction is authored geometry — it is drawn, never narrowed. */
    @Test
    fun `an unusually wide keyway is never shrunk to the host fraction`() {
        // Half-width 0.45 of the host radius — above MAX_KEYWAY_FRAC_OF_HOST_DIA (0.40).
        val hostR = 50f
        val trueHalf = hostR * 0.45f
        assertEquals(trueHalf, drawnKeywayHalfWidthPx(trueHalf, hostR, MIN_KEYWAY_WIDTH_PT, 2f), 1e-3f)
    }

    /** …bounded only by the shaft itself: a slot can never draw outside the silhouette. */
    @Test
    fun `a keyway wider than its shaft is clamped at the silhouette`() {
        assertEquals(50f, drawnKeywayHalfWidthPx(80f, 50f, MIN_KEYWAY_WIDTH_PT, 2f), 1e-3f)
    }
}
