package com.android.shaftschematic.ui.order

/**
 * Component identity across types (Body/Taper/Thread/Liner/CouplerBoltSlot).
 *
 * [ComponentKind] tags rows, cards, snackbars and test tags. [ComponentKey] pairs a kind with
 * a stable id for the model-layer physical ordering helpers (`ShaftSpec.buildPhysicalKeyOrder`,
 * `snapForwardFrom`).
 *
 * It is NOT a display order: the carousel renders resolved components in physical position
 * order along the shaft, so there is no stored cross-type order to keep in sync. See
 * `docs/contracts/ComponentsOrdering.md`.
 */
enum class ComponentKind { BODY, TAPER, THREAD, LINER, COUPLER_BOLT_SLOT }

data class ComponentKey(
    val id: String,
    val kind: ComponentKind
)
