package com.android.shaftschematic.doc

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem

/**
 * TemplateEnvelope — the geometry-only document a template is written as.
 *
 * A template carries a shaft's SHAPE and nothing about the job it came from, and the scrub happens
 * at WRITE time: what is not put in the envelope here never reaches the file, so no copy of that
 * file can leak a customer name into the next drawing.
 *
 * This function is the single definition of that envelope. `ShaftViewModel.exportTemplateJson`
 * delegates to it and `TemplateScrubTest` calls it directly — the test used to hand-copy the
 * envelope, which meant a new field added to the real writer could carry job data into templates
 * with the scrub test still green.
 *
 * Pure: it takes values rather than a ViewModel (an `AndroidViewModel` is not instantiable in the
 * JVM suite), so the thing the app writes is the thing the test checks.
 */

/**
 * The envelope a template is saved as: [spec] plus how it is authored, and nothing else.
 *
 * Included, deliberately: [preferredUnit] and [unitLocked] describe how the geometry was authored,
 * not whose job it is, and [unitOverrides] the same way — which features are metric is a fact about
 * the shaft. Excluded, deliberately: job number, customer, vessel, shaft position, notes, the
 * per-job `RunoutConfig` sheet tuning, the dual-display flag, and every measurement record (wear,
 * runout readings, station placements, undercuts).
 *
 * `station_interval_version` is not passed: [ShaftDocCodec.encodeV1] stamps it on every write.
 */
fun templateDocFor(
    spec: ShaftSpec,
    preferredUnit: UnitSystem,
    unitLocked: Boolean,
    unitOverrides: Map<String, UnitSystem> = emptyMap(),
): ShaftDocCodec.ShaftDocV1 = ShaftDocCodec.ShaftDocV1(
    preferredUnit = preferredUnit,
    unitLocked = unitLocked,
    spec = spec,
    unitOverrides = unitOverrides,
)

/** [templateDocFor], encoded — what `TemplateStorage.save` writes. */
fun encodeTemplateJson(
    spec: ShaftSpec,
    preferredUnit: UnitSystem,
    unitLocked: Boolean,
    unitOverrides: Map<String, UnitSystem> = emptyMap(),
): String = ShaftDocCodec.encodeV1(
    templateDocFor(spec, preferredUnit, unitLocked, unitOverrides)
)
