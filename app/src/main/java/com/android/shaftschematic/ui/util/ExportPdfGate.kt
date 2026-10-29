package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec

/** Whether the toolbar Export-PDF action is available, and why not when it isn't. */
data class ExportPdfGate(val enabled: Boolean, val disabledMessage: String)

/**
 * Export is available only when the shaft has at least one real component and no
 * collisions. Coupler bolt slots are reference features and do not count — a
 * slots-only spec has nothing dimensionable to export.
 *
 * A collision names the parts ("Can't export: FWD Liner overlaps …") through
 * [overlapExportMessage] — the warning banner's own first overlap line — so the greyed button and
 * the banner point at the same two parts.
 */
fun exportPdfGate(spec: ShaftSpec, collidingIds: Set<String>): ExportPdfGate {
    val hasComponents = spec.bodies.isNotEmpty() || spec.tapers.isNotEmpty() ||
        spec.threads.isNotEmpty() || spec.liners.isNotEmpty()
    val disabledMessage = if (collidingIds.isNotEmpty())
        overlapExportMessage(spec) ?: FIX_COLLISIONS_MSG
    else
        "Please add at least 1 component before export is active."
    return ExportPdfGate(
        enabled = hasComponents && collidingIds.isEmpty(),
        disabledMessage = disabledMessage
    )
}

/** The gate's refusal when the collision set names no pair the spec still holds. */
internal const val FIX_COLLISIONS_MSG = "Fix component collisions before exporting."
