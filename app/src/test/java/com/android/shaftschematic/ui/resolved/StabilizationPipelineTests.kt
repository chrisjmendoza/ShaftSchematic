package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.TaperOrientation
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

        resolveComponents(spec)
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
        resolveComponents(updated)

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

        resolveComponents(spec)

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

        resolveComponents(spec, draft = draft)

        assertEquals(snapshot, spec)
    }

    @Test
    fun split_body_yields_separate_segments() {
        val body = Body(id = "B1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f)
        val liner = Liner(id = "L1", startFromAftMm = 40f, lengthMm = 20f, odMm = 55f)
        val spec = ShaftSpec(overallLengthMm = 200f, bodies = listOf(body), liners = listOf(liner))

        val resolved = resolveComponents(spec)
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

    @Test
    fun editing_oal_same_value_does_not_shift_components() {
        val taper = Taper(
            id = "T1",
            startFromAftMm = 50f,
            lengthMm = 20f,
            startDiaMm = 40f,
            endDiaMm = 30f,
            orientation = TaperOrientation.AFT
        )
        val spec = ShaftSpec(overallLengthMm = 120f, tapers = listOf(taper))

        val resolved1 = resolveComponents(spec)
        val resolved2 = resolveComponents(spec.copy(overallLengthMm = 120f))

        val t1 = resolved1.filterIsInstance<ResolvedTaper>().single()
        val t2 = resolved2.filterIsInstance<ResolvedTaper>().single()

        assertEquals(t1.startMmPhysical, t2.startMmPhysical, 1e-4f)
        assertEquals(t1.endMmPhysical, t2.endMmPhysical, 1e-4f)
    }

    @Test
    fun temporary_oal_change_does_not_snap_components() {
        val taper = Taper(
            id = "T1",
            startFromAftMm = 60f,
            lengthMm = 20f,
            startDiaMm = 40f,
            endDiaMm = 30f,
            orientation = TaperOrientation.AFT
        )
        val spec = ShaftSpec(overallLengthMm = 120f, tapers = listOf(taper))

        val resolved = resolveComponents(spec.copy(overallLengthMm = 90f))
        val t = resolved.filterIsInstance<ResolvedTaper>().single()

        assertEquals(60f, t.startMmPhysical, 1e-4f)
        assertEquals(80f, t.endMmPhysical, 1e-4f)
    }

    private fun approx(a: Float, b: Float, eps: Float = 1e-3f): Boolean = kotlin.math.abs(a - b) <= eps
}
