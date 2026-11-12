package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.AutoDiaCallout
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.showsAutoSectionDia
import com.android.shaftschematic.model.withAutoSectionShowDia
import com.android.shaftschematic.pdf.bodyForPdf
import com.android.shaftschematic.doc.ShaftDocCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ShaftSpec.autoDiaCallouts` — each bare-shaft section shows or hides its Ø callout by itself
 * (on-device report: turning one section on or off turned them all on or off), staying an
 * auto section with a derived extent.
 */
class AutoSectionDiaCalloutTest {

    private val separator = Liner(id = "l1", startFromAftMm = 400f, lengthMm = 200f, odMm = 200f)

    /** OAL 1000 with a liner at 400..600 — auto sections 0..400 and 600..1000. */
    private fun twoSections(showAll: Boolean = false, vararg callouts: AutoDiaCallout) = ShaftSpec(
        overallLengthMm = 1000f,
        liners = listOf(separator),
        autoBodyDiaMm = 150f,
        showAutoBodyDia = showAll,
        autoDiaCallouts = callouts.toList(),
    )

    private fun shownAutoSpans(spec: ShaftSpec): List<Pair<Float, Float>> =
        resolveComponents(spec)
            .filterIsInstance<ResolvedBody>()
            .filter { it.source == ResolvedComponentSource.AUTO }
            .filter { spec.bodyForPdf(it).showDiaOnDrawing }
            .map { it.startMmPhysical to it.endMmPhysical }

    @Test
    fun `showing one section leaves the other hidden`() {
        val spec = twoSections().withAutoSectionShowDia(0f, 400f, true)

        assertEquals(listOf(0f to 400f), shownAutoSpans(spec))
    }

    @Test
    fun `hiding one section of a show-all document leaves the other shown`() {
        val spec = twoSections(showAll = true).withAutoSectionShowDia(600f, 1000f, false)

        assertTrue(spec.showAutoBodyDia)
        assertEquals(listOf(0f to 400f), shownAutoSpans(spec))
    }

    @Test
    fun `a section set back to the default stores no entry`() {
        val shown = twoSections().withAutoSectionShowDia(0f, 400f, true)
        val back = shown.withAutoSectionShowDia(0f, 400f, false)

        assertEquals(1, shown.autoDiaCallouts.size)
        assertTrue(back.autoDiaCallouts.isEmpty())
    }

    @Test
    fun `a no-op set returns the same spec`() {
        val spec = twoSections()
        assertSame(spec, spec.withAutoSectionShowDia(0f, 400f, false))
    }

    @Test
    fun `the section keeps its callout when its extent re-derives`() {
        // The liner moves FWD: the AFT section grows from 0..400 to 0..450 and is still auto.
        val spec = twoSections().withAutoSectionShowDia(0f, 400f, true)
        val moved = spec.copy(liners = listOf(separator.copy(startFromAftMm = 450f)))

        assertEquals(listOf(0f to 450f), shownAutoSpans(moved))
    }

    @Test
    fun `the aft-most anchor in a merged span wins`() {
        val spec = twoSections(
            showAll = false,
            AutoDiaCallout(anchorMm = 800f, show = true),
            AutoDiaCallout(anchorMm = 200f, show = false),
        ).copy(liners = emptyList())

        assertFalse(spec.showsAutoSectionDia(0f, 1000f))
    }

    @Test
    fun `removing the liner AFT of a section drops that section's callout entry`() {
        val before = twoSections().withAutoSectionShowDia(600f, 1000f, true)
        val after = before.copy(liners = emptyList())

        assertTrue(after.withAutoDiaOverridesSurviving(before).autoDiaCallouts.isEmpty())
    }

    @Test
    fun `removing the liner FWD of a section keeps that section's callout entry`() {
        val before = twoSections().withAutoSectionShowDia(0f, 400f, true)
        val after = before.copy(liners = emptyList())

        assertEquals(before.autoDiaCallouts, after.withAutoDiaOverridesSurviving(before).autoDiaCallouts)
    }

    @Test
    fun `a body made on exactly a shown section is asked to show`() {
        val spec = twoSections().withAutoSectionShowDia(0f, 400f, true)

        assertTrue(spec.autoSectionShowsDiaOn(0f, 400f))
        assertFalse(spec.autoSectionShowsDiaOn(600f, 1000f))
        // Not a whole section: an ordinary add starts hidden.
        assertFalse(spec.autoSectionShowsDiaOn(0f, 300f))
    }

    @Test
    fun `a demoted body hands its callout to the section that replaces it`() {
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 150f, showDiaOnDrawing = true)
        val before = twoSections().copy(bodies = listOf(body))
        val after = before.copy(bodies = emptyList())
            .withAutoDiaOverridesSurviving(before)
            .withDemotedBodyDiaCallout(body)

        assertEquals(listOf(0f to 400f), shownAutoSpans(after))
    }

    @Test
    fun `a demoted hidden body leaves a hidden section in a show-all document`() {
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 150f, showDiaOnDrawing = false)
        val before = twoSections(showAll = true).copy(bodies = listOf(body))
        val after = before.copy(bodies = emptyList())
            .withAutoDiaOverridesSurviving(before)
            .withDemotedBodyDiaCallout(body)

        assertEquals(listOf(600f to 1000f), shownAutoSpans(after))
    }

    @Test
    fun `entries round-trip and an older document decodes with none`() {
        val spec = twoSections().withAutoSectionShowDia(0f, 400f, true)
        val decoded = ShaftDocCodec.decode(
            ShaftDocCodec.encodeV1(ShaftDocCodec.ShaftDocV1(spec = spec))
        ).spec
        assertEquals(spec.autoDiaCallouts, decoded.autoDiaCallouts)

        val legacy = ShaftDocCodec.decode(
            ShaftDocCodec.encodeV1(ShaftDocCodec.ShaftDocV1(spec = twoSections(showAll = true)))
                .replace(Regex(""","autoDiaCallouts":\[[^\]]*]"""), "")
        ).spec
        assertTrue(legacy.autoDiaCallouts.isEmpty())
        assertEquals(2, shownAutoSpans(legacy).size)
    }
}
