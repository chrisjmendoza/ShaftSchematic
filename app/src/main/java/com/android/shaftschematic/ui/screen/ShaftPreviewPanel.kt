package com.android.shaftschematic.ui.screen

/**
 * ShaftPreviewPanel — preview drawing card + overlay badges.
 *
 * Houses the preview `Card` and the OAL badge overlay.
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
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.util.PreviewColorSetting
import com.android.shaftschematic.util.UnitSystem

/* ───────────────── Preview ───────────────── */

@Composable
internal fun PreviewCard(
    showGrid: Boolean,
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>,
    unit: UnitSystem,
    devOptionsEnabled: Boolean,
    showOalInPreviewBox: Boolean,
    // NEW: explicit preview controls
    highlightEnabled: Boolean,
    highlightId: String?,
    onTapComponentId: ((String) -> Unit)?,
    showRenderLayoutDebugOverlay: Boolean,
    showRenderOalMarkers: Boolean,
    showDimDebugOverlay: Boolean = false,
    pdfTieringMode: PdfTieringMode = PdfTieringMode.AUTO,
    previewOutline: PreviewColorSetting,
    previewBodyFill: PreviewColorSetting,
    previewTaperFill: PreviewColorSetting,
    previewLinerFill: PreviewColorSetting,
    previewThreadFill: PreviewColorSetting,
    previewThreadHatch: PreviewColorSetting,
    previewBlackWhiteOnly: Boolean,
    lineThicknessScale: Float = 1.0f,
    /** PDF-shade mirror: components the PDF will print shaded — see [ShaftDrawing]. */
    shadedComponentIds: Set<String> = emptySet(),
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
                shadedComponentIds = shadedComponentIds,
                highlightEnabled = highlightEnabled && (highlightId != null),
                highlightId = highlightId,
                onTapComponentId = onTapComponentId,
                showLayoutDebugOverlay = showRenderLayoutDebugOverlay,
                showOalMarkers = showRenderOalMarkers,
                showDimDebugOverlay = showDimDebugOverlay,
                pdfTieringMode = pdfTieringMode
            )

            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (devOptionsEnabled && showOalInPreviewBox) {
                    PreviewOalBadge(
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
    modifier: Modifier = Modifier,
) {
    val effectiveOalMm = remember(spec.overallLengthMm, spec.threads, spec.tapers) { computeOalWindow(spec).oalMm.toFloat() }
    // Mirrors the renderer's 0-OAL fallback: a not-yet-set length shows the drawn extent.
    val displayOalMm = if (spec.overallLengthMm > 0f) spec.overallLengthMm else effectiveOalMm

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
