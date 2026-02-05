package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.withPhysical
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.abs

class ComponentInvariantTests {

    @Test
    fun adding_or_resolving_components_does_not_modify_existing_specs() {
        val body = Body(id = "B1", startFromAftMm = 0f, lengthMm = 25f, diaMm = 40f)
        val liner = Liner(id = "L1", startFromAftMm = 40f, lengthMm = 20f, odMm = 45f)
        val spec = ShaftSpec(
            overallLengthMm = 200f,
            bodies = listOf(body),
            liners = listOf(liner)
        )
        val snapshot = spec.deepCopy()

        resolveComponents(spec, overallIsManual = true)
        deriveAutoBodies(
            overallLengthMm = spec.overallLengthMm,
            explicitComponents = resolveExplicitComponents(spec)
        )

        val added = spec.copy(
            bodies = spec.bodies + Body(id = "B2", startFromAftMm = 80f, lengthMm = 10f, diaMm = 35f)
        )
        resolveComponents(added, overallIsManual = true)

        assertEquals(snapshot, spec)
    }

    @Test
    fun resolving_and_deriving_components_never_mutates_spec_fields() {
        val body = Body(id = "B1", startFromAftMm = 10f, lengthMm = 30f, diaMm = 60f)
        val liner = Liner(
            id = "L1",
            startFromAftMm = 70f,
            lengthMm = 20f,
            odMm = 65f,
            authoredReference = LinerAuthoredReference.AFT,
            endMmPhysical = 90f
        )
        val spec = ShaftSpec(
            overallLengthMm = 200f,
            bodies = listOf(body),
            liners = listOf(liner)
        )
        val snapshot = spec.deepCopy()

        resolveComponents(spec, overallIsManual = true)
        resolveExplicitComponents(spec)
        deriveAutoBodies(
            overallLengthMm = spec.overallLengthMm,
            explicitComponents = resolveExplicitComponents(spec)
        )

        assertEquals(snapshot, spec)
    }

    @Test
    fun fwd_set_liner_placement_does_not_mutate_input() {
        val oal = 200f
        val authoredStartFwd = 15f
        val length = 20f
        val physicalStart = oal - authoredStartFwd - length
        val physicalEnd = physicalStart + length

        val liner = Liner(
            id = "L1",
            startFromAftMm = physicalStart,
            lengthMm = length,
            odMm = 50f,
            authoredReference = LinerAuthoredReference.FWD,
            authoredStartFromFwdMm = authoredStartFwd,
            endMmPhysical = physicalEnd
        )
        val body = Body(id = "B1", startFromAftMm = 0f, lengthMm = 10f, diaMm = 40f)
        val spec = ShaftSpec(overallLengthMm = oal, bodies = listOf(body), liners = listOf(liner))
        val snapshot = spec.deepCopy()

        val resolved = resolveComponents(spec, overallIsManual = true)
        val resolvedLiner = resolved.filterIsInstance<ResolvedLiner>().single { it.id == "L1" }

        assertEquals(snapshot, spec)
        assertEquals(physicalStart, liner.startFromAftMm, 1e-4f)
        assertEquals(length, liner.lengthMm, 1e-4f)
        assertEquals(physicalEnd, liner.endMmPhysical, 1e-4f)
        assertEquals(LinerAuthoredReference.FWD, liner.authoredReference)

        val authoredFromSpec = oal - liner.startFromAftMm - liner.lengthMm
        assertEquals(authoredStartFwd, authoredFromSpec, 1e-4f)
        assertEquals(physicalStart, resolvedLiner.startMmPhysical, 1e-4f)
        assertEquals(physicalEnd, resolvedLiner.endMmPhysical, 1e-4f)
    }

    @Test
    fun auto_body_span_invariant_length_equals_end_minus_start() {
        val b1 = Body(id = "B1", startFromAftMm = 0f, lengthMm = 10f, diaMm = 40f)
        val b2 = Body(id = "B2", startFromAftMm = 30f, lengthMm = 10f, diaMm = 40f)
        val spec = ShaftSpec(overallLengthMm = 0f, bodies = listOf(b1, b2))

        val autoBodies = deriveAutoBodies(
            overallLengthMm = 0f,
            explicitComponents = resolveExplicitComponents(spec)
        )
        val gap = autoBodies
            .filterIsInstance<ResolvedBody>()
            .firstOrNull {
                it.source == ResolvedComponentSource.AUTO &&
                    approx(it.startMmPhysical, 10f) &&
                    approx(it.endMmPhysical, 30f)
            }

        assertNotNull("Expected auto body for 10..30 gap", gap)
        gap!!

        assertEquals(10f, gap.startMmPhysical, 1e-4f)
        assertEquals(30f, gap.endMmPhysical, 1e-4f)
        assertEquals(20f, gap.endMmPhysical - gap.startMmPhysical, 1e-4f)
        assertEquals(gap.endMmPhysical - gap.startMmPhysical, gap.lengthMm(), 1e-4f)
    }

