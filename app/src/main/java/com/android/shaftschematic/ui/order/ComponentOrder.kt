package com.android.shaftschematic.ui.order

/** Cross-type ordering for the editor, keyed by stable component IDs. */
enum class ComponentKind { BODY, TAPER, THREAD, LINER }

data class ComponentKey(
    val id: String,
    val kind: ComponentKind
)
