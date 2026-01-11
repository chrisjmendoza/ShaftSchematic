package com.android.shaftschematic.doc

import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SampleShaftAssetsTest {

    private val epsMm = 0.25f
    private val minTaperLengthIn = 12.0f
    private val maxTaperLengthIn = 24.0f
    private val minTaperRateInPerFt = 0.70f
    private val maxTaperRateInPerFt = 1.05f
    private val chainToleranceMm = 1.0f

    @Test
    fun `bundled sample shafts exist and decode with realistic metadata`() {
        val userDir = System.getProperty("user.dir") ?: error("Missing system property: user.dir")
        val root = File(userDir)

        val samplesDir = listOf(
            File(root, "app/src/main/assets/sample_shafts"),
            File(root, "src/main/assets/sample_shafts"),
        ).firstOrNull { it.exists() }
            ?: error("Missing assets directory under: ${root.absolutePath}")

        val files = samplesDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(SHAFT_DOT_EXT, ignoreCase = true) }
            ?.sortedBy { it.name }
            ?: emptyList()

        assertEquals(5, files.size)

        val decoded = files.map { f ->
            val raw = f.readText()
            val doc = ShaftDocCodec.decode(raw)

            assertTrue("Job number must be 814### in ${f.name}", Regex("^814\\d{3}$").matches(doc.jobNumber))
            assertFalse("Customer should be non-blank in ${f.name}", doc.customer.isBlank())
            assertFalse("Vessel should be non-blank in ${f.name}", doc.vessel.isBlank())
            assertFalse("Notes should be non-blank in ${f.name}", doc.notes.isBlank())
            assertTrue("Notes should include sample marker in ${f.name}", isSeededSampleNotes(doc.notes))
            assertTrue("Spec should validate in ${f.name}", doc.spec.validate())

            // Shop-style realism checks: threads → taper → body → liners → taper.
            val spec = doc.spec
            assertTrue("Need ≥1 thread in ${f.name}", spec.threads.isNotEmpty())
            assertTrue("Need ≥2 tapers in ${f.name}", spec.tapers.size >= 2)
            assertTrue("Need ≥1 body in ${f.name}", spec.bodies.isNotEmpty())
            assertTrue("Need ≥1 liner in ${f.name}", spec.liners.isNotEmpty())

            // Global geometry rule for sample docs: no overlaps between any components.
            // Components should form a contiguous chain from AFT (0) to OAL.
            data class Segment(val kind: String, val startMm: Float, val endMm: Float)
            val segments = buildList {
                spec.threads.forEach { add(Segment("thread", it.startFromAftMm, it.startFromAftMm + it.lengthMm)) }
                spec.tapers.forEach { add(Segment("taper", it.startFromAftMm, it.startFromAftMm + it.lengthMm)) }
                spec.bodies.forEach { add(Segment("body", it.startFromAftMm, it.startFromAftMm + it.lengthMm)) }
                spec.liners.forEach { add(Segment("liner", it.startFromAftMm, it.startFromAftMm + it.lengthMm)) }
            }.sortedWith(compareBy<Segment>({ it.startMm }, { it.endMm }, { it.kind }))

            assertTrue("Sample must have multiple segments in ${f.name}", segments.size >= 5)
            assertTrue("First segment should start at 0mm in ${f.name}", segments.first().startMm <= epsMm)
            assertTrue("First segment should be threads in ${f.name}", segments.first().kind == "thread")
            assertTrue("Last segment should be a taper in ${f.name}", segments.last().kind == "taper")

            var prevEnd = segments.first().endMm
            for (seg in segments.drop(1)) {
                assertTrue("Segment overlap in ${f.name}", seg.startMm + epsMm >= prevEnd)
                assertTrue(
                    "Segment chain should be contiguous in ${f.name}",
                    kotlin.math.abs(seg.startMm - prevEnd) <= chainToleranceMm,
                )
                prevEnd = seg.endMm
            }
            assertTrue(
                "Last segment should end at OAL in ${f.name}",
                kotlin.math.abs(spec.overallLengthMm - prevEnd) <= chainToleranceMm,
            )

            // Tapers: keep lengths in a shop-realistic range and ensure rate is roughly 1:12 to 1:16.
            for (taper in spec.tapers) {
                val lengthIn = taper.lengthMm / 25.4f
                assertTrue(
                    "Taper length should be 12–24in in ${f.name}",
                    lengthIn + 1e-3f >= minTaperLengthIn && lengthIn - 1e-3f <= maxTaperLengthIn,
                )

                val deltaIn = kotlin.math.abs(taper.endDiaMm - taper.startDiaMm) / 25.4f
                val rateInPerFt = (deltaIn / lengthIn) * 12.0f
                assertTrue(
                    "Taper rate should be ~0.75–1.0 in/ft in ${f.name}",
                    rateInPerFt + 1e-3f >= minTaperRateInPerFt && rateInPerFt - 1e-3f <= maxTaperRateInPerFt,
                )
            }

            val body = spec.bodies.minBy { it.startFromAftMm }
            val bodyDia = body.diaMm

            val firstThread = spec.threads.minBy { it.startFromAftMm }
            val threadEnd = firstThread.startFromAftMm + firstThread.lengthMm

            val firstTaper = spec.tapers.minBy { it.startFromAftMm }
            val firstTaperEnd = firstTaper.startFromAftMm + firstTaper.lengthMm

            val lastTaper = spec.tapers.maxBy { it.startFromAftMm }
            val lastTaperEnd = lastTaper.startFromAftMm + lastTaper.lengthMm

            val linersSorted = spec.liners.sortedBy { it.startFromAftMm }
            val maxLinerEnd = linersSorted.maxOf { it.startFromAftMm + it.lengthMm }

            assertTrue("First thread should start at AFT (0 mm) in ${f.name}", firstThread.startFromAftMm <= epsMm)
            assertTrue("AFT taper should start after threads in ${f.name}", firstTaper.startFromAftMm + epsMm >= threadEnd)
            assertTrue("Body should start after AFT taper in ${f.name}", body.startFromAftMm + epsMm >= firstTaperEnd)
            assertTrue("FWD taper should start after liners in ${f.name}", lastTaper.startFromAftMm + epsMm >= maxLinerEnd)
            assertTrue("FWD taper should end at overall length in ${f.name}", kotlin.math.abs(spec.overallLengthMm - lastTaperEnd) <= 1.0f)

            // Taper LETs should match body OD (within tolerance).
            assertTrue(
                "AFT taper end dia should match body dia in ${f.name}",
                kotlin.math.abs(firstTaper.endDiaMm - bodyDia) <= 0.5f,
            )
            assertTrue(
                "FWD taper start dia should match body dia in ${f.name}",
                kotlin.math.abs(lastTaper.startDiaMm - bodyDia) <= 0.5f,
            )

            // Liners: no overlaps, and OD should be only slightly above body.
            var prevLinerEnd = -1f
            for (liner in linersSorted) {
                val start = liner.startFromAftMm
                val end = liner.startFromAftMm + liner.lengthMm
                assertTrue("Liner overlaps another liner in ${f.name}", start + epsMm >= prevLinerEnd)
                prevLinerEnd = end

                val delta = liner.odMm - bodyDia
                assertTrue("Liner OD should exceed body OD in ${f.name}", delta > 0f)
                assertTrue("Liner OD delta should be 6–12mm in ${f.name}", delta >= 6f && delta <= 12f)
            }

            doc
        }

        val portCount = decoded.count { it.shaftPosition == ShaftPosition.PORT }
        val stbdCount = decoded.count { it.shaftPosition == ShaftPosition.STBD }

        assertTrue("Need at least 2 PORT samples", portCount >= 2)
        assertTrue("Need at least 2 STBD samples", stbdCount >= 2)
    }
}
