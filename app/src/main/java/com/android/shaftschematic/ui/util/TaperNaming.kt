package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.buildTaperTitleById as buildTaperTitleByIdShared

/**
 * Deterministic display titles for tapers.
 *
 * Rules:
 * - Direction is based on explicit taper orientation (AFT/FWD).
 * - Numbering is shown only when more than one taper shares the same direction.
 * - Ordering is stable: sort by startMm (AFT→FWD), tie-break by stable id.
 */
fun buildTaperTitleById(spec: ShaftSpec): Map<String, String> {
    return buildTaperTitleByIdShared(spec)
}
