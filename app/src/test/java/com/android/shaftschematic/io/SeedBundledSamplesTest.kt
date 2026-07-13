package com.android.shaftschematic.io

import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.DocumentNaming
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SeedBundledSamplesTest {

    private class FakeAssets(
        private val files: Map<String, String>
    ) : InternalStorage.SampleAssetSource {
        override suspend fun listSampleShaftFiles(): List<String> = files.keys.toList()

        override suspend fun readSampleShaftText(filename: String): String =
            files[filename] ?: error("Missing fake asset: $filename")
    }

    private class FakeSettings(
        var seedVersion: Int,
        override var currentSeedVersion: Int = 4,
    ) : InternalStorage.SampleSeedSettings {
        var seededHashes: Map<String, String> = emptyMap()

        override suspend fun getSeedVersion(): Int = seedVersion

        override suspend fun setSeedVersion(v: Int) {
            seedVersion = v
        }

        override suspend fun getSeededSampleHashes(): Map<String, String> = seededHashes

        override suspend fun setSeededSampleHashes(hashes: Map<String, String>) {
            seededHashes = hashes
        }
    }

    private fun encodeDoc(
        jobNumber: String,
        customer: String,
        vessel: String,
        position: ShaftPosition,
        spec: ShaftSpec,
        notes: String = "[SAMPLE] seed-test",
    ): String = ShaftDocCodec.encodeV1(
        ShaftDocCodec.ShaftDocV1(
            preferredUnit = UnitSystem.INCHES,
            unitLocked = true,
            jobNumber = jobNumber,
            customer = customer,
            vessel = vessel,
            shaftPosition = position,
            notes = notes,
            spec = spec,
        )
    )

    @Test
    fun `seedVersion 0 and empty storage seeds samples and bumps version`() = runBlocking {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)

            val assets = FakeAssets(
                mapOf(
                    "01_basic" + SHAFT_DOT_EXT to encodeDoc(
                        jobNumber = "814123",
                        customer = "NorthSound Marine",
                        vessel = "MV Copper Kestrel",
                        position = ShaftPosition.PORT,
                        spec = ShaftSpec(
                            overallLengthMm = 1000f,
                            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 50f))
                        ),
                    ),
                    "02_other" + SHAFT_DOT_EXT to encodeDoc(
                        jobNumber = "814456",
                        customer = "Harborline Propulsion",
                        vessel = "FV Silver Marlin",
                        position = ShaftPosition.STBD,
                        spec = ShaftSpec(overallLengthMm = 500f),
                    ),
                )
            )

            val settings = FakeSettings(seedVersion = 0)

            val report = InternalStorage.seedBundledSamplesIfNeeded(shaftsDir, assets, settings)

            assertTrue(report.attemptedCount > 0)
            assertEquals(report.attemptedCount, report.savedCount)
            assertTrue(InternalStorage.list(shaftsDir).isNotEmpty())
            assertEquals(4, settings.seedVersion)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `seedVersion already current makes seeding a no-op`() = runBlocking {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)
            val assets = FakeAssets(mapOf("01_basic" + SHAFT_DOT_EXT to "{}"))
            val settings = FakeSettings(seedVersion = 4)

            val report = InternalStorage.seedBundledSamplesIfNeeded(shaftsDir, assets, settings)

            assertEquals(0, report.attemptedCount)
            assertEquals(0, report.savedCount)
            assertEquals(0, report.failedCount)
            assertEquals(emptyList<String>(), InternalStorage.list(shaftsDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `collision handling appends Sample suffix and never overwrites`() = runBlocking {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)

            val job = "814999"
            val customer = "NorthSound Marine"
            val vessel = "MV Copper Kestrel"
            val position = ShaftPosition.PORT

            val desiredBase = DocumentNaming.suggestedBaseName(
                jobNumber = job,
                customer = customer,
                vessel = vessel,
                suffix = position.printableLabelOrNull(),
            )!!

            // Existing user doc with the same base name.
            val userName = desiredBase + SHAFT_DOT_EXT
            File(shaftsDir, userName).writeText("USER")

            val assets = FakeAssets(
                mapOf(
                    "01_basic" + SHAFT_DOT_EXT to encodeDoc(
                        jobNumber = job,
                        customer = customer,
                        vessel = vessel,
                        position = position,
                        spec = ShaftSpec(overallLengthMm = 100f),
                    )
                )
            )
            val settings = FakeSettings(seedVersion = 0)

            val report = InternalStorage.seedBundledSamplesIfNeeded(shaftsDir, assets, settings)

            assertEquals(1, report.savedCount)
            assertEquals("USER", File(shaftsDir, userName).readText())

            val seededName = desiredBase + " (Sample)" + SHAFT_DOT_EXT
            assertTrue(File(shaftsDir, seededName).exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `force restore bypasses version gate and does not bump seed version`() = runBlocking {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)

            val job = "814111"
            val customer = "Harborline Propulsion"
            val vessel = "FV Silver Marlin"
            val position = ShaftPosition.STBD

            val desiredBase = DocumentNaming.suggestedBaseName(
                jobNumber = job,
                customer = customer,
                vessel = vessel,
                suffix = position.printableLabelOrNull(),
            )!!

            val assets = FakeAssets(
                mapOf(
                    "01_basic" + SHAFT_DOT_EXT to encodeDoc(
                        jobNumber = job,
                        customer = customer,
                        vessel = vessel,
                        position = position,
                        spec = ShaftSpec(overallLengthMm = 100f),
                    )
                )
            )

            // Pretend we already seeded on a previous run (already at current).
            val settings = FakeSettings(seedVersion = 4)

            val report = InternalStorage.seedBundledSamples(shaftsDir, assets, settings, force = true)

            assertEquals(1, report.attemptedCount)
            assertEquals(1, report.savedCount)
            assertEquals(0, report.failedCount)
            assertTrue(File(shaftsDir, desiredBase + SHAFT_DOT_EXT).exists())

            // Force restore intentionally does not update the seed-version gate.
            assertEquals(4, settings.seedVersion)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `force restore re-adds missing samples only and does not duplicate`() = runBlocking {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)

            val job = "814222"
            val customer = "NorthSound Marine"
            val vessel = "MV Copper Kestrel"
            val position = ShaftPosition.PORT

            val desiredBase = DocumentNaming.suggestedBaseName(
                jobNumber = job,
                customer = customer,
                vessel = vessel,
                suffix = position.printableLabelOrNull(),
            )!!

            // Existing user doc with the same base name.
            val userName = desiredBase + SHAFT_DOT_EXT
            File(shaftsDir, userName).writeText("USER")

            val assets = FakeAssets(
                mapOf(
                    "01_basic" + SHAFT_DOT_EXT to encodeDoc(
                        jobNumber = job,
                        customer = customer,
                        vessel = vessel,
                        position = position,
                        spec = ShaftSpec(overallLengthMm = 100f),
                    )
                )
            )

            val settings = FakeSettings(seedVersion = 4)

            val first = InternalStorage.seedBundledSamples(shaftsDir, assets, settings, force = true)
            assertEquals(1, first.savedCount)
            assertEquals("USER", File(shaftsDir, userName).readText())

            val seeded1 = desiredBase + " (Sample)" + SHAFT_DOT_EXT
            assertTrue(File(shaftsDir, seeded1).exists())

            val second = InternalStorage.seedBundledSamples(shaftsDir, assets, settings, force = true)
                        assertEquals(0, second.savedCount)
            assertEquals("USER", File(shaftsDir, userName).readText())

                        val seeded2 = desiredBase + " (Sample 2)" + SHAFT_DOT_EXT
                        assertTrue(!File(shaftsDir, seeded2).exists())

            // Still not bumped.
            assertEquals(4, settings.seedVersion)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `version bump prunes only ledger-tracked unmodified seeded samples`() = runBlocking {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)

            val job = "814333"
            val customer = "Harborline Propulsion"
            val vessel = "MV New Dawn"
            val position = ShaftPosition.STBD

            val desiredBase = DocumentNaming.suggestedBaseName(
                jobNumber = job,
                customer = customer,
                vessel = vessel,
                suffix = position.printableLabelOrNull(),
            )!!

            // Seed v1 for real so the ledger records the file + hash.
            val settings = FakeSettings(seedVersion = 0, currentSeedVersion = 1)
            val v1Assets = FakeAssets(
                mapOf(
                    "01_old" + SHAFT_DOT_EXT to encodeDoc(
                        jobNumber = job,
                        customer = customer,
                        vessel = vessel,
                        position = position,
                        spec = ShaftSpec(overallLengthMm = 111f),
                        notes = "[SAMPLE] old seeded",
                    )
                )
            )
            InternalStorage.seedBundledSamplesIfNeeded(shaftsDir, v1Assets, settings)
            assertEquals(1, settings.seedVersion)
            assertTrue(settings.seededHashes.containsKey(desiredBase + SHAFT_DOT_EXT))

            // Simulate an app update bumping the bundled seed version.
            settings.currentSeedVersion = 4
            val v4Assets = FakeAssets(
                mapOf(
                    "01_new" + SHAFT_DOT_EXT to encodeDoc(
                        jobNumber = job,
                        customer = customer,
                        vessel = vessel,
                        position = position,
                        spec = ShaftSpec(overallLengthMm = 123f),
                        notes = "[SAMPLE] new bundled",
                    )
                )
            )

            val report = InternalStorage.seedBundledSamplesIfNeeded(shaftsDir, v4Assets, settings)
            assertEquals("Expected 1 file seeded on version bump", 1, report.savedCount)
            assertEquals("Expected seed version to bump to 4", 4, settings.seedVersion)

            // Untouched seeded sample was replaced (pruned then re-added under its name).
            val updated = ShaftDocCodec.decode(File(shaftsDir, desiredBase + SHAFT_DOT_EXT).readText())
            assertEquals(123f, updated.spec.overallLengthMm)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `version bump keeps a seeded sample the user has edited`() = runBlocking {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)

            val job = "814444"
            val customer = "NorthSound Marine"
            val vessel = "MV Copper Kestrel"
            val position = ShaftPosition.PORT

            val desiredBase = DocumentNaming.suggestedBaseName(
                jobNumber = job,
                customer = customer,
                vessel = vessel,
                suffix = position.printableLabelOrNull(),
            )!!
            val seededName = desiredBase + SHAFT_DOT_EXT

            val settings = FakeSettings(seedVersion = 0, currentSeedVersion = 1)
            InternalStorage.seedBundledSamplesIfNeeded(
                shaftsDir,
                FakeAssets(
                    mapOf(
                        "01_old" + SHAFT_DOT_EXT to encodeDoc(
                            jobNumber = job, customer = customer, vessel = vessel,
                            position = position,
                            spec = ShaftSpec(overallLengthMm = 111f),
                        )
                    )
                ),
                settings,
            )

            // User opens the sample, edits it, and saves it back under the same
            // name — even keeping the [SAMPLE] notes marker. This was the exact
            // scenario that used to get silently deleted on update.
            val userEdited = encodeDoc(
                jobNumber = job, customer = customer, vessel = vessel,
                position = position,
                spec = ShaftSpec(overallLengthMm = 999f),
                notes = "[SAMPLE] still has the marker, but this is user work now",
            )
            File(shaftsDir, seededName).writeText(userEdited)

            settings.currentSeedVersion = 4
            InternalStorage.seedBundledSamplesIfNeeded(
                shaftsDir,
                FakeAssets(
                    mapOf(
                        "01_new" + SHAFT_DOT_EXT to encodeDoc(
                            jobNumber = job, customer = customer, vessel = vessel,
                            position = position,
                            spec = ShaftSpec(overallLengthMm = 123f),
                        )
                    )
                ),
                settings,
            )

            // The edited file survives untouched; the new sample lands beside it.
            val kept = ShaftDocCodec.decode(File(shaftsDir, seededName).readText())
            assertEquals(999f, kept.spec.overallLengthMm)
            assertTrue(File(shaftsDir, "$desiredBase (Sample)$SHAFT_DOT_EXT").exists())
            // Edited file is user data now — dropped from the ledger.
            assertTrue(!settings.seededHashes.containsKey(seededName) ||
                settings.seededHashes[seededName] != ShaftBackup.sha256Hex(userEdited))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `version bump never deletes files that predate the seed ledger`() = runBlocking {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)

            val job = "814555"
            val customer = "Harborline Propulsion"
            val vessel = "FV Silver Marlin"
            val position = ShaftPosition.STBD

            val desiredBase = DocumentNaming.suggestedBaseName(
                jobNumber = job,
                customer = customer,
                vessel = vessel,
                suffix = position.printableLabelOrNull(),
            )!!

            // A pre-ledger doc: canonical sample name AND the [SAMPLE] marker,
            // but no ledger entry (seeded by an older app version).
            val legacy = encodeDoc(
                jobNumber = job, customer = customer, vessel = vessel,
                position = position,
                spec = ShaftSpec(overallLengthMm = 111f),
                notes = "[SAMPLE] legacy seeded, no ledger",
            )
            File(shaftsDir, desiredBase + SHAFT_DOT_EXT).writeText(legacy)

            val settings = FakeSettings(seedVersion = 1, currentSeedVersion = 4)
            InternalStorage.seedBundledSamplesIfNeeded(
                shaftsDir,
                FakeAssets(
                    mapOf(
                        "01_new" + SHAFT_DOT_EXT to encodeDoc(
                            jobNumber = job, customer = customer, vessel = vessel,
                            position = position,
                            spec = ShaftSpec(overallLengthMm = 123f),
                        )
                    )
                ),
                settings,
            )

            // Legacy file kept; new sample seeded beside it with a suffix.
            val kept = ShaftDocCodec.decode(File(shaftsDir, desiredBase + SHAFT_DOT_EXT).readText())
            assertEquals(111f, kept.spec.overallLengthMm)
            assertTrue(File(shaftsDir, "$desiredBase (Sample)$SHAFT_DOT_EXT").exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
