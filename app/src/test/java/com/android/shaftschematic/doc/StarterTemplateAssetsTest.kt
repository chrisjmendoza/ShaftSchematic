package com.android.shaftschematic.doc

import com.android.shaftschematic.io.TemplateStorage
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.validate
import com.android.shaftschematic.template.TemplateLinerCount
import com.android.shaftschematic.template.TemplateSizeBucket
import com.android.shaftschematic.template.templateLinerCount
import com.android.shaftschematic.template.templateSizeBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled starter templates must decode, be geometrically valid, carry no job identity
 * (they are seeded into a store whose whole contract is "geometry only"), and land in the
 * buckets their filenames advertise — a starter that files itself under the wrong heading is
 * worse than no starter at all.
 */
class StarterTemplateAssetsTest {

    private fun templateFiles(): List<File> {
        val userDir = System.getProperty("user.dir") ?: error("Missing system property: user.dir")
        val root = File(userDir)
        val dir = listOf(
            File(root, "app/src/main/assets/templates"),
            File(root, "src/main/assets/templates"),
        ).firstOrNull { it.exists() }
            ?: error("Missing templates assets directory under: ${root.absolutePath}")

        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(SHAFT_DOT_EXT, ignoreCase = true) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    @Test
    fun `every starter template decodes and is geometrically valid`() {
        val files = templateFiles()
        assertTrue("Expected at least one starter template", files.isNotEmpty())

        files.forEach { f ->
            val doc = ShaftDocCodec.decode(f.readText())
            assertTrue("${f.name} must have a positive OAL", doc.spec.overallLengthMm > 0f)
            assertTrue("${f.name} must be within bounds", doc.spec.validate())
            assertTrue("${f.name} must have components", doc.spec.bodies.isNotEmpty())
        }
    }

    @Test
    fun `no starter template carries job identity or measurements`() {
        templateFiles().forEach { f ->
            val doc = ShaftDocCodec.decode(f.readText())
            assertEquals("${f.name} job number", "", doc.jobNumber)
            assertEquals("${f.name} customer", "", doc.customer)
            assertEquals("${f.name} vessel", "", doc.vessel)
            assertEquals("${f.name} notes", "", doc.notes)
            assertEquals("${f.name} position", ShaftPosition.OTHER, doc.shaftPosition)
            assertTrue("${f.name} wear spots", doc.wearRecord.spots.isEmpty())
            assertTrue("${f.name} readings", doc.runoutReadings.readings.isEmpty())
            assertTrue("${f.name} undercuts", doc.undercutRecord.undercuts.isEmpty())
        }
    }

    @Test
    fun `each starter files under the size and count its name advertises`() {
        // Filenames are "<order>_<size>in_<n>liner(s).shaft" — the derived buckets must agree,
        // or the browser puts a starter somewhere its name says it is not.
        val pattern = Regex("^\\d+_(\\d+)in_(\\d+)liners?$")

        templateFiles().forEach { f ->
            val base = stripShaftDocExtension(f.name)
            val match = pattern.matchEntire(base) ?: error("Unexpected starter name: ${f.name}")
            val expectedInches = match.groupValues[1].toInt()
            val expectedLiners = match.groupValues[2].toInt()

            val spec = ShaftDocCodec.decode(f.readText()).spec
            assertEquals(
                "${f.name} size bucket",
                TemplateSizeBucket.Inches(expectedInches),
                templateSizeBucket(spec),
            )
            assertEquals(
                "${f.name} liner count",
                expectedLiners,
                spec.liners.count { it.odMm > 0f },
            )
            assertTrue(
                "${f.name} count bucket",
                templateLinerCount(spec) != TemplateLinerCount.NONE,
            )
        }
    }

    @Test
    fun `every starter taper's labeled rate matches its geometry`() {
        // taperRateText prints verbatim on the footer, so a starter whose stored "1:12" is
        // cut at 1:24 puts a wrong rate on the first drawing made from it — and trips the
        // taper card's rate-mismatch warning out of the box.
        templateFiles().forEach { f ->
            val spec = ShaftDocCodec.decode(f.readText()).spec
            spec.tapers.forEach { t ->
                val label = t.taperRateText.trim()
                val n = Regex("^1:(\\d+(?:\\.\\d+)?)$").matchEntire(label)?.groupValues?.get(1)?.toFloat()
                    ?: error("${f.name} ${t.id}: unexpected rate text \"$label\"")
                val deltaDia = kotlin.math.abs(t.startDiaMm - t.endDiaMm)
                assertTrue("${f.name} ${t.id} must change diameter", deltaDia > 0f)
                val actual = t.lengthMm / deltaDia
                assertTrue(
                    "${f.name} ${t.id}: labeled 1:$n but geometry is 1:$actual",
                    kotlin.math.abs(actual - n) / n < 0.005f,
                )
            }
        }
    }

    @Test
    fun `the seeded display name drops the ordering prefix`() {
        assertEquals("6in 2liners", TemplateStorage.starterDisplayBase("02_6in_2liners.shaft"))
        assertEquals("4in 1liner", TemplateStorage.starterDisplayBase("01_4in_1liner.shaft"))
    }
}
