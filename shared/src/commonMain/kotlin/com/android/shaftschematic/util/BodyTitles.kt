package com.android.shaftschematic.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftSpec

/** Deterministic display titles for bodies (aft→fwd physical order). Custom label wins when set. */
fun buildBodyTitleById(spec: ShaftSpec): Map<String, String> {
    val bodies = spec.bodies
    if (bodies.isEmpty()) return emptyMap()

    val sorted = bodies.sortedWith(compareBy<Body>({ it.startFromAftMm }, { it.id }))
    return sorted
        .mapIndexed { i, b ->
            val label = b.label?.trim()?.takeIf { it.isNotEmpty() }
            b.id to (label ?: "Body #${i + 1}")
        }
        .toMap()
}
