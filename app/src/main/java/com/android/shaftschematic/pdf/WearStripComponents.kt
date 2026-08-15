// app/src/main/java/com/android/shaftschematic/pdf/WearStripComponents.kt
package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.resolvedBodyBaseId
import com.android.shaftschematic.util.buildBodyTitleById
import com.android.shaftschematic.util.buildLinerTitleById
import com.android.shaftschematic.util.buildTaperTitleById

/**
 * The bridge between the resolved component list and the wear sheet's pure strip layout
 * (`pdf/WearStripLayout.kt`, which stays free of any `ui` import so its rules unit-test
 * directly). Kept beside the composer rather than in the layout file for exactly that reason.
 *
 * Both the printed strips and the wear PDF options sheet's "Components" checkboxes read these
 * two functions, so a checkbox and the strip it elects always carry the same title.
 */

/**
 * Every strip-eligible component — liner, taper, or body (explicit or auto) — flattened to
 * [WearStripComponent] and ordered AFT→FWD, keyed by **resolved component id** (the same key
 * wear pits and measured-Ø readings use).
 *
 * [resolved] is the ViewModel's resolved list; with it, split body runs and bare-shaft (auto)
 * spans are included exactly as they draw. Without it the raw [spec] is used, which has no auto
 * spans to offer — the same degradation every other composer path takes when a caller supplies
 * no resolved list.
 */
fun wearStripComponentsFor(
    spec: ShaftSpec,
    resolved: List<ResolvedComponent>?,
): List<WearStripComponent> {
    val out = if (resolved != null) {
        resolved.mapNotNull { rc ->
            when (rc) {
                is ResolvedLiner -> WearStripComponent(
                    rc.id, WearStripComponentKind.LINER,
                    rc.startMmPhysical, rc.endMmPhysical, rc.odMm, rc.odMm,
                )
                is ResolvedTaper -> WearStripComponent(
                    rc.id, WearStripComponentKind.TAPER,
                    rc.startMmPhysical, rc.endMmPhysical, rc.startDiaMm, rc.endDiaMm,
                )
                is ResolvedBody -> WearStripComponent(
                    rc.id, WearStripComponentKind.BODY,
                    rc.startMmPhysical, rc.endMmPhysical, rc.diaMm, rc.diaMm,
                    auto = rc.source == ResolvedComponentSource.AUTO,
                )
                else -> null   // threads and coupler bolt slots take no wear strip
            }
        }
    } else {
        buildList {
            spec.liners.forEach {
                add(WearStripComponent(
                    it.id, WearStripComponentKind.LINER,
                    it.startFromAftMm, it.startFromAftMm + it.lengthMm, it.odMm, it.odMm,
                ))
            }
            spec.tapers.forEach {
                add(WearStripComponent(
                    it.id, WearStripComponentKind.TAPER,
                    it.startFromAftMm, it.startFromAftMm + it.lengthMm, it.startDiaMm, it.endDiaMm,
                ))
            }
            spec.bodies.forEach {
                add(WearStripComponent(
                    it.id, WearStripComponentKind.BODY,
                    it.startFromAftMm, it.startFromAftMm + it.lengthMm, it.diaMm, it.diaMm,
                ))
            }
        }
    }
    return out.sortedBy { it.startMm }
}

/**
 * Display title per strip-eligible component id, from the SAME builders the carousel cards and
 * the rest of the printed sheet use (`util/LinerTitles.kt`, `util/TaperTitles.kt`,
 * `util/BodyTitles.kt`), so a strip's printed title and its options-sheet checkbox always read
 * the same.
 *
 * [spec] must be the authored spec, not a resolved one: a body split into several drawn runs
 * carries fragment ids (`"<id>#2"`), which are looked up by base id and then disambiguated with
 * a "(run n)" suffix so two rows are never indistinguishable. Bare-shaft (auto) spans have no
 * stored body to name and take the positional "Bare shaft" label the carousel shows.
 */
fun buildWearStripTitleById(
    spec: ShaftSpec,
    components: List<WearStripComponent>,
): Map<String, String> {
    val linerTitles = buildLinerTitleById(spec)
    val taperTitles = buildTaperTitleById(spec)
    val bodyTitles = buildBodyTitleById(spec)
    val autoCount = components.count { it.kind == WearStripComponentKind.BODY && it.auto }
    var autoSeen = 0
    val raw = components.map { c ->
        val label = when (c.kind) {
            WearStripComponentKind.LINER -> linerTitles[c.id] ?: "Liner"
            WearStripComponentKind.TAPER -> taperTitles[c.id] ?: "Taper"
            WearStripComponentKind.BODY -> if (c.auto) {
                autoSeen += 1
                if (autoCount > 1) "Bare shaft #$autoSeen" else "Bare shaft"
            } else {
                bodyTitles[resolvedBodyBaseId(c.id)] ?: "Body"
            }
        }
        c.id to label
    }
    val repeated = raw.groupingBy { it.second }.eachCount()
    val runSeen = mutableMapOf<String, Int>()
    return raw.associate { (id, label) ->
        if ((repeated[label] ?: 0) <= 1) id to label else {
            val n = (runSeen[label] ?: 0) + 1
            runSeen[label] = n
            id to "$label (run $n)"
        }
    }
}
