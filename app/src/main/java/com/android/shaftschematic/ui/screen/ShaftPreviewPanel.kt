package com.android.shaftschematic.ui.screen

/**
 * ShaftPreviewPanel — preview drawing card + overlay badges.
 *
 * Houses the preview `Card`, the OAL badge, and the Free-to-End badge overlays.
 * Extracted verbatim from ShaftScreen.kt — pure code move, no behavior change.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.lastOccupiedEndMm
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.util.freeToEndSignedMm
import com.android.shaftschematic.util.PreviewColorSetting
import com.android.shaftschematic.util.UnitSystem

/* ───────────────── Preview ───────────────── */

@Composable
internal fun PreviewCard(
    showGrid: Boolean,
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>,
    unit: UnitSystem,
    overallIsManual: Boolean,
    devOptionsEnabled: Boolean,
    showOalInPreviewBox: Boolean,
    // NEW: explicit preview controls
    highlightEnabled: Boolean,
    highlightId: String?,
    onTapComponentId: ((String) -> Unit)?,
    showRenderLayoutDebugOverlay: Boolean,
    showRenderOalMarkers: Boolean,
    previewOutline: PreviewColorSetting,
    previewBodyFill: PreviewColorSetting,
    previewTaperFill: PreviewColorSetting,
    previewLinerFill: PreviewColorSetting,
    previewThreadFill: PreviewColorSetting,
    previewThreadHatch: PreviewColorSetting,
    previewBlackWhiteOnly: Boolean,
    lineThicknessScale: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Transparent)) {
            // Direct render: pass grid + highlight to the renderer
            ShaftDrawing(
                spec = spec,
                resolvedComponents = resolvedComponents,
                unit = unit,
                showGrid = showGrid,
                blackWhiteOnly = previewBlackWhiteOnly,
                previewOutline = previewOutline,
                previewBodyFill = previewBodyFill,
                previewTaperFill = previewTaperFill,
                previewLinerFill = previewLinerFill,
                previewThreadFill = previewThreadFill,
                previewThreadHatch = previewThreadHatch,
                lineThicknessScale = lineThicknessScale,
                highlightEnabled = highlightEnabled && (highlightId != null),
                highlightId = highlightId,
                onTapComponentId = onTapComponentId,
                showLayoutDebugOverlay = showRenderLayoutDebugOverlay,
                showOalMarkers = showRenderOalMarkers
            )

            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (devOptionsEnabled && showOalInPreviewBox) {
                    PreviewOalBadge(
                        spec = spec,
                        unit = unit,
                        overallIsManual = overallIsManual,
                    )
                }

                if (overallIsManual) {
                    FreeToEndBadge(
                        spec = spec,
                        unit = unit,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewOalBadge(
    spec: ShaftSpec,
    unit: UnitSystem,
    overallIsManual: Boolean,
    modifier: Modifier = Modifier,
) {
    val effectiveOalMm = remember(spec.overallLengthMm, spec.threads, spec.tapers) { computeOalWindow(spec).oalMm.toFloat() }
    val displayOalMm = if (overallIsManual) spec.overallLengthMm else effectiveOalMm

    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = modifier
    ) {
        Text(
            text = "OAL: ${formatDisplay(displayOalMm, unit)} ${abbr(unit)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** Latest occupied end position along the shaft (mm) from all components. */
internal fun lastOccupiedEndMm(spec: ShaftSpec): Float = spec.lastOccupiedEndMm()

/* ───────────────── Free-to-End badge ───────────────── */

@Composable
private fun FreeToEndBadge(
    spec: ShaftSpec,
    unit: UnitSystem,
    modifier: Modifier = Modifier
) {
    val freeSignedMm = freeToEndSignedMm(spec)
    val isOversized = freeSignedMm < 0f
    val isSnug = !isOversized && freeSignedMm < 10f

    // When there are no precision components (tapers/liners/threads), auto-bodies always fill
    // visually to OAL, so the badge would mislead the user into thinking there's uncovered
    // shaft. Only suppress in the non-oversized case — oversized is always a red warning.
    val hasPrecisionComponents = spec.tapers.isNotEmpty() ||
        spec.threads.any { !it.excludeFromOAL } ||
        spec.liners.isNotEmpty()
    if (!isOversized && !hasPrecisionComponents) return

    val bg = when {
        isOversized -> MaterialTheme.colorScheme.errorContainer
        isSnug      -> MaterialTheme.colorScheme.tertiaryContainer
        else        -> MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    }
    val fg = when {
        isOversized -> MaterialTheme.colorScheme.onErrorContainer
        isSnug      -> MaterialTheme.colorScheme.onTertiaryContainer
        else        -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        tonalElevation = if (isOversized) 3.dp else 2.dp,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = "Free to end: ${formatDisplay(freeSignedMm, unit)} ${abbr(unit)}",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
