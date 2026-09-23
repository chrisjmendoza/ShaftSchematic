package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto-body derivation against a not-yet-authored overall length.
 *
 * `overallLengthMm` of 0 means "no length typed yet", not "zero-length shaft", so there is no
 * shaft span for auto fill to reach for: an empty spec resolves to nothing at all, and a spec
 * with components resolves ONLY the gaps between them — no leading span from 0 to the first
 * component, no trailing span past the last. Both edges appear the moment a positive length is
 * authored (`docs/contracts/OverallLength.md`).
 */
class AutoBodyOalGuardTest {

    private fun autoSpans(spec: ShaftSpec) =
        resolveComponents(spec)
            .filter { it.source == ResolvedComponentSource.AUTO }
            .map { it.startMmPhysical to it.endMmPhysical }

    private val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 100f)
    private val liner = Liner(id = "l1", startFromAftMm = 500f, lengthMm = 100f, odMm = 120f)

    @Test
    fun `a spec with no length and no components resolves to nothing`() {
        assertTrue(resolveComponents(ShaftSpec(overallLengthMm = 0f)).isEmpty())
    }

    @Test
    fun `with no length only the gap between components auto-fills`() {
        val spec = ShaftSpec(overallLengthMm = 0f, bodies = listOf(body), liners = listOf(liner))

        assertEquals("no leading span, no trailing span — only the inter-component gap",
            listOf(400f to 500f), autoSpans(spec))
    }

    @Test
    fun `authoring a length brings the trailing span back`() {
        val spec = ShaftSpec(overallLengthMm = 1000f, bodies = listOf(body), liners = listOf(liner))

        assertEquals("the gap plus the trailing span to the authored end",
            listOf(400f to 500f, 600f to 1000f), autoSpans(spec).sortedBy { it.first })
    }
}
