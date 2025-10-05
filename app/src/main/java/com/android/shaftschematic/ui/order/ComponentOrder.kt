package com.android.shaftschematic.ui.order

/**
 * UI-order key used by the editor list. The ViewModel owns a `List<ComponentKey>` that
 * defines the **rendered order across types** (Body/Taper/Thread/Liner). The screen
 * renders strictly by this list with no geometry-based sorting.
 */
enum class ComponentKind { BODY, TAPER, THREAD, LINER }

data class ComponentKey(
    val id: String,
    val kind: ComponentKind
)
