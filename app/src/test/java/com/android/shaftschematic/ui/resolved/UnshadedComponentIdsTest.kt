package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which components give up the grey shade fill — the whole shade decision the two dimensioned
 * composers hand to their draw passes as ids.
 *
 * The decision has to be made off the RESOLVED list plus the stored spec, because
 * `ShaftSpec.bodyForPdf` drops both the source and the display flags when it maps a resolved
 * run to a drawable [com.android.shaftschematic.model.Body] — the ids are the only thing that
 * survives to the draw pass. That is also why the composers build the fill paint
 * unconditionally: a kind's checkbox turned off is expressed by naming every id, which leaves
 * room for one component to shade against it.
 */
class UnshadedComponentIdsTest {

    private fun run(id: String, source: ResolvedComponentSource, start: Float, end: Float) =
        ResolvedBody(
            id = id,
            type = if (source == ResolvedComponentSource.AUTO) {
                ResolvedComponentType.BODY_AUTO
            } else {
                ResolvedComponentType.BODY
            },
            source = source,
            startMmPhysical = start,
            endMmPhysical = end,
            diaMm = 152.4f,
        )

    private val resolved = listOf(
        run("auto_body_0_100", ResolvedComponentSource.AUTO, 0f, 100f),
        run("b1", ResolvedComponentSource.EXPLICIT, 100f, 400f),
        run("b1#2", ResolvedComponentSource.EXPLICIT, 500f, 700f),
        run("auto_body_700_900", ResolvedComponentSource.AUTO, 700f, 900f),
        ResolvedLiner(
            id = "ln1",
            startMmPhysical = 400f,
            endMmPhysical = 500f,
            odMm = 165f,
        ),
    )

    /** The stored bodies behind [resolved]: one explicit body (split into two runs). */
    private fun spec(shade: Boolean? = null) = ShaftSpec(
        overallLengthMm = 900f,
        bodies = listOf(
            Body(id = "b1", startFromAftMm = 100f, lengthMm = 600f, diaMm = 152.4f, shadeOnDrawing = shade),
        ),
        liners = listOf(Liner(id = "ln1", startFromAftMm = 400f, lengthMm = 100f, odMm = 165f)),
    )

    // ── Bodies ────────────────────────────────────────────────────────────────

    @Test
    fun `kind on with no overrides shades every run`() {
        assertEquals(
            emptySet<String>(),
            unshadedBodyRunIds(spec(), resolved, shadedBodies = true, shadeExplicitBodiesOnly = false),
        )
    }

    @Test
    fun `kind off with no overrides names every run`() {
        assertEquals(
            setOf("auto_body_0_100", "b1", "b1#2", "auto_body_700_900"),
            unshadedBodyRunIds(spec(), resolved, shadedBodies = false, shadeExplicitBodiesOnly = false),
        )
    }

    /** The narrowing subtracts AUTO runs only — an explicit body is authored by definition. */
    @Test
    fun `explicit-bodies-only bares just the auto runs`() {
        assertEquals(
            setOf("auto_body_0_100", "auto_body_700_900"),
            unshadedBodyRunIds(spec(), resolved, shadedBodies = true, shadeExplicitBodiesOnly = true),
        )
    }

    /** The on-device case: shade ONE named section without shading the whole drawing. */
    @Test
    fun `an explicit true shades that body with the kind off`() {
        val ids = unshadedBodyRunIds(
            spec(shade = true), resolved, shadedBodies = false, shadeExplicitBodiesOnly = false,
        )
        assertTrue("b1" !in ids)
        assertTrue("b1#2" !in ids)
        assertEquals(setOf("auto_body_0_100", "auto_body_700_900"), ids)
    }

    @Test
    fun `an explicit false bares that body with the kind on`() {
        assertEquals(
            setOf("b1", "b1#2"),
            unshadedBodyRunIds(
                spec(shade = false), resolved, shadedBodies = true, shadeExplicitBodiesOnly = false,
            ),
        )
    }

    /** Every fragment of a split body reads the SAME authored flag through its base id. */
    @Test
    fun `fragments resolve to the stored body's flag`() {
        val shown = unshadedBodyRunIds(
            spec(shade = true), resolved, shadedBodies = false, shadeExplicitBodiesOnly = false,
        )
        val hidden = unshadedBodyRunIds(
            spec(shade = false), resolved, shadedBodies = true, shadeExplicitBodiesOnly = false,
        )
        assertEquals("b1" in shown, "b1#2" in shown)
        assertEquals("b1" in hidden, "b1#2" in hidden)
    }

    /** A per-body override never reaches bare shaft: `shadeExplicitBodiesOnly` still rules AUTO runs. */
    @Test
    fun `auto runs follow the narrowing whatever a body was authored to do`() {
        val ids = unshadedBodyRunIds(
            spec(shade = true), resolved, shadedBodies = true, shadeExplicitBodiesOnly = true,
        )
        assertTrue("auto_body_0_100" in ids)
        assertTrue("auto_body_700_900" in ids)
    }