    @Test
    fun auto_body_between_34_875_and_127_875_has_length_93() {
        val b1 = Body(id = "B1", startFromAftMm = 0f, lengthMm = 34.875f, diaMm = 40f)
        val b2 = Body(id = "B2", startFromAftMm = 127.875f, lengthMm = 10f, diaMm = 40f)
        val spec = ShaftSpec(overallLengthMm = 0f, bodies = listOf(b1, b2))

        val autoBodies = deriveAutoBodies(
            overallLengthMm = 0f,
            explicitComponents = resolveExplicitComponents(spec)
        )
        val gap = autoBodies
            .filterIsInstance<ResolvedBody>()
            .firstOrNull {
                it.source == ResolvedComponentSource.AUTO &&
                    approx(it.startMmPhysical, 34.875f, 1e-3f) &&
                    approx(it.endMmPhysical, 127.875f, 1e-3f)
            }

        assertNotNull("Expected auto body for 34.875..127.875 gap", gap)
        gap!!

        assertEquals(34.875f, gap.startMmPhysical, 1e-3f)
        assertEquals(127.875f, gap.endMmPhysical, 1e-3f)
        assertEquals(93.0f, gap.endMmPhysical - gap.startMmPhysical, 1e-3f)
        assertEquals(gap.endMmPhysical - gap.startMmPhysical, gap.lengthMm(), 1e-3f)
    }

    @Test
    fun editing_one_liner_does_not_modify_other_liners() {
        val l1 = Liner(
            id = "L1",
            startFromAftMm = 100f,
            lengthMm = 20f,
            odMm = 50f,
            endMmPhysical = 120f,
            authoredReference = LinerAuthoredReference.AFT
        )
        val l2 = Liner(
            id = "L2",
            startFromAftMm = 140f,
            lengthMm = 20f,
            odMm = 55f,
            endMmPhysical = 160f,
            authoredReference = LinerAuthoredReference.AFT
        )
        val spec = ShaftSpec(overallLengthMm = 300f, liners = listOf(l1, l2))
        val snapshotSecond = l2.copy()

        val updated = spec.copy(
            liners = spec.liners.toMutableList().also { list ->
                list[0] = l1.withPhysical(startMmPhysical = 110f, lengthMm = 25f, odMm = 50f)
            }
        )

        resolveComponents(updated, overallIsManual = true)

        assertEquals(snapshotSecond, updated.liners[1])
        assertEquals(snapshotSecond, spec.liners[1])
    }

    @Test
    fun adding_component_does_not_modify_existing_specs() {
        val body = Body(id = "B1", startFromAftMm = 0f, lengthMm = 40f, diaMm = 55f)
        val liner = Liner(id = "L1", startFromAftMm = 60f, lengthMm = 20f, odMm = 65f, endMmPhysical = 80f)
        val spec = ShaftSpec(overallLengthMm = 200f, bodies = listOf(body), liners = listOf(liner))
        val snapshot = spec.deepCopy()

        val added = spec.copy(
            bodies = spec.bodies + Body(id = "B2", startFromAftMm = 100f, lengthMm = 10f, diaMm = 45f)
        )
        resolveComponents(added, overallIsManual = true)

        assertEquals(snapshot, spec)
        assertEquals(snapshot.bodies, spec.bodies)
        assertEquals(snapshot.liners, spec.liners)
        assertEquals(snapshot.overallLengthMm, spec.overallLengthMm, 1e-4f)
        assertEquals(2, added.bodies.size)
        assertEquals(1, spec.bodies.size)
    }

    private fun approx(actual: Float, expected: Float, eps: Float = 1e-4f): Boolean =
        abs(actual - expected) <= eps

    private fun ResolvedBody.lengthMm(): Float {
        val getter = this.javaClass.methods.firstOrNull {
            it.name == "getLengthMm" && it.parameterCount == 0
        }
        if (getter != null) {
            val value = getter.invoke(this)
            return when (value) {
                is Float -> value
                is Double -> value.toFloat()
                else -> throw AssertionError("ResolvedBody.lengthMm has unexpected type: ${value?.javaClass}")
            }
        }

        val field = this.javaClass.declaredFields.firstOrNull { it.name == "lengthMm" }
        if (field != null) {
            field.isAccessible = true
            val value = field.get(this)
            return when (value) {
                is Float -> value
                is Double -> value.toFloat()
                else -> throw AssertionError("ResolvedBody.lengthMm has unexpected type: ${value?.javaClass}")
            }
        }

        throw AssertionError("ResolvedBody.lengthMm is missing; auto-body length must be stored explicitly")
    }

    private fun ShaftSpec.deepCopy(): ShaftSpec = copy(
        bodies = bodies.map { it.copy() },
        tapers = tapers.map { it.copy() },
        threads = threads.map { it.copy() },
        liners = liners.map { it.copy() }
    )
}
