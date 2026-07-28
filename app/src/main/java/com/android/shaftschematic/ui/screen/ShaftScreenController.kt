package com.android.shaftschematic.ui.screen

/**
 * ShaftScreenController — add-defaults helpers backing ShaftScreen.
 *
 * Extracted from ShaftScreen.kt. The applySnapped{Body,Taper,Thread,Liner}Update
 * wrappers that lived here were removed: they snapped typed field commits to
 * component-edge anchors (±1 mm), silently rewriting values the user just entered (a
 * sub-tolerance taper-length edit was undone entirely). Typed values are exact; snapping
 * is reserved for coarse gestures (tap-to-add, `ui/viewmodel/SnapUtils.kt`).
 */

import com.android.shaftschematic.model.ShaftSpec

/** Defaults for new components (mm). */
internal data class AddDefaults(val startMm: Float, val lastDiaMm: Float)
internal fun computeAddDefaults(spec: ShaftSpec): AddDefaults {
    // Bodies are fillers; excluded threads sit outside the shaft envelope.
    // Only sacred components (tapers, non-excluded threads, liners) drive the default start position.
    var end = 0f
    spec.tapers.forEach  { end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.threads.filter { !it.excludeFromOAL }.forEach { end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach  { end = maxOf(end, it.startFromAftMm + it.lengthMm) }

    var dia = 50f
    spec.liners.firstOrNull  { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.odMm }
    spec.threads.filter { !it.excludeFromOAL }.firstOrNull { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.majorDiaMm }
    spec.tapers.firstOrNull  { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.endDiaMm }
    if (dia == 50f && spec.bodies.isNotEmpty()) dia = spec.bodies.first().diaMm

    return AddDefaults(startMm = end, lastDiaMm = dia)
}
