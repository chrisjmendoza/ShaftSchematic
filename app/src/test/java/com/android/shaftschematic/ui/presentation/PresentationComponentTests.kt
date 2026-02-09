package com.android.shaftschematic.ui.presentation

import com.android.shaftschematic.model.AutoBodyKey
import com.android.shaftschematic.model.AutoBodyOverride
import com.android.shaftschematic.model.ThreadAttachment
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.ResolvedComponentType
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.ResolvedThread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationComponentTests {

    @Test
    fun split_explicit_body_fragments_grouped_into_single_component() {
        val parts = listOf(
            explicitBodySegment(id = "B1::seg:0", authoredId = "B1", start = 0f, end = 10f),
            explicitBodySegment(id = "B1::seg:1", authoredId = "B1", start = 20f, end = 30f)
        )

        val result = PresentationComponent.fromResolved(parts, emptyMap(), overallLengthMm = 1000f)

        assertEquals(1, result.size)
        val body = result.single() as PresentationComponent.Body
        assertEquals("B1", body.id)
        assertEquals(2, body.resolvedParts.size)
        assertTrue(body.editable)
    }

    @Test
    fun two_auto_bodies_separated_by_liner_form_two_body_components() {
        val key1 = AutoBodyKey(leftId = "B1", rightId = "L1")
        val key2 = AutoBodyKey(leftId = "L1", rightId = "B2")
        val auto1 = autoBodyResolved(key1, start = 0f, end = 10f)
        val liner = ResolvedLiner(
            id = "L1",
            authoredSourceId = "L1",
            type = ResolvedComponentType.LINER,
            source = ResolvedComponentSource.EXPLICIT,
            startMmPhysical = 10f,
            endMmPhysical = 20f,
            odMm = 45f
        )
        val auto2 = autoBodyResolved(key2, start = 20f, end = 30f)

        val result = PresentationComponent.fromResolved(listOf(auto1, liner, auto2), emptyMap(), overallLengthMm = 1000f)
        val bodies = result.filterIsInstance<PresentationComponent.Body>()

        assertEquals(3, result.size)
        assertEquals(2, bodies.size)
        assertEquals(setOf(key1.stableId(), key2.stableId()), bodies.map { it.id }.toSet())
    }

    @Test
    fun auto_body_with_override_is_editable() {
        val key = AutoBodyKey(leftId = "B1", rightId = "B2")
        val auto = autoBodyResolved(key, start = 0f, end = 10f)
        val overrides = mapOf(key.stableId() to AutoBodyOverride(label = "Editable"))

        val result = PresentationComponent.fromResolved(listOf(auto), overrides, overallLengthMm = 1000f)
        val body = result.single() as PresentationComponent.Body

        assertTrue(body.editable)
        assertEquals("Editable", body.autoBodyOverride?.label)
    }

    @Test
    fun auto_body_without_override_is_not_editable() {
        val key = AutoBodyKey(leftId = "B1", rightId = "B2")
        val auto = autoBodyResolved(key, start = 0f, end = 10f)

        val result = PresentationComponent.fromResolved(listOf(auto), emptyMap(), overallLengthMm = 1000f)
        val body = result.single() as PresentationComponent.Body

        assertFalse(body.editable)
    }

    @Test
    fun draft_component_is_editable_and_flagged_as_draft() {
        val taper = ResolvedTaper(
            id = "T1",
            authoredSourceId = "T1",
            type = ResolvedComponentType.TAPER,
            source = ResolvedComponentSource.DRAFT,
            startMmPhysical = 0f,
            endMmPhysical = 10f,
            startDiaMm = 50f,
            endDiaMm = 40f
        )

        val result = PresentationComponent.fromResolved(listOf(taper), emptyMap(), overallLengthMm = 1000f)
        val comp = result.single() as PresentationComponent.Taper

        assertEquals(PresentationComponentSource.DRAFT, comp.source)
        assertTrue(comp.editable)
    }

    @Test
    fun ordering_is_stable_across_input_order_changes() {
        val linerA = ResolvedLiner(
            id = "L1",
            authoredSourceId = "L1",
            type = ResolvedComponentType.LINER,
            source = ResolvedComponentSource.EXPLICIT,
            startMmPhysical = 0f,
            endMmPhysical = 10f,
            odMm = 40f
        )
        val linerB = ResolvedLiner(
            id = "L2",
            authoredSourceId = "L2",
            type = ResolvedComponentType.LINER,
            source = ResolvedComponentSource.EXPLICIT,
            startMmPhysical = 50f,
            endMmPhysical = 60f,
            odMm = 42f
        )

        val resultA = PresentationComponent.fromResolved(listOf(linerA, linerB), emptyMap(), overallLengthMm = 1000f)
        val resultB = PresentationComponent.fromResolved(listOf(linerB, linerA), emptyMap(), overallLengthMm = 1000f)

        assertEquals(listOf("L1", "L2"), resultA.map { it.id })
        assertEquals(resultA.map { it.id }, resultB.map { it.id })
    }

    @Test
    fun auto_body_missing_key_throws() {
        val auto = ResolvedBody(
            id = "auto_body_missing",
            authoredSourceId = "auto_body_missing",
            type = ResolvedComponentType.BODY_AUTO,
            source = ResolvedComponentSource.AUTO,
            startMmPhysical = 0f,
            endMmPhysical = 10f,
            diaMm = 40f,
            autoBodyKey = null
        )

        assertThrows(IllegalArgumentException::class.java) {
            PresentationComponent.fromResolved(listOf(auto), emptyMap(), overallLengthMm = 1000f)
        }
    }

    @Test
    fun group_mixing_kind_throws() {
        val body = explicitBodySegment(id = "B1", authoredId = "X", start = 0f, end = 10f)
        val liner = ResolvedLiner(
            id = "L1",
            authoredSourceId = "X",
            type = ResolvedComponentType.LINER,
            source = ResolvedComponentSource.EXPLICIT,
            startMmPhysical = 0f,
            endMmPhysical = 10f,
            odMm = 45f
        )

        assertThrows(IllegalArgumentException::class.java) {
            PresentationComponent.fromResolved(listOf(body, liner), emptyMap(), overallLengthMm = 1000f)
        }
    }

    @Test
    fun ordering_tie_is_deterministic() {
        val body = explicitBodySegment(id = "B1", authoredId = "B1", start = 0f, end = 10f)
        val liner = ResolvedLiner(
            id = "L1",
            authoredSourceId = "L1",
            type = ResolvedComponentType.LINER,
            source = ResolvedComponentSource.EXPLICIT,
            startMmPhysical = 0f,
            endMmPhysical = 10f,
            odMm = 40f
        )
        val taper = ResolvedTaper(
            id = "T1",
            authoredSourceId = "T1",
            type = ResolvedComponentType.TAPER,
            source = ResolvedComponentSource.EXPLICIT,
            startMmPhysical = 0f,
            endMmPhysical = 10f,
            startDiaMm = 50f,
            endDiaMm = 40f
        )

        val resultA = PresentationComponent.fromResolved(listOf(liner, body, taper), emptyMap(), overallLengthMm = 1000f)
        val resultB = PresentationComponent.fromResolved(listOf(taper, liner, body), emptyMap(), overallLengthMm = 1000f)

        assertEquals(listOf("B1", "T1", "L1"), resultA.map { it.id })
        assertEquals(resultA.map { it.id }, resultB.map { it.id })
    }

    @Test
    fun excluded_threads_are_grouped_at_ends_and_auto_bodies_sort_after_explicit() {
        val aftExcluded = ResolvedThread(
            id = "TH-AFT",
            authoredSourceId = "TH-AFT",
            type = ResolvedComponentType.THREAD,
            source = ResolvedComponentSource.EXPLICIT,
            startMmPhysical = 0f,
            endMmPhysical = 10f,
            majorDiaMm = 40f,
            pitchMm = 2f,
            excludeFromOal = true,
            endAttachment = ThreadAttachment.AFT
        )
        val fwdExcluded = ResolvedThread(
            id = "TH-FWD",
            authoredSourceId = "TH-FWD",
            type = ResolvedComponentType.THREAD,
            source = ResolvedComponentSource.EXPLICIT,
            startMmPhysical = 990f,
            endMmPhysical = 1000f,
            majorDiaMm = 40f,
            pitchMm = 2f,
            excludeFromOal = true,
            endAttachment = ThreadAttachment.FWD
        )
        val bodyExplicit = explicitBodySegment(id = "B1::seg:0", authoredId = "B1", start = 0f, end = 100f)
        val bodyAuto = autoBodyResolved(AutoBodyKey(leftId = "B1", rightId = "L1"), start = 0f, end = 80f)
        val threadIncluded = ResolvedThread(
            id = "TH-IN",
            authoredSourceId = "TH-IN",
            type = ResolvedComponentType.THREAD,
            source = ResolvedComponentSource.EXPLICIT,
            startMmPhysical = 50f,
            endMmPhysical = 60f,
            majorDiaMm = 40f,
            pitchMm = 2f,
            excludeFromOal = false,
            endAttachment = null
        )
        val taper = ResolvedTaper(
            id = "T1",
            authoredSourceId = "T1",
            type = ResolvedComponentType.TAPER,
            source = ResolvedComponentSource.EXPLICIT,
            startMmPhysical = 200f,
            endMmPhysical = 240f,
            startDiaMm = 50f,
            endDiaMm = 40f
        )

        val result = PresentationComponent.fromResolved(
            listOf(fwdExcluded, taper, threadIncluded, bodyAuto, aftExcluded, bodyExplicit),
            emptyMap(),
            overallLengthMm = 1000f
        )

        assertEquals(listOf("TH-AFT", "B1", AutoBodyKey(leftId = "B1", rightId = "L1").stableId(), "TH-IN", "T1", "TH-FWD"), result.map { it.id })
    }

    private fun explicitBodySegment(
        id: String,
        authoredId: String,
        start: Float,
        end: Float,
    ): ResolvedBody = ResolvedBody(
        id = id,
        authoredSourceId = authoredId,
        type = ResolvedComponentType.BODY,
        source = ResolvedComponentSource.EXPLICIT,
        startMmPhysical = start,
        endMmPhysical = end,
        diaMm = 50f
    )

    private fun autoBodyResolved(
        key: AutoBodyKey,
        start: Float,
        end: Float,
    ): ResolvedBody = ResolvedBody(
        id = "auto_body_${key.stableId()}",
        authoredSourceId = "auto_body_${key.stableId()}",
        type = ResolvedComponentType.BODY_AUTO,
        source = ResolvedComponentSource.AUTO,
        startMmPhysical = start,
        endMmPhysical = end,
        diaMm = 40f,
        autoBodyKey = key
    )
}
