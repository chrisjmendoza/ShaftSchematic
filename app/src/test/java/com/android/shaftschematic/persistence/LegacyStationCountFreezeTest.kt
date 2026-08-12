package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.settings.RunoutConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decode-time protection for documents authored before station counts became length-driven:
 * a component that already carries readings keeps the count those readings were taken against.
 */
class LegacyStationCountFreezeTest {

    private val spec = ShaftSpec(
        overallLengthMm = 3000f,
        bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 1500f, diaMm = 120f)),
        tapers = listOf(Taper(id = "t1", startFromAftMm = 1600f, lengthMm = 300f, startDiaMm = 120f, endDiaMm = 90f)),
        liners = listOf(Liner(id = "l1", startFromAftMm = 2000f, lengthMm = 500f, odMm = 150f)),
    )

    /** Legacy by default — an unstamped document is one authored before the interval. */
    private fun freeze(
        readings: RunoutReadings,
        config: RunoutConfig = RunoutConfig(),
        stationIntervalVersion: Int = 0,
    ) = ShaftDocCodec.freezeLegacyStationCounts(spec, config, readings, stationIntervalVersion)

    @Test
    fun `a document with no readings is left alone`() {
        val out = freeze(RunoutReadings())
        assertTrue(out.componentOverrides.isEmpty())
    }

    @Test
    fun `a component with readings keeps its pre-interval count`() {
        val readings = RunoutReadings(
            listOf(
                RunoutReading(componentId = "b1", stationIndex = 1, valueMm = 0.05f),
                RunoutReading(componentId = "t1", stationIndex = 0, valueMm = 0.02f),
                RunoutReading(componentId = "l1", stationIndex = 1, valueMm = 0.03f),
            )
        )
        val out = freeze(readings).componentOverrides

        assertEquals(RunoutConfig.LEGACY_BODY_DEFAULT_COUNT, out["b1"])
        assertEquals(RunoutConfig.LEGACY_TAPER_DEFAULT_COUNT, out["t1"])
        assertEquals(RunoutConfig.LEGACY_LINER_DEFAULT_COUNT, out["l1"])
    }

    @Test
    fun `a component without readings picks up the new interval defaults`() {
        val readings = RunoutReadings(listOf(RunoutReading(componentId = "b1", stationIndex = 0, valueMm = 0.05f)))
        val out = freeze(readings).componentOverrides

        assertEquals(RunoutConfig.LEGACY_BODY_DEFAULT_COUNT, out["b1"])
        assertNull(out["t1"])
        assertNull(out["l1"])
    }

    @Test
    fun `an explicit override always wins over the freeze`() {
        val readings = RunoutReadings(listOf(RunoutReading(componentId = "b1", stationIndex = 4, valueMm = 0.05f)))
        val out = freeze(readings, RunoutConfig(componentOverrides = mapOf("b1" to 6))).componentOverrides

        assertEquals(6, out["b1"])
    }

    @Test
    fun `an auto-body id freezes as a body`() {
        val readings = RunoutReadings(
            listOf(RunoutReading(componentId = "auto_body_0.000_500.000", stationIndex = 0, valueMm = 0.05f))
        )
        val out = freeze(readings).componentOverrides

        assertEquals(RunoutConfig.LEGACY_BODY_DEFAULT_COUNT, out["auto_body_0.000_500.000"])
    }

    @Test
    fun `the freeze survives a full envelope decode of a legacy document`() {
        // Hand-authored JSON, not encodeV1: the encoder stamps every document it writes with
        // the current station-interval version, so a legacy (unstamped) file is by definition
        // one this build can no longer produce.
        val legacyJson = """
            {
              "version": 1,
              "spec": {
                "overallLengthMm": 3000.0,
                "bodies": [
                  { "id": "b1", "startFromAftMm": 0.0, "lengthMm": 1500.0, "diaMm": 120.0 }
                ]
              },
              "runout_readings": {
                "readings": [
                  { "componentId": "b1", "stationIndex": 2, "valueMm": 0.04 }
                ]
              }
            }
        """.trimIndent()
        val decoded = ShaftDocCodec.decode(legacyJson)

        assertEquals(
            RunoutConfig.LEGACY_BODY_DEFAULT_COUNT,
            decoded.runoutConfig.componentOverrides["b1"],
        )
        // The reading itself is untouched — the freeze protects it, never rewrites it.
        assertEquals(0.04f, decoded.runoutReadings.find("b1", 2)?.valueMm)
    }

    // ── The freeze must never touch a document authored AFTER the interval ─────

    @Test
    fun `a stamped document is never frozen`() {
        val readings = RunoutReadings(listOf(RunoutReading(componentId = "b1", stationIndex = 4, valueMm = 0.05f)))
        val out = freeze(
            readings,
            stationIntervalVersion = ShaftDocCodec.CURRENT_STATION_INTERVAL_VERSION,
        )
        assertTrue(out.componentOverrides.isEmpty())
    }

    @Test
    fun `a document saved by this build reopens with its length-derived counts intact`() {
        // The corruption this guards against: a 100" body defaults to 5 stations under the
        // interval, the user records a reading at index 4, and reopening pins the body to the
        // OLD default of 3 — orphaning the readings at 3 and 4.
        val longBody = ShaftSpec(
            overallLengthMm = 3000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f * 25.4f, diaMm = 120f)),
        )
        val doc = ShaftDocCodec.ShaftDocV1(
            spec = longBody,
            runoutReadings = RunoutReadings(
                listOf(RunoutReading(componentId = "b1", stationIndex = 4, valueMm = 0.06f))
            ),
            stationIntervalVersion = ShaftDocCodec.CURRENT_STATION_INTERVAL_VERSION,
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(doc))

        assertNull(decoded.runoutConfig.componentOverrides["b1"])
        assertEquals(0.06f, decoded.runoutReadings.find("b1", 4)?.valueMm)
    }
}
