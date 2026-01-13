package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.buildBodyTitleById as buildBodyTitleByIdShared

/** Deterministic display titles for bodies (aft→fwd physical order). */
fun buildBodyTitleById(spec: ShaftSpec): Map<String, String> {
    return buildBodyTitleByIdShared(spec)
}
