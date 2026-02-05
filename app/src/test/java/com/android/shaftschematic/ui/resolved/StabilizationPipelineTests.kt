package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilizationPipelineTests {

    @Test
    fun oal_is_never_mutated_by_resolve_or_auto_bodies() {
        val spec = ShaftSpec(
            overallLengthMm = 250f,
            bodies = listOf(Body(id = "B1", startFromAftMm = 0f, lengthMm = 50f, diaMm = 40f)),
            liners = listOf(Liner(id = "L1", startFromAftMm = 80f, lengthMm = 20f, odMm = 45f))
        )
        val snapshot = spec.copy()

        resolveComponents(spec, overallIsManual = true)
        deriveAutoBodies(spec.overallLengthMm, resolveExplicitComponents(spec))

        assertEquals(snapshot.overallLengthMm, spec.overallLengthMm, 1e-6f)
        assertEquals(snapshot, spec)
    }

    @Test
    fun liner_immutability_on_other_component_edit() {
        val liner1 = Liner(id = "L1", startFromAftMm = 10f, lengthMm = 10f, odMm = 30f)
        val liner2 = Liner(id = "L2", startFromAftMm = 40f, lengthMm = 10f, odMm = 35f)
        val spec = ShaftSpec(overallLengthMm = 200f, liners = listOf(liner1, liner2))

        val updated = spec.copy(
            liners = listOf(liner1, liner2.copy(lengthMm = 15f))
        )
        resolveComponents(updated, overallIsManual = true)

        assertEquals(liner1, updated.liners.first())
    }

    @Test
    fun auto_bodies_are_not_stored_in_spec() {
        val spec = ShaftSpec(
            overallLengthMm = 200f,
            bodies = listOf(Body(id = "B1", startFromAftMm = 0f, lengthMm = 20f, diaMm = 50f)),
            liners = listOf(Liner(id = "L1", startFromAftMm = 60f, lengthMm = 20f, odMm = 55f))
        )
        val before = spec.bodies.toList()

        resolveComponents(spec, overallIsManual = true)

        assertEquals(before, spec.bodies)
    }

    @Test
    fun draft_add_does_not_mutate_spec_until_commit() {
        val liner = Liner(id = "L1", startFromAftMm = 20f, lengthMm = 10f, odMm = 30f)
        val spec = ShaftSpec(overallLengthMm = 200f, liners = listOf(liner))
        val snapshot = spec.copy()

        val draft = DraftComponent.Liner(
            id = "D1",
            startMmPhysical = 80f,
            lengthMm = 15f,
            odMm = 35f
        )

        resolveComponents(spec, overallIsManual = true, draft = draft)

        assertEquals(snapshot, spec)
    }

    @Test
    fun split_body_yields_separate_segments() {
        val body = Body(id = "B1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val liner = Liner(id = "L1", startFromAftMm = 40f, lengthMm = 20f, odMm = 55f)
        val spec = ShaftSpec(overallLengthMm = 200f, bodies = listOf(body), liners = listOf(liner))

        val resolved = resolveComponents(spec, overallIsManual = true)
        val bodySegments = resolved.filterIsInstance<ResolvedBody>().filter { it.authoredSourceId == "B1" }

        assertEquals("Segments=$bodySegments", 2, bodySegments.size)
        assertTrue(
            "Expected segment 0..40, got $bodySegments",
            bodySegments.any { approx(it.startMmPhysical, 0f) && approx(it.endMmPhysical, 40f) }
        )
        assertTrue(
            "Expected segment 60..100, got $bodySegments",
            bodySegments.any { approx(it.startMmPhysical, 60f) && approx(it.endMmPhysical, 100f) }
        )
    }

    private fun approx(a: Float, b: Float, eps: Float = 1e-3f): Boolean = kotlin.math.abs(a - b) <= eps
}
