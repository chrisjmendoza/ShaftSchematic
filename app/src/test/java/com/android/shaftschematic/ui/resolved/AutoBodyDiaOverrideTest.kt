package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ShaftSpec.autoBodyDiaMm — the user-set bare-shaft Ø for auto-body spans.
 * One value covers ALL auto spans (one piece of stock); it wins over neighbor
 * derivation and never affects auto-span positioning.
 */
class AutoBodyDiaOverrideTest {

    private fun autoBodies(spec: ShaftSpec) =
        resolveComponents(spec, overallIsManual = true)
            .filterIsInstance<ResolvedBody>()
            .filter { it.source == ResolvedComponentSource.AUTO }

    @Test
    fun `override applies to every auto span`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(Liner(startFromAftMm = 400f, lengthMm = 200f, odMm = 200f)),
            autoBodyDiaMm = 150f,
        )
        val autos = autoBodies(spec)
        assertEquals(2, autos.size) // leading + trailing spans around the liner
        autos.forEach { assertEquals(150f, it.diaMm) }
    }

    @Test
    fun `override wins over an adjacent explicit body's diameter`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 400f, diaMm = 178f)),
            liners = listOf(Liner(startFromAftMm = 400f, lengthMm = 200f, odMm = 200f)),
            autoBodyDiaMm = 150f,
        )
        val autos = autoBodies(spec)
        assertEquals(1, autos.size) // the 600..1000 gap
        assertEquals(150f, autos.single().diaMm)
    }

    @Test
    fun `unset override keeps derived neighbor diameter`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 400f, diaMm = 178f)),
            liners = listOf(Liner(startFromAftMm = 400f, lengthMm = 200f, odMm = 200f)),
        )
        assertEquals(178f, autoBodies(spec).single().diaMm)
    }

    @Test
    fun `override never moves auto spans`() {
        val base = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(Liner(startFromAftMm = 400f, lengthMm = 200f, odMm = 200f)),
        )
        val spans = { s: ShaftSpec -> autoBodies(s).map { it.startMmPhysical to it.endMmPhysical } }
        assertEquals(spans(base), spans(base.copy(autoBodyDiaMm = 150f)))
    }

    @Test
    fun `autoBodyDiaMm survives json roundtrip and defaults to 0 on legacy docs`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val spec = ShaftSpec(overallLengthMm = 500f, autoBodyDiaMm = 150f)
        val decoded = json.decodeFromString<ShaftSpec>(json.encodeToString(ShaftSpec.serializer(), spec))
        assertEquals(150f, decoded.autoBodyDiaMm)

        val legacy = json.decodeFromString<ShaftSpec>("""{"overallLengthMm":500.0}""")
        assertEquals(0f, legacy.autoBodyDiaMm)
        assertTrue(autoBodies(legacy).all { it.diaMm > 0f }) // still derives a drawable Ø
    }
}
