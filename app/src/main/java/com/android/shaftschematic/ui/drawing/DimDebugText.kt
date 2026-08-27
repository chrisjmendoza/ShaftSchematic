package com.android.shaftschematic.ui.drawing

import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.LinerDim
import com.android.shaftschematic.settings.PdfTieringMode

/**
 * Pure text builder for the preview's optional dimension-debug overlay.
 *
 * Surfaces two independent tiering concerns at once, in one small block, so a developer can
 * see both without cross-referencing code: the tier ORIGIN — the single mm coordinate
 * dimension-rail tiering measures from ([tierOriginMm], via `tierOriginMmFor`) — and the
 * per-liner MEASUREMENT REFERENCE — which SET each liner ties to ([dims], via
 * `mapToLinerDimsForPdf`). These never conflate: origin is one shaft-wide coordinate (or
 * absent, under AUTO); reference is resolved per liner.
 */
internal fun dimDebugLines(
    mode: PdfTieringMode,
    tierOriginMm: Double?,
    dims: List<LinerDim>,
    maxLiners: Int = 6,
): List<String> = buildList {
    val originText = if (tierOriginMm == null) {
        "auto (L-to-R)"
    } else {
        "%.1fmm".format(tierOriginMm)
    }
    add("dim: mode=${mode.name} tierOrigin=$originText")

    dims.take(maxLiners).forEachIndexed { index, dim ->
        val anchorText = when (dim.anchor) {
            LinerAnchor.AFT_SET -> "AFT_SET"
            LinerAnchor.FWD_SET -> "FWD_SET"
        }
        val offsetText = "%.1f".format(dim.offsetFromSetMm)
        add("L${index + 1} → $anchorText +${offsetText}mm")
    }
    val overflow = dims.size - maxLiners
    if (overflow > 0) {
        add("… ($overflow more)")
    }
}
