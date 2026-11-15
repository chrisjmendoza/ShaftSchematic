package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.buildLinerTitleById as buildLinerTitleByIdShared

/**
 * Builds deterministic display titles for liner component cards.
 *
 * Rules:
 * - If a liner has a non-blank custom label, that label wins.
 * - Otherwise, use a default name computed from the liner's position on the shaft.
 * - Ordering is stable: sort by startMm (AFT→FWD), tie-break by stable id.
 */
fun buildLinerTitleById(spec: ShaftSpec): Map<String, String> {
    return buildLinerTitleByIdShared(spec)
}
