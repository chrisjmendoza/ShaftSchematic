package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.buildThreadTitleById as buildThreadTitleByIdShared

/**
 * Deterministic display titles for threads.
 *
 * Rules:
 * - Threads are grouped into AFT vs FWD by physical position along the shaft.
 * - Numbering is shown only when more than one thread shares the same end.
 * - Ordering is stable: sort by startMm (AFT→FWD), tie-break by stable id.
 */
fun buildThreadTitleById(spec: ShaftSpec): Map<String, String> {
    return buildThreadTitleByIdShared(spec)
}