    /** Without a resolve pass the drawn bodies ARE the stored ones, keyed by stored id. */
    @Test
    fun `no resolved list falls back to the stored bodies`() {
        assertEquals(
            setOf("b1"),
            unshadedBodyRunIds(spec(), null, shadedBodies = false, shadeExplicitBodiesOnly = false),
        )
        assertEquals(
            emptySet<String>(),
            unshadedBodyRunIds(spec(shade = true), null, shadedBodies = false, shadeExplicitBodiesOnly = false),
        )
    }

    // ── Tapers and liners ─────────────────────────────────────────────────────

    private val kindSpec = ShaftSpec(
        overallLengthMm = 1000f,
        tapers = listOf(
            Taper(id = "t_unset", startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 100f, endDiaMm = 120f),
            Taper(id = "t_on", startFromAftMm = 200f, lengthMm = 200f, startDiaMm = 120f, endDiaMm = 140f, shadeOnDrawing = true),
            Taper(id = "t_off", startFromAftMm = 400f, lengthMm = 200f, startDiaMm = 140f, endDiaMm = 160f, shadeOnDrawing = false),
        ),
        liners = listOf(
            Liner(id = "l_unset", startFromAftMm = 600f, lengthMm = 100f, odMm = 180f),
            Liner(id = "l_on", startFromAftMm = 700f, lengthMm = 100f, odMm = 180f, shadeOnDrawing = true),
            Liner(id = "l_off", startFromAftMm = 800f, lengthMm = 100f, odMm = 180f, shadeOnDrawing = false),
        ),
    )

    /** Unset follows the checkbox; an explicit value beats it in both directions. */
    @Test
    fun `tapers are tri-state against their kind checkbox`() {
        assertEquals(setOf("t_off"), unshadedTaperIds(kindSpec, shadedTapers = true))
        assertEquals(setOf("t_unset", "t_off"), unshadedTaperIds(kindSpec, shadedTapers = false))
    }

    @Test
    fun `liners are tri-state against their kind checkbox`() {
        assertEquals(setOf("l_off"), unshadedLinerIds(kindSpec, shadedLiners = true))
        assertEquals(setOf("l_unset", "l_off"), unshadedLinerIds(kindSpec, shadedLiners = false))
    }

    /**
     * The byte-identity guard for every document that never touches the flags: with all three
     * kind checkboxes off, EVERY drawn id is named, which is exactly what the old null fill
     * paints did.
     */
    @Test
    fun `a flagless spec under all-off prefs names every id`() {
        val flagless = spec()
        val bodyIds = unshadedBodyRunIds(flagless, resolved, shadedBodies = false, shadeExplicitBodiesOnly = false)
        assertEquals(
            resolved.filterIsInstance<ResolvedBody>().map { it.id }.toSet(),
            bodyIds,
        )
        val plainKinds = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = kindSpec.tapers.map { it.copy(shadeOnDrawing = null) },
            liners = kindSpec.liners.map { it.copy(shadeOnDrawing = null) },
        )
        assertEquals(
            plainKinds.tapers.map { it.id }.toSet(),
            unshadedTaperIds(plainKinds, shadedTapers = false),
        )
        assertEquals(
            plainKinds.liners.map { it.id }.toSet(),
            unshadedLinerIds(plainKinds, shadedLiners = false),
        )
    }

    // ── Positive complement (the editor preview's PDF-shade mirror) ───────────

    @Test
    fun `shadedComponentIds is empty for a flagless spec with every kind off`() {
        assertTrue(
            shadedComponentIds(
                spec(), resolved,
                shadedBodies = false, shadedTapers = false, shadedLiners = false,
                shadeExplicitBodiesOnly = false,
            ).isEmpty()
        )
    }

    @Test
    fun `one explicit shade under all kinds off names exactly that body's runs`() {
        // The on-device case: a named section's card toggle checked, every checkbox off —
        // the preview box must mark that section and nothing else.
        assertEquals(
            setOf("b1", "b1#2"),
            shadedComponentIds(
                spec(shade = true), resolved,
                shadedBodies = false, shadedTapers = false, shadedLiners = false,
                shadeExplicitBodiesOnly = false,
            )
        )
    }

    @Test
    fun `kinds on shade everything a sheet draws minus explicit opt-outs`() {
        val ids = shadedComponentIds(
            spec(shade = false), resolved,
            shadedBodies = true, shadedTapers = true, shadedLiners = true,
            shadeExplicitBodiesOnly = false,
        )
        // The opted-out body's runs stay bare; auto runs and the liner shade.
        assertEquals(setOf("auto_body_0_100", "auto_body_700_900", "ln1"), ids)
    }
}
