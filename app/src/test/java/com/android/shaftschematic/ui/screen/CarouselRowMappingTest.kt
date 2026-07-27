package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.resolveComponents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `buildCarouselRows` — the resolved-component → stored-component index mapping behind the
 * carousel pager.
 *
 * A stored body trimmed around a liner/taper/thread resolves into several rows whose ids carry
 * a fragment suffix (`"<id>#2"`, …). Every one of those rows must still edit the SAME stored
 * body, i.e. resolve to the same `explicitIndex`, otherwise the extra fragments render as
 * uneditable cards.
 */
class CarouselRowMappingTest {

    private fun keyedBody(id: String, startMm: Float, lengthMm: Float) = Body(
        id = id,
        startFromAftMm = startMm,
        lengthMm = lengthMm,
        diaMm = 150f,
        keywayWidthMm = 12f,
        keywayDepthMm = 6f,
        keywayLengthMm = 80f,
        keywayEnd = LinerAuthoredReference.AFT,
    )

    @Test
    fun `both fragments of a trimmed body map to the same stored body`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(
                Body(id = "body-0", startFromAftMm = 700f, lengthMm = 100f, diaMm = 150f),
                keyedBody("body-1", startMm = 0f, lengthMm = 600f),
            ),
            liners = listOf(Liner(id = "liner-1", startFromAftMm = 200f, lengthMm = 200f, odMm = 200f)),
        )
        val rows = buildCarouselRows(spec, resolveComponents(spec, overallIsManual = false))

        val fragmentRows = rows.filter { row ->
            val c = row.component
            c is ResolvedBody && c.source == ResolvedComponentSource.EXPLICIT &&
                c.startMmPhysical < 700f
        }
        assertEquals(2, fragmentRows.size)
        assertEquals(listOf("body-1", "body-1#2"), fragmentRows.map { it.component.id })

        val storedIndex = spec.bodies.indexOfFirst { it.id == "body-1" }
        fragmentRows.forEach { row ->
            assertEquals("fragment must edit the stored body", storedIndex, row.explicitIndex)
        }
    }

    @Test
    fun `every resolved row gets a row and ids stay unique`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(keyedBody("body-1", startMm = 0f, lengthMm = 600f)),
            liners = listOf(Liner(id = "liner-1", startFromAftMm = 200f, lengthMm = 200f, odMm = 200f)),
        )
        val resolved = resolveComponents(spec, overallIsManual = true)
        val rows = buildCarouselRows(spec, resolved)

        assertEquals(resolved.size, rows.size)
        val ids = rows.map { it.component.id }
        assertEquals("pager keys must be unique: $ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `auto bodies have no stored index`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(Liner(id = "liner-1", startFromAftMm = 400f, lengthMm = 200f, odMm = 200f)),
        )
        val rows = buildCarouselRows(spec, resolveComponents(spec, overallIsManual = true))

        val autoRows = rows.filter {
            (it.component as? ResolvedBody)?.source == ResolvedComponentSource.AUTO
        }
        assertTrue(autoRows.isNotEmpty())
        autoRows.forEach { assertNull(it.explicitIndex) }
    }

    @Test
    fun `non-body components map by their own id`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(Taper(id = "taper-1", startFromAftMm = 0f, lengthMm = 150f, startDiaMm = 140f, endDiaMm = 120f)),
            liners = listOf(
                Liner(id = "liner-0", startFromAftMm = 300f, lengthMm = 100f, odMm = 200f),
                Liner(id = "liner-1", startFromAftMm = 600f, lengthMm = 100f, odMm = 200f),
            ),
        )
        val rows = buildCarouselRows(spec, resolveComponents(spec, overallIsManual = false))

        assertEquals(0, rows.first { it.component.id == "taper-1" }.explicitIndex)
        assertEquals(0, rows.first { it.component.id == "liner-0" }.explicitIndex)
        assertEquals(1, rows.first { it.component.id == "liner-1" }.explicitIndex)
        assertTrue(rows.any { it.component is ResolvedLiner })
    }
}
