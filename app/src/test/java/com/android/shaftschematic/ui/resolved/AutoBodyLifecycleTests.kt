package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.AuthoredReference
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.TaperOrientation
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AutoBodyLifecycleTests {

    @Test
    fun auto_body_created_between_two_liners() {
        val spec = ShaftSpec(
            overallLengthMm = 0f,
            liners = listOf(
                liner(id = "A", start = 0f, length = 20f),
                liner(id = "B", start = 60f, length = 10f)
            )
        )

        val auto = autoBodies(spec)

        assertEquals(1, auto.size)
        assertAuto(auto[0], start = 20f, end = 60f)
    }

    @Test
    fun auto_body_removed_when_gap_disappears() {
        val initial = ShaftSpec(
            overallLengthMm = 0f,
            liners = listOf(
                liner(id = "A", start = 0f, length = 20f),
                liner(id = "B", start = 60f, length = 10f)
            )
        )
        val filled = initial.copy(
            liners = initial.liners + liner(id = "C", start = 20f, length = 40f)
        )

        val auto = autoBodies(filled)
        assertEquals(0, auto.size)
    }

    @Test
    fun auto_body_lengthens_when_liner_moves_away() {
        val initial = ShaftSpec(
            overallLengthMm = 0f,
            liners = listOf(
                liner(id = "A", start = 0f, length = 20f),
                liner(id = "B", start = 60f, length = 10f)
            )
        )
        val moved = initial.copy(
            liners = listOf(
                initial.liners[0],
                initial.liners[1].copy(startFromAftMm = 80f, endMmPhysical = 90f)
            )
        )

        val auto = autoBodies(moved)

        assertEquals(1, auto.size)
        assertAuto(auto[0], start = 20f, end = 80f)
    }

    @Test
    fun auto_body_shortens_when_liner_moves_closer() {
        val initial = ShaftSpec(
            overallLengthMm = 0f,
            liners = listOf(
                liner(id = "A", start = 0f, length = 20f),
                liner(id = "B", start = 80f, length = 10f)
            )
        )
        val moved = initial.copy(
            liners = listOf(
                initial.liners[0],
                initial.liners[1].copy(startFromAftMm = 50f, endMmPhysical = 60f)
            )
        )

        val auto = autoBodies(moved)

        assertEquals(1, auto.size)
        assertAuto(auto[0], start = 20f, end = 50f)
    }

    @Test
    fun auto_bodies_merge_when_middle_liner_removed() {
        val initial = ShaftSpec(
            overallLengthMm = 0f,
            liners = listOf(
                liner(id = "A", start = 0f, length = 20f),
                liner(id = "B", start = 40f, length = 20f),
                liner(id = "C", start = 80f, length = 10f)
            )
        )
        val withoutMiddle = initial.copy(liners = listOf(initial.liners[0], initial.liners[2]))

        val auto = autoBodies(withoutMiddle)

        assertEquals(1, auto.size)
        assertAuto(auto[0], start = 20f, end = 80f)
    }

    @Test
    fun auto_bodies_never_overlap_explicit_components() {
        val spec = ShaftSpec(
            overallLengthMm = 0f,
            liners = listOf(
                liner(id = "A", start = 0f, length = 15f),
                liner(id = "B", start = 40f, length = 10f),
                liner(id = "C", start = 75f, length = 5f)
            )
        )

        val explicit = resolveExplicitComponents(spec)
            .sortedBy { it.startMmPhysical }
        val auto = autoBodies(spec)

        assertEquals(explicit.size - 1, auto.size)

        auto.forEachIndexed { index, body ->
            val left = explicit[index]
            val right = explicit[index + 1]
            assertAuto(body, start = left.endMmPhysical, end = right.startMmPhysical)
        }
    }

    @Test
    fun explicit_components_remain_unchanged_across_auto_body_lifecycle() {
        val spec = ShaftSpec(
            overallLengthMm = 0f,
            liners = listOf(
                liner(id = "A", start = 0f, length = 20f),
                liner(id = "B", start = 60f, length = 10f)
            )
        )
        val snapshot = spec.liners.map { it.copy() }

        val modified = spec.copy(
            liners = listOf(
                spec.liners[0].copy(startFromAftMm = 10f, endMmPhysical = 30f),
                spec.liners[1]
            )
        )

        autoBodies(modified)

        assertEquals(snapshot[1], modified.liners[1])
        assertEquals(snapshot[1], spec.liners[1])
        assertEquals(snapshot[0], spec.liners[0])
    }

    @Test
    fun forward_assembly_lock_thread_and_taper() {
        val oal = 120f
        val thread = Threads(
            id = "TH",
            startFromAftMm = 0f,
            lengthMm = 10f,
            majorDiaMm = 30f,
            pitchMm = 1f,
            authoredReference = AuthoredReference.FWD,
            authoredStartFromFwdMm = 0f
        )
        val taper = Taper(
            id = "TP",
            startFromAftMm = 0f,
            lengthMm = 20f,
            startDiaMm = 30f,
            endDiaMm = 20f,
            authoredReference = AuthoredReference.FWD,
            authoredStartFromFwdMm = 10f
        )
        val liner = liner(id = "LN", start = 60f, length = 10f)

        val spec = ShaftSpec(
            overallLengthMm = oal,
            threads = listOf(thread),
            tapers = listOf(taper),
            liners = listOf(liner)
        )

        val baseResolved = resolveExplicitComponents(spec)
        val baseThread = baseResolved.filterIsInstance<ResolvedThread>().single { it.id == "TH" }
        val baseTaper = baseResolved.filterIsInstance<ResolvedTaper>().single { it.id == "TP" }
        val baseAuto = findAutoBody(
            autoBodies = deriveAutoBodies(oal, baseResolved),
            start = 70f,
            end = 110f
        )

        assertEquals(oal, baseThread.endMmPhysical, 1e-4f)
        assertEquals(oal - 10f, baseThread.startMmPhysical, 1e-4f)
        assertEquals(20f, baseTaper.endMmPhysical, 1e-4f)
        assertEquals(0f, baseTaper.startMmPhysical, 1e-4f)

        val modified = spec.copy(
            liners = listOf(spec.liners[0].copy(startFromAftMm = 40f, lengthMm = 20f, endMmPhysical = 60f))
        )

        val resolved = resolveExplicitComponents(modified)
        val threadResolved = resolved.filterIsInstance<ResolvedThread>().single { it.id == "TH" }
        val taperResolved = resolved.filterIsInstance<ResolvedTaper>().single { it.id == "TP" }
        val auto = findAutoBody(
            autoBodies = deriveAutoBodies(oal, resolved),
            start = 60f,
            end = 110f
        )

        assertEquals(thread, spec.threads[0])
        assertEquals(taper, spec.tapers[0])
        assertEquals(oal, threadResolved.endMmPhysical, 1e-4f)
        assertEquals(oal - 10f, threadResolved.startMmPhysical, 1e-4f)
        assertEquals(20f, taperResolved.endMmPhysical, 1e-4f)
        assertEquals(0f, taperResolved.startMmPhysical, 1e-4f)
        assertEquals(baseAuto.lengthMm + 10f, auto.lengthMm, 1e-4f)
    }

    @Test
    fun explicit_taper_positions_are_authoritative_over_auto_bodies() {
        val oal = 120f
        val taper = Taper(
            id = "TP",
            startFromAftMm = 40f,
            lengthMm = 20f,
            startDiaMm = 50f,
            endDiaMm = 40f
        )
        val spec = ShaftSpec(
            overallLengthMm = oal,
            tapers = listOf(taper)
        )

        val resolved = resolveComponents(spec)
        val resolvedTaper = resolved.filterIsInstance<ResolvedTaper>().single { it.id == "TP" }
        val autoBodies = resolved
            .filterIsInstance<ResolvedBody>()
            .filter { it.source == ResolvedComponentSource.AUTO }

        assertEquals(40f, resolvedTaper.startMmPhysical, 1e-4f)
        assertEquals(60f, resolvedTaper.endMmPhysical, 1e-4f)
        assertEquals(2, autoBodies.size)
        assertTrue(
            autoBodies.any { approx(it.startMmPhysical, 0f) && approx(it.endMmPhysical, 40f) }
        )
        assertTrue(
            autoBodies.any { approx(it.startMmPhysical, 60f) && approx(it.endMmPhysical, oal) }
        )
    }

    @Test
    fun explicit_thread_positions_are_authoritative_over_auto_bodies() {
        val oal = 120f
        val thread = Threads(
            id = "TH",
            startFromAftMm = 30f,
            lengthMm = 15f,
            majorDiaMm = 35f,
            pitchMm = 1.5f
        )
        val spec = ShaftSpec(
            overallLengthMm = oal,
            threads = listOf(thread)
        )

        val resolved = resolveComponents(spec)
        val resolvedThread = resolved.filterIsInstance<ResolvedThread>().single { it.id == "TH" }
        val autoBodies = resolved
            .filterIsInstance<ResolvedBody>()
            .filter { it.source == ResolvedComponentSource.AUTO }

        assertEquals(30f, resolvedThread.startMmPhysical, 1e-4f)
        assertEquals(45f, resolvedThread.endMmPhysical, 1e-4f)
        assertEquals(2, autoBodies.size)
        assertTrue(
            autoBodies.any { approx(it.startMmPhysical, 0f) && approx(it.endMmPhysical, 30f) }
        )
        assertTrue(
            autoBodies.any { approx(it.startMmPhysical, 45f) && approx(it.endMmPhysical, oal) }
        )
    }

    @Test
    fun explicit_liner_positions_are_authoritative_over_auto_bodies() {
        val oal = 120f
        val liner = Liner(
            id = "LN",
            startFromAftMm = 50f,
            lengthMm = 10f,
            odMm = 45f,
            authoredReference = LinerAuthoredReference.AFT,
            endMmPhysical = 60f
        )
        val spec = ShaftSpec(
            overallLengthMm = oal,
            liners = listOf(liner)
        )

        val resolved = resolveComponents(spec)
        val resolvedLiner = resolved.filterIsInstance<ResolvedLiner>().single { it.id == "LN" }
        val autoBodies = resolved
            .filterIsInstance<ResolvedBody>()
            .filter { it.source == ResolvedComponentSource.AUTO }

        assertEquals(50f, resolvedLiner.startMmPhysical, 1e-4f)
        assertEquals(60f, resolvedLiner.endMmPhysical, 1e-4f)
        assertEquals(2, autoBodies.size)
        assertTrue(
            autoBodies.any { approx(it.startMmPhysical, 0f) && approx(it.endMmPhysical, 50f) }
        )
        assertTrue(
            autoBodies.any { approx(it.startMmPhysical, 60f) && approx(it.endMmPhysical, oal) }
        )
    }

    @Test
    fun auto_bodies_rederived_after_taper_commit() {
        val oal = 200f
        val body = Body(id = "B1", startFromAftMm = 0f, lengthMm = 40f, diaMm = 50f)
        val baseSpec = ShaftSpec(overallLengthMm = oal, bodies = listOf(body))

        val baseResolved = resolveComponents(baseSpec)
        val baseAuto = baseResolved.filterIsInstance<ResolvedBody>()
            .single { it.source == ResolvedComponentSource.AUTO }

        assertEquals(body.id, baseAuto.autoBodyKey?.leftId)
        assertEquals(null, baseAuto.autoBodyKey?.rightId)

        val taper = Taper(
            id = "T1",
            startFromAftMm = 180f,
            lengthMm = 20f,
            startDiaMm = 50f,
            endDiaMm = 40f,
            orientation = TaperOrientation.FWD
        )
        val updatedSpec = baseSpec.copy(tapers = listOf(taper))

        val resolved = resolveComponents(updatedSpec)
        val resolvedTaper = resolved.filterIsInstance<ResolvedTaper>().single { it.id == "T1" }
        val autoBodies = resolved.filterIsInstance<ResolvedBody>()
            .filter { it.source == ResolvedComponentSource.AUTO }

        assertEquals(180f, resolvedTaper.startMmPhysical, 1e-4f)
        assertEquals(1, autoBodies.size)
        assertEquals("T1", autoBodies.single().autoBodyKey?.rightId)
    }

    @Test
    fun excluded_thread_does_not_reduce_auto_body_fill() {
        val oal = 100f
        val thread = Threads(
            id = "TH",
            startFromAftMm = 90f,
            lengthMm = 10f,
            majorDiaMm = 30f,
            pitchMm = 1f,
            excludeFromOAL = true
        )
        val spec = ShaftSpec(overallLengthMm = oal, threads = listOf(thread))

        val explicit = resolveExplicitComponents(spec)
        val auto = deriveAutoBodies(overallLengthMm = oal, explicitComponents = explicit)
            .filterIsInstance<ResolvedBody>()
            .filter { it.source == ResolvedComponentSource.AUTO }

        assertEquals(1, auto.size)
        assertAuto(auto[0], start = 0f, end = oal)
    }

    @Test
    fun fwd_taper_near_end_keeps_authored_start() {
        val oal = 136f
        val body = Body(id = "B1", startFromAftMm = 0f, lengthMm = 20f, diaMm = 50f)
        val taper = Taper(
            id = "T1",
            startFromAftMm = 124f,
            lengthMm = 12f,
            startDiaMm = 50f,
            endDiaMm = 40f,
            orientation = TaperOrientation.FWD
        )
        val spec = ShaftSpec(overallLengthMm = oal, bodies = listOf(body), tapers = listOf(taper))

        val resolved = resolveComponents(spec)
        val resolvedTaper = resolved.filterIsInstance<ResolvedTaper>().single { it.id == "T1" }

        assertEquals(124f, resolvedTaper.startMmPhysical, 1e-4f)
    }

    @Test
    fun fwd_taper_at_end_preserves_span_and_set_let_with_auto_body() {
        val oal = 136f
        val taper = Taper(
            id = "T1",
            startFromAftMm = 124f,
            lengthMm = 12f,
            startDiaMm = 50f,
            endDiaMm = 40f,
            orientation = TaperOrientation.FWD,
            authoredReference = AuthoredReference.FWD,
            authoredStartFromFwdMm = 0f
        )
        val spec = ShaftSpec(overallLengthMm = oal, tapers = listOf(taper))

        val resolved = resolveComponents(spec)
        val resolvedTaper = resolved.filterIsInstance<ResolvedTaper>().single { it.id == "T1" }
        val autoBodies = resolved.filterIsInstance<ResolvedBody>()
            .filter { it.source == ResolvedComponentSource.AUTO }

        assertEquals(124f, resolvedTaper.startMmPhysical, 1e-4f)
        assertEquals(136f, resolvedTaper.endMmPhysical, 1e-4f)
        assertEquals(50f, resolvedTaper.startDiaMm, 1e-4f)
        assertEquals(40f, resolvedTaper.endDiaMm, 1e-4f)
        assertTrue(autoBodies.any { approx(it.startMmPhysical, 0f) && approx(it.endMmPhysical, 124f) })
    }

    @Test
    fun fwd_taper_at_end_preserved_after_undo_redo() {
        val oal = 136f
        val taper = Taper(
            id = "T1",
            startFromAftMm = 124f,
            lengthMm = 12f,
            startDiaMm = 50f,
            endDiaMm = 40f,
            orientation = TaperOrientation.FWD
        )
        val baseSpec = ShaftSpec(overallLengthMm = oal, tapers = listOf(taper))
        val withBody = baseSpec.copy(
            bodies = listOf(Body(id = "B1", startFromAftMm = 0f, lengthMm = 20f, diaMm = 50f))
        )

        val baseResolved = resolveComponents(baseSpec)
        val redoResolved = resolveComponents(withBody)
        val undoResolved = resolveComponents(baseSpec)
        val redoAgain = resolveComponents(withBody)

        assertAuthoredTaper(baseResolved, taper)
        assertAuthoredTaper(redoResolved, taper)
        assertAuthoredTaper(undoResolved, taper)
        assertAuthoredTaper(redoAgain, taper)
    }

    @Test
    fun aft_taper_at_end_preserves_span_and_set_let_with_auto_body() {
        val oal = 136f
        val taper = Taper(
            id = "T1",
            startFromAftMm = 124f,
            lengthMm = 12f,
            startDiaMm = 50f,
            endDiaMm = 40f,
            orientation = TaperOrientation.AFT
        )
        val spec = ShaftSpec(overallLengthMm = oal, tapers = listOf(taper))

        val resolved = resolveComponents(spec)
        val resolvedTaper = resolved.filterIsInstance<ResolvedTaper>().single { it.id == "T1" }
        val autoBodies = resolved.filterIsInstance<ResolvedBody>()
            .filter { it.source == ResolvedComponentSource.AUTO }

        assertEquals(124f, resolvedTaper.startMmPhysical, 1e-4f)
        assertEquals(136f, resolvedTaper.endMmPhysical, 1e-4f)
        assertEquals(50f, resolvedTaper.startDiaMm, 1e-4f)
        assertEquals(40f, resolvedTaper.endDiaMm, 1e-4f)
        assertTrue(autoBodies.any { approx(it.startMmPhysical, 0f) && approx(it.endMmPhysical, 124f) })
    }

    private fun autoBodies(spec: ShaftSpec): List<ResolvedBody> =
        deriveAutoBodies(
            overallLengthMm = 0f,
            explicitComponents = resolveExplicitComponents(spec)
        )
            .filterIsInstance<ResolvedBody>()
            .filter { it.source == ResolvedComponentSource.AUTO }

    private fun findAutoBody(autoBodies: List<ResolvedComponent>, start: Float, end: Float): ResolvedBody {
        return autoBodies
            .filterIsInstance<ResolvedBody>()
            .first {
                it.source == ResolvedComponentSource.AUTO &&
                    approx(it.startMmPhysical, start) &&
                    approx(it.endMmPhysical, end)
            }
    }

    private fun assertAuto(body: ResolvedBody, start: Float, end: Float) {
        assertEquals(ResolvedComponentSource.AUTO, body.source)
        assertEquals(start, body.startMmPhysical, 1e-4f)
        assertEquals(end, body.endMmPhysical, 1e-4f)
        assertEquals(end - start, body.lengthMm, 1e-4f)
        assertTrue("Auto body length must be non-negative", body.lengthMm >= 0f)
    }

    private fun assertAuthoredTaper(resolved: List<ResolvedComponent>, taper: Taper) {
        val resolvedTaper = resolved.filterIsInstance<ResolvedTaper>().single { it.id == taper.id }
        assertEquals(taper.startFromAftMm, resolvedTaper.startMmPhysical, 1e-4f)
        assertEquals(taper.startFromAftMm + taper.lengthMm, resolvedTaper.endMmPhysical, 1e-4f)
        assertEquals(taper.startDiaMm, resolvedTaper.startDiaMm, 1e-4f)
        assertEquals(taper.endDiaMm, resolvedTaper.endDiaMm, 1e-4f)
    }

    private fun liner(
        id: String,
        start: Float,
        length: Float,
        odMm: Float = 50f,
        ref: LinerAuthoredReference = LinerAuthoredReference.AFT
    ): Liner = Liner(
        id = id,
        startFromAftMm = start,
        lengthMm = length,
        odMm = odMm,
        authoredReference = ref,
        endMmPhysical = start + length
    )

    private fun approx(actual: Float, expected: Float, eps: Float = 1e-4f): Boolean =
        abs(actual - expected) <= eps
}
