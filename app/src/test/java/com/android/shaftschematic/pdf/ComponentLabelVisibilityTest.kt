package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-component name-label visibility (`showLabelOnDrawing`) as the schematic's label pass sees
 * it. The global Settings switch and the per-sheet export option gate the whole pass at the call
 * site, so only the per-component filter is exercised here.
 */
class ComponentLabelVisibilityTest {

    private fun spec(
        body: Boolean = true,
        taper: Boolean = true,
        thread: Boolean = true,
        liner: Boolean = true,
    ) = ShaftSpec(
        overallLengthMm = 2000f,
        bodies = listOf(
            Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 120f,
                label = "Coupling end", showLabelOnDrawing = body),
        ),
        tapers = listOf(
            Taper(id = "t1", startFromAftMm = 400f, lengthMm = 300f, startDiaMm = 100f, endDiaMm = 120f,
                label = "Prop taper", showLabelOnDrawing = taper),
        ),
        threads = listOf(
            Threads(id = "th1", startFromAftMm = 1700f, lengthMm = 100f, majorDiaMm = 90f,
                label = "Nut thread", showLabelOnDrawing = thread),
        ),
        liners = listOf(
            Liner(id = "l1", startFromAftMm = 800f, lengthMm = 200f, odMm = 150f,
                label = "Fwd sleeve", showLabelOnDrawing = liner),
        ),
    )

    private fun texts(s: ShaftSpec) = componentLabelSpans(s).map { it.text }

    @Test
    fun `every component labelled by default`() {
        assertEquals(
            listOf("Coupling end", "Prop taper", "Nut thread", "Fwd sleeve"),
            texts(spec())
        )
    }

    @Test
    fun `a hidden component drops its label and the others keep theirs`() {
        assertEquals(listOf("Prop taper", "Nut thread", "Fwd sleeve"), texts(spec(body = false)))
        assertEquals(listOf("Coupling end", "Nut thread", "Fwd sleeve"), texts(spec(taper = false)))
        assertEquals(listOf("Coupling end", "Prop taper", "Fwd sleeve"), texts(spec(thread = false)))
        assertEquals(listOf("Coupling end", "Prop taper", "Nut thread"), texts(spec(liner = false)))
    }

    @Test
    fun `hiding every component leaves nothing to draw`() {
        assertTrue(componentLabelSpans(spec(false, false, false, false)).isEmpty())
    }

    @Test
    fun `a label spans its component`() {
        val span = componentLabelSpans(spec()).single { it.text == "Prop taper" }
        assertEquals(400f, span.startMm, 0.001f)
        assertEquals(700f, span.endMm, 0.001f)
    }

    @Test
    fun `hiding one body does not renumber the derived names of the others`() {
        // Fallback numbering runs over the full AFT→FWD list, so #2 stays #2 with #1 hidden.
        val s = ShaftSpec(
            overallLengthMm = 1200f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 120f, showLabelOnDrawing = false),
                Body(id = "b2", startFromAftMm = 600f, lengthMm = 400f, diaMm = 120f),
            ),
        )
        val names = texts(s)
        assertEquals(1, names.size)
        assertEquals("Body #2", names.single())
        assertFalse(names.contains("Body #1"))
    }

    @Test
    fun `a blank label on a shown component contributes nothing`() {
        // An empty custom label falls back to the derived title, so the entry survives; only a
        // component whose derived title is itself blank can drop out.
        val s = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 120f, label = "   ")),
        )
        assertEquals(listOf("Body #1"), texts(s))
    }
}
