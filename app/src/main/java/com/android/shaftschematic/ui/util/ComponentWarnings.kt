package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads

private const val SHORT_SEGMENT_MM = 1f

fun bodyWarningMessage(body: Body): String? {
    if (body.lengthMm in Float.MIN_VALUE..SHORT_SEGMENT_MM) return "Very short segment (< 1 mm)"
    return null
}

fun taperWarningMessage(taper: Taper): String? {
    if (taper.lengthMm in Float.MIN_VALUE..SHORT_SEGMENT_MM) return "Very short segment (< 1 mm)"
    return null
}

fun threadWarningMessage(thread: Threads): String? {
    if (thread.pitchMm == 0f) return "Zero pitch — thread renders flat"
    if (thread.lengthMm in Float.MIN_VALUE..SHORT_SEGMENT_MM) return "Very short segment (< 1 mm)"
    return null
}

fun linerWarningMessage(liner: Liner): String? {
    if (liner.lengthMm in Float.MIN_VALUE..SHORT_SEGMENT_MM) return "Very short segment (< 1 mm)"
    return null
}
