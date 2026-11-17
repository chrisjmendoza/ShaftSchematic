package com.android.shaftschematic.persistence

import com.android.shaftschematic.data.AutosaveManager
import com.android.shaftschematic.data.isDefaultSession
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.doc.mateDuplicate
import com.android.shaftschematic.doc.templateDocFor
import com.android.shaftschematic.model.ShaftEnd
import com.android.shaftschematic.model.ShaftEndHoles
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.ThreadSeries
import com.android.shaftschematic.model.endHoles
import com.android.shaftschematic.model.withEndHoles
import com.android.shaftschematic.util.UnitSystem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-sheet notes switches (`sheet_notes_sheets`) and the shaft ends' holes
 * (`ShaftSpec.aftEndHoles` / `fwdEndHoles`) survive save, autosave and decode; older files decode
 * to "off" and "none".
 */
class NotesAndEndHolesPersistenceTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val choices = mapOf("SCHEMATIC" to true)
    private val holes = ShaftEndHoles(
        drawPlateCount = 4, drawPlateSize = "1 1/4", drawPlateBoltCircleMm = 114.3f,
        drawPlateSeries = ThreadSeries.NF,
    )
    private val spec = ShaftSpec(overallLengthMm = 2000f).withEndHoles(ShaftEnd.FWD, holes)

    @Test
    fun `switches and holes round-trip through the envelope, verbatim`() {
        val doc = ShaftDocCodec.ShaftDocV1(spec = spec, sheetNotesSheets = choices, notes = "Clad taper")
        val raw = ShaftDocCodec.encodeV1(doc)

        assertTrue(raw.contains("\"sheet_notes_sheets\""))
        val decoded = ShaftDocCodec.decode(raw)
        assertEquals(choices, decoded.sheetNotesSheets)
        assertEquals(holes, decoded.spec.endHoles(ShaftEnd.FWD))
        assertEquals(ShaftEndHoles(), decoded.spec.endHoles(ShaftEnd.AFT))
        assertEquals(choices, ShaftDocCodec.decodeEnvelope(raw).sheetNotesSheets)
    }

    @Test
    fun `an older file decodes to no switches and no holes`() {
        val decoded = ShaftDocCodec.decode("""{ "version": 1, "spec": { "overallLengthMm": 1000.0 } }""")
        assertEquals(emptyMap<String, Boolean>(), decoded.sheetNotesSheets)
        assertEquals(ShaftEndHoles(), decoded.spec.aftEndHoles)
        assertEquals(ShaftEndHoles(), decoded.spec.fwdEndHoles)
    }

    @Test
    fun `the switches round-trip through an autosave draft and count as session content`() {
        val snap = AutosaveManager.SessionSnapshot(
            shaftSpec = ShaftSpec(overallLengthMm = 400f),
            unitSystem = UnitSystem.INCHES,
            shaftPosition = ShaftPosition.OTHER,
            customer = "", vessel = "", jobNumber = "", notes = "",
            sheetNotesSheets = choices,
        )
        val restored = json.decodeFromString(
            AutosaveManager.SessionSnapshot.serializer(),
            json.encodeToString(AutosaveManager.SessionSnapshot.serializer(), snap),
        )
        assertEquals(choices, restored.sheetNotesSheets)
        assertFalse(snap.copy(shaftSpec = ShaftSpec()).isDefaultSession())
    }

    @Test
    fun `a template keeps the holes but not the switches, a mate carries both`() {
        val template = templateDocFor(spec, UnitSystem.INCHES, unitLocked = true)
        assertEquals(holes, template.spec.fwdEndHoles)
        assertEquals(emptyMap<String, Boolean>(), template.sheetNotesSheets)

        val source = ShaftDocCodec.ShaftDocV1(spec = spec, sheetNotesSheets = choices)
        val mate = mateDuplicate(source, jobNumber = "J-2", customer = "C", vessel = "V", position = ShaftPosition.PORT)
        assertEquals(choices, mate.sheetNotesSheets)
        assertEquals(holes, mate.spec.fwdEndHoles)
    }

    @Test
    fun `holes saved before the series decode to NC, an unknown series too`() {
        val raw = """{ "version": 1, "spec": { "overallLengthMm": 1000.0,
            "fwdEndHoles": { "drawPlateCount": 4, "drawPlateSize": "1 1/4" },
            "aftEndHoles": { "drawPlateSize": "1", "drawPlateSeries": "UNEF" } } }"""
        val decoded = ShaftDocCodec.decode(raw)
        assertEquals(ThreadSeries.NC, decoded.spec.fwdEndHoles.drawPlateSeries)
        assertEquals(ThreadSeries.NC, decoded.spec.aftEndHoles.drawPlateSeries)
    }
}
