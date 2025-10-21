package com.android.shaftschematic.domain.model

/**
 * Anchor reference for a liner dimension.
 * AFT_SET: offset measured from aft SET to liner's aft edge; length goes FWD.
 * FWD_SET: offset measured from fwd SET to liner's fwd edge (toward AFT); length goes AFT.
 */
enum class LinerAnchor { AFT_SET, FWD_SET }
