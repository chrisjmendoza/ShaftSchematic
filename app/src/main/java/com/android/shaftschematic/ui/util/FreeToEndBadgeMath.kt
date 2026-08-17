package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.lastOccupiedEndMm

/**
 * File: FreeToEndBadgeMath.kt
 * Layer: UI (pure helper — no Compose, no side effects)
 * Purpose: The Free-to-End badge's **value contract** in one testable place.
 *
 * Signed remaining length (mm) from the last occupied component end to the shaft's
 * *effective* overall length:
 *
 *   freeSignedMm = effectiveOalMm − lastOccupiedEndMm(spec)
 *
 * The effective OAL is [ShaftSpec.overallLengthMm], **except** when that is exactly `0f`
 * (manual-OAL mode with no length entered yet). In that case the effective OAL is
 * [ShaftSpec.lastOccupiedEndMm], which **mirrors the preview's `safeSpec`**
 * (`ShaftDrawing.kt`: OAL 0 → extend to the last occupied end). This yields `0f` instead of
 * a phantom negative "oversized" value while the preview is happily drawing a full shaft.
 *
 * It is **not** clamped otherwise: a genuinely oversized shaft (OAL > 0 with components
 * running past the end) must still return a negative value so the badge shows its red
 * warning. Only the OAL-is-zero placeholder case is special-cased.
 *
 * Positive → free room remains; `0f` → exactly filled (or the OAL-zero placeholder);
 * negative → oversized.
 *
 * See docs/contracts/FreeToEndBadge.md.
 */
fun freeToEndSignedMm(spec: ShaftSpec): Float {
    val effectiveOalMm =
        if (spec.overallLengthMm == 0f) spec.lastOccupiedEndMm() else spec.overallLengthMm
    return effectiveOalMm - spec.lastOccupiedEndMm()
}
