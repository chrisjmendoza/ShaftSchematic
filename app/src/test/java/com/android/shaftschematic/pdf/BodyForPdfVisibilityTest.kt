package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.ResolvedComponentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The resolved→drawable mapping must carry each body's authored Ø-callout visibility, keyed
 * off the BASE id: a body split by a liner or taper draws as several fragments
 * (`"<id>#2"`, …), and hiding the body has to hide every one of them.
 */
class BodyForPdfVisibilityTest {

    private fun resolved(
        id: String,
        startMm: Float,
        endMm: Float,
        diaMm: Float = 127f,
        source: ResolvedComponentSource = ResolvedComponentSource.EXPLICIT,
    ) = ResolvedBody(
        id = id,
        type = if (source == ResolvedComponentSource.AUTO) ResolvedComponentType.BODY_AUTO
               else ResolvedComponentType.BODY,
        source = source,
        startMmPhysical = startMm,
        endMmPhysical = endMm,
        diaMm = diaMm,
    )

    @Test
    fun `explicit body carries its own visibility`() {
        val spec = ShaftSpec(bodies = listOf(Body(id = "b1", showDiaOnDrawing = false)))
        assertFalse(spec.bodyForPdf(resolved("b1", 0f, 500f)).showDiaOnDrawing)
    }

    @Test
    fun `every fragment of a hidden body is hidden`() {
        val spec = ShaftSpec(bodies = listOf(Body(id = "b1", showDiaOnDrawing = false)))
        val fragments = listOf(
            resolved("b1", 0f, 200f),
            resolved("b1#2", 300f, 500f),
            resolved("b1#3", 600f, 900f),
        )
        assertTrue(fragments.map { spec.bodyForPdf(it) }.none { it.showDiaOnDrawing })
    }

    @Test
    fun `every fragment of a shown body stays shown`() {
        val spec = ShaftSpec(bodies = listOf(Body(id = "b1", showDiaOnDrawing = true)))
        val fragments = listOf(resolved("b1", 0f, 200f), resolved("b1#2", 300f, 500f))
        assertTrue(fragments.map { spec.bodyForPdf(it) }.all { it.showDiaOnDrawing })
    }

    @Test
    fun `auto spans follow the single bare-shaft flag`() {
        val hidden = ShaftSpec(showAutoBodyDia = false)
        val shown = ShaftSpec(showAutoBodyDia = true)
        val auto = resolved("auto_body_0.000_500.000", 0f, 500f, source = ResolvedComponentSource.AUTO)

        assertFalse(hidden.bodyForPdf(auto).showDiaOnDrawing)
        assertTrue(shown.bodyForPdf(auto).showDiaOnDrawing)
    }

    @Test
    fun `an auto span is unaffected by an explicit body's flag`() {
        // The bare-shaft flag and a body's own flag are independent in both directions.
        val spec = ShaftSpec(
            bodies = listOf(Body(id = "b1", showDiaOnDrawing = false)),
            showAutoBodyDia = true,
        )
        val auto = resolved("auto_body_600.000_900.000", 600f, 900f, source = ResolvedComponentSource.AUTO)
        assertTrue(spec.bodyForPdf(auto).showDiaOnDrawing)
    }

    @Test
    fun `a resolved id with no stored match follows the hidden-by-default posture`() {
        assertFalse(ShaftSpec().bodyForPdf(resolved("gone", 0f, 100f)).showDiaOnDrawing)
    }

    @Test
    fun `geometry still comes from the resolved span`() {
        val spec = ShaftSpec(bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 900f, diaMm = 100f)))
        val mapped = spec.bodyForPdf(resolved("b1#2", 300f, 500f, diaMm = 127f))

        assertEquals(300f, mapped.startFromAftMm, 0.001f)
        assertEquals(200f, mapped.lengthMm, 0.001f)
        assertEquals(127f, mapped.diaMm, 0.001f)
    }
}
