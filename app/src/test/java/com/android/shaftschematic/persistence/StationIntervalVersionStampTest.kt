package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.doc.encodeTemplateJson
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `station_interval_version` stamp is what tells a document authored before length-driven
 * station counts from one authored after. Without it the legacy-count freeze cannot distinguish
 * the two — neither carries an override — and would pin new documents to the old flat defaults.
 *
 * Both writers must stamp it, and neither passes it — [ShaftDocCodec.encodeV1] owning the stamp is
 * what makes a forgetful caller safe instead of a corruption vector. The template writer is checked
 * through its REAL envelope (`doc/TemplateEnvelope.kt`); the job writer lives on `ShaftViewModel`,
 * an `AndroidViewModel` this JVM suite does not instantiate, so it is checked at the encoder it
 * calls.
 */
class StationIntervalVersionStampTest {

    private val spec = ShaftSpec(
        overallLengthMm = 1000f,
        bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 1000f, diaMm = 100f)),
    )

    @Test
    fun `an absent stamp reads as legacy`() {
        val legacyJson = """
            { "version": 1, "spec": { "overallLengthMm": 1000.0 } }
        """.trimIndent()
        // Decoding must not throw, and the document is treated as pre-interval (the freeze is
        // then gated on it having readings at all).
        assertTrue(ShaftDocCodec.decode(legacyJson).runoutConfig.componentOverrides.isEmpty())
    }

    @Test
    fun `the encoder stamps the current version even when the caller forgets`() {
        // stationIntervalVersion deliberately NOT passed: the field's decode default is 0, so
        // a forgetful writer compiles cleanly — encodeV1 owning the stamp is what makes that
        // safe instead of a corruption vector.
        val json = ShaftDocCodec.encodeV1(ShaftDocCodec.ShaftDocV1(spec = spec))

        assertTrue(
            "envelope must carry station_interval_version",
            json.contains("\"station_interval_version\""),
        )
        assertTrue(
            "the stamp must be the current version",
            json.contains("\"station_interval_version\": ${ShaftDocCodec.CURRENT_STATION_INTERVAL_VERSION}"),
        )
    }

    @Test
    fun `a saved template carries the stamp`() {
        // Through the real template envelope, not a copy of it: a template written without the
        // stamp would read as pre-interval and have its station counts frozen to the old flat
        // defaults on the first drawing made from it.
        val json = encodeTemplateJson(
            spec = spec,
            preferredUnit = UnitSystem.INCHES,
            unitLocked = true,
        )

        assertTrue(
            "a template envelope must carry station_interval_version",
            json.contains("\"station_interval_version\": ${ShaftDocCodec.CURRENT_STATION_INTERVAL_VERSION}"),
        )
    }

    @Test
    fun `the stamp round-trips`() {
        val json = ShaftDocCodec.encodeV1(ShaftDocCodec.ShaftDocV1(spec = spec))
        // Re-decode through the envelope serializer to prove the field survives a full trip.
        val reDecoded = ShaftDocCodec.decode(json)
        assertEquals(1000f, reDecoded.spec.overallLengthMm, 0.001f)
    }

    @Test
    fun `the stamp is additive - an older build ignoring it still decodes the file`() {
        // `ignoreUnknownKeys = true` is what makes this additive; a file carrying the stamp
        // must stay readable by a build that has never heard of it.
        val withStamp = """
            {
              "version": 1,
              "station_interval_version": 1,
              "some_future_field": 42,
              "spec": { "overallLengthMm": 800.0 }
            }
        """.trimIndent()

        assertEquals(800f, ShaftDocCodec.decode(withStamp).spec.overallLengthMm, 0.001f)
    }
}
