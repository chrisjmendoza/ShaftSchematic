package com.android.shaftschematic.persistence

import com.android.shaftschematic.data.AutosaveManager
import com.android.shaftschematic.data.isDefaultSession
import com.android.shaftschematic.data.shouldWriteDraft
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.doc.mateDuplicate
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persistence of the FINAL drawing — the document's second geometry (`final_spec`).
 *
 * Three things are pinned here. It round-trips **whole**, component ids included, since ids
 * shared with the original are what line the two drawings up (unit overrides today, a
 * before/after comparison later). It is **absent by default**: a file written without it, and
 * every legacy spec-only file, decodes to null — no drawing conjured where none was authored.
 * And a session that has one is **dirty**, both through the autosave gate and through the
 * factory-default predicate that can veto it: a started final that never reaches a draft dies
 * with the process.
 */
class FinalSpecPersistenceTest {

    private fun original(): ShaftSpec = ShaftSpec(
        overallLengthMm = 3000f,
        bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 1000f, diaMm = 152.4f)),
        liners = listOf(Liner(id = "ln1", startFromAftMm = 1200f, lengthMm = 400f, odMm = 160f)),
    )

    // The foreman's decision after the wear map: the liner moves forward and grows.
    private fun final(): ShaftSpec = original().copy(
        liners = listOf(Liner(id = "ln1", startFromAftMm = 1350f, lengthMm = 500f, odMm = 160f)),
    )

    // ── Codec ────────────────────────────────────────────────────────────────

    @Test
    fun `envelope round trip preserves the final spec with its component ids`() {
        val doc = ShaftDocCodec.ShaftDocV1(spec = original(), finalSpec = final())

        val raw = ShaftDocCodec.encodeV1(doc)
        assertTrue("expected final_spec key in JSON", raw.contains("\"final_spec\""))

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        val f = requireNotNull(decoded.finalSpec) { "the final spec must survive the round trip" }
        assertEquals("the whole geometry round-trips structurally", final(), f)
        assertEquals("component ids are preserved", listOf("ln1"), f.liners.map { it.id })
        assertEquals("body ids are preserved", listOf("b1"), f.bodies.map { it.id })
        // The two geometries stay independent: the original still has its own liner position.
        assertEquals(1200f, decoded.spec.liners.single().startFromAftMm, 0.001f)
        assertEquals(400f, decoded.spec.liners.single().lengthMm, 0.001f)
        assertEquals(1350f, f.liners.single().startFromAftMm, 0.001f)
    }

    @Test
    fun `an envelope written without a final spec decodes to null`() {
        val doc = ShaftDocCodec.ShaftDocV1(spec = original())

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertNull("no final drawing was ever started", decoded.finalSpec)
    }

    @Test
    fun `an envelope whose JSON omits the key decodes to null`() {
        // A file written before final_spec existed: the key is simply not there.
        val raw = """{"version":1,"spec":{"overallLengthMm":3000.0}}"""

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(ShaftDocCodec.Format.ENVELOPE_V1, decoded.format)
        assertNull(decoded.finalSpec)
    }

    @Test
    fun `a legacy spec-only file decodes to null`() {
        val raw = Json.encodeToString(ShaftSpec.serializer(), original())

        val decoded = ShaftDocCodec.decode(raw)

        assertEquals(ShaftDocCodec.Format.LEGACY_SPEC, decoded.format)
        assertNull("a spec-only file predates the final drawing entirely", decoded.finalSpec)
    }

    @Test
    fun `decodeEnvelope carries the final spec back into the envelope shape`() {
        val doc = ShaftDocCodec.ShaftDocV1(spec = original(), finalSpec = final())

        val envelope = ShaftDocCodec.decodeEnvelope(ShaftDocCodec.encodeV1(doc))

        assertEquals(final(), envelope.finalSpec)
    }

    @Test
    fun `duplicate for mate drops the final drawing`() {
        val source = ShaftDocCodec.ShaftDocV1(
            spec = original(),
            finalSpec = final(),
            jobNumber = "J-1",
            customer = "Acme",
            vessel = "Tug",
            shaftPosition = ShaftPosition.PORT,
        )

        val mate = mateDuplicate(
            source = source,
            jobNumber = "J-2",
            customer = "Acme",
            vessel = "Tug",
            position = ShaftPosition.STBD,
        )

        assertNull("the mate is its own job — it draws its own final", mate.finalSpec)
        assertEquals("the geometry still travels", original(), mate.spec)
    }

    // ── Autosave ─────────────────────────────────────────────────────────────

    // Mirrors AutosaveManager's private Json config.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun snapshot(finalSpec: ShaftSpec? = null) = AutosaveManager.SessionSnapshot(
        shaftSpec = original(),
        unitSystem = UnitSystem.INCHES,
        shaftPosition = ShaftPosition.PORT,
        customer = "Acme",
        vessel = "Tug",
        jobNumber = "J-1",
        notes = "",
        finalSpec = finalSpec,
    )

    @Test
    fun `session snapshot round trips the final spec`() {
        val snap = snapshot(final())

        val raw = json.encodeToString(AutosaveManager.SessionSnapshot.serializer(), snap)
        val back = json.decodeFromString(AutosaveManager.SessionSnapshot.serializer(), raw)

        assertEquals(snap, back)
        assertEquals(final(), back.finalSpec)
    }

    @Test
    fun `an older draft without the field decodes to no final drawing`() {
        val snap = snapshot(finalSpec = null)
        val raw = json.encodeToString(AutosaveManager.SessionSnapshot.serializer(), snap)
        // Strip the key the way a draft written before the field existed would lack it.
        val stripped = raw.replace(",\"finalSpec\":null", "").replace("\"finalSpec\":null,", "")

        val back = json.decodeFromString(AutosaveManager.SessionSnapshot.serializer(), stripped)

        assertNull(back.finalSpec)
    }

    @Test
    fun `a snapshot differing only in its final spec is dirty`() {
        val saved = snapshot(finalSpec = null)
        val live = snapshot(final())

        assertTrue("starting a final drawing is unsaved work", shouldWriteDraft(live, saved))
    }

    @Test
    fun `an edit to the final spec alone is dirty`() {
        val saved = snapshot(original())
        val live = snapshot(final())

        assertTrue("an edit on the final drawing is unsaved work", shouldWriteDraft(live, saved))
    }

    @Test
    fun `a session carrying a final drawing is never a factory-default session`() {
        // The blank-session veto sits in front of the dirty gate, so a final started on an
        // otherwise-empty document would never reach the draft ring if it counted as default.
        val blank = AutosaveManager.SessionSnapshot(
            shaftSpec = ShaftSpec(),
            unitSystem = UnitSystem.INCHES,
            shaftPosition = ShaftPosition.OTHER,
            customer = "",
            vessel = "",
            jobNumber = "",
            notes = "",
        )

        assertTrue("sanity: the blank session is default", blank.isDefaultSession())
        assertFalse(
            "a started final drawing is authored content",
            blank.copy(finalSpec = ShaftSpec()).isDefaultSession(),
        )
    }
}
