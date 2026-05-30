package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.settings.PdfTieringMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for mapToLinerDimsForPdf — the adapter that converts model Liners into LinerDim
 * objects used by the PDF dimension pipeline.
 *
 * Key behaviors under test:
 *  - AUTO mode: anchor inferred by proximity to AFT vs FWD SET
 *  - AFT / FWD forced modes override proximity
 *  - Offset values are SET→near-edge distances in measurement space
 */
class LinerDimAdapterTest {

    private fun liner(startMm: Float, lengthMm: Float) =
        Liner(startFromAftMm = startMm, lengthMm = lengthMm, odMm = 80f)

    // Simple shaft: two tapers at each end (provide SETs), one body, no threads.
    // OAL = 1000mm, AFT taper 0..200, FWD taper 800..1000.
    // SETs: aftSET = 0mm (taper start), fwdSET = 1000mm (taper end) → measure-space same as physical.
    private fun baseSpec(vararg liners: Liner) = ShaftSpec(
        overallLengthMm = 1000f,
        tapers = listOf(
            Taper(startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 100f, endDiaMm = 80f),
            Taper(startFromAftMm = 800f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 100f)
        ),
        liners = liners.toList()
    )

    @Test
    fun `AUTO mode assigns AFT anchor when liner is closer to AFT SET`() {
        // Liner at 250..350mm — 250mm from AFT SET (0), 650mm from FWD SET (1000)
        val spec = baseSpec(liner(250f, 100f))
        val dims = mapToLinerDimsForPdf(spec, PdfTieringMode.AUTO)

        assertEquals(1, dims.size)
        assertEquals(LinerAnchor.AFT_SET, dims[0].anchor)
        assertEquals(250.0, dims[0].offsetFromSetMm, 1e-6)  // AFT SET=0, liner AFT edge=250
    }

    @Test
    fun `AUTO mode assigns FWD anchor when liner is closer to FWD SET`() {
        // Liner at 700..800mm — 700mm from AFT SET (0), 200mm from FWD SET (1000)
        val spec = baseSpec(liner(700f, 100f))
        val dims = mapToLinerDimsForPdf(spec, PdfTieringMode.AUTO)

        assertEquals(1, dims.size)
        assertEquals(LinerAnchor.FWD_SET, dims[0].anchor)
        assertEquals(200.0, dims[0].offsetFromSetMm, 1e-6)  // FWD SET=1000, liner FWD edge=800
    }

    @Test
    fun `AFT forced mode overrides proximity and anchors to AFT SET`() {
        // Liner near FWD end — would normally get FWD anchor in AUTO
        val spec = baseSpec(liner(700f, 100f))
        val dims = mapToLinerDimsForPdf(spec, PdfTieringMode.AFT)

        assertEquals(LinerAnchor.AFT_SET, dims[0].anchor)
        assertEquals(700.0, dims[0].offsetFromSetMm, 1e-6)  // AFT SET=0, liner AFT edge=700
    }

    @Test
    fun `FWD forced mode overrides proximity and anchors to FWD SET`() {
        // Liner near AFT end — would normally get AFT anchor in AUTO
        val spec = baseSpec(liner(250f, 100f))
        val dims = mapToLinerDimsForPdf(spec, PdfTieringMode.FWD)

        assertEquals(LinerAnchor.FWD_SET, dims[0].anchor)
        assertEquals(650.0, dims[0].offsetFromSetMm, 1e-6)  // FWD SET=1000, liner FWD edge=350
    }

    @Test
    fun `length is preserved in measure space`() {
        val spec = baseSpec(liner(400f, 150f))
        val dims = mapToLinerDimsForPdf(spec, PdfTieringMode.AUTO)

        assertEquals(150.0, dims[0].lengthMm, 1e-6)
    }

    @Test
    fun `offset is zero when liner AFT edge sits exactly on AFT SET`() {
        val spec = baseSpec(liner(0f, 100f))
        val dims = mapToLinerDimsForPdf(spec, PdfTieringMode.AFT)

        assertEquals(LinerAnchor.AFT_SET, dims[0].anchor)
        assertEquals(0.0, dims[0].offsetFromSetMm, 1e-6)
    }

    @Test
    fun `multiple liners each get independently anchored`() {
        val spec = baseSpec(liner(250f, 50f), liner(700f, 50f))
        val dims = mapToLinerDimsForPdf(spec, PdfTieringMode.AUTO)

        assertEquals(2, dims.size)
        val anchors = dims.map { it.anchor }.toSet()
        // One near AFT, one near FWD → both anchors should appear
        assertEquals(setOf(LinerAnchor.AFT_SET, LinerAnchor.FWD_SET), anchors)
    }

    @Test
    fun `AUTO mode assigns AFT anchor when liner sits exactly at midpoint (tie goes to AFT)`() {
        // Liner at 450..550mm — 450mm from AFT SET (0), 450mm from FWD SET (1000).
        // distFwd < distAft is false (equal), so the else branch gives AFT anchor.
        val spec = baseSpec(liner(450f, 100f))
        val dims = mapToLinerDimsForPdf(spec, PdfTieringMode.AUTO)

        assertEquals(LinerAnchor.AFT_SET, dims[0].anchor)
        assertEquals(450.0, dims[0].offsetFromSetMm, 1e-6)
    }

    @Test
    fun `measurement space is rebased when AFT thread is excluded`() {
        // Excluded AFT thread of 100mm shifts measurement origin to x=100.
        // Liner at 300..400mm physical → 200..300mm in measure space.
        // AFT SET should be at measure-x = 0 (taper starts at physical x=100 = measure x=0).
        val excludedThread = Threads(
            startFromAftMm = 0f, lengthMm = 100f,
            majorDiaMm = 60f, pitchMm = 2f, excludeFromOAL = true
        )
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(excludedThread),
            tapers = listOf(
                Taper(startFromAftMm = 100f, lengthMm = 200f, startDiaMm = 100f, endDiaMm = 80f),
                Taper(startFromAftMm = 800f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 100f)
            ),
            liners = listOf(liner(300f, 100f))
        )

        val dims = mapToLinerDimsForPdf(spec, PdfTieringMode.AFT)
        // measureStart = 100mm, so liner AFT edge in measure space = 300 - 100 = 200mm
        assertEquals(200.0, dims[0].offsetFromSetMm, 1e-6)
    }
}
