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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.THREAD_DENSITY_DEFAULT
import com.android.shaftschematic.geom.THREAD_SLANT_DEFAULT
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.lastOccupiedEndMm
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing
import com.android.shaftschematic.ui.drawing.render.PreviewIssueMark
import com.android.shaftschematic.ui.drawing.render.PreviewRailSettings
import com.android.shaftschematic.ui.drawing.render.PreviewSheetSettings
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
    /** Already ANDed with the Developer Options master switch by `ShaftRoute`. */
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
    /** App-wide thread-hatch slant (`PdfPrefs.threadSlant`) — see [ShaftDrawing]. */
    threadSlant: Float = THREAD_SLANT_DEFAULT,
    /** App-wide thread-hatch density (`PdfPrefs.threadDensity`) — see [ShaftDrawing]. */
    threadDensity: Float = THREAD_DENSITY_DEFAULT,
    /** PDF-shade mirror: components the PDF will print shaded — see [ShaftDrawing]. */
    shadedComponentIds: Set<String> = emptySet(),
    /** The warning banner's problems as red marks on the drawing — see [ShaftDrawing]. */
    issueMarks: List<PreviewIssueMark> = emptyList(),
    /** The sheet's dimension rails over the shaft, `null` = off — see [ShaftDrawing]. */
    dimensionRails: PreviewRailSettings? = null,
    /** The preview drawn like the schematic sheet, `null` = linear — see [ShaftDrawing]. */
    sheetLayout: PreviewSheetSettings? = null,
    /** Opens the preview's options sheet from the box's own corner button; `null` = no button. */
    onOpenOptions: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // The developer OAL badge repeats the OAL rail's value and sits where the rail block
    // starts, so it stands down while the rails draw one (a 0 OAL draws no rails; the badge
    // still shows the drawn extent then).
    val railsShowOal = dimensionRails != null && spec.overallLengthMm > 0f
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
                threadSlant = threadSlant,
                threadDensity = threadDensity,
                shadedComponentIds = shadedComponentIds,
                issueMarks = issueMarks,
                dimensionRails = dimensionRails,
                sheetLayout = sheetLayout,
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
                // The preview's options open from the preview itself (on-device request: the
                // overflow menu was not where anyone looked for them). The disc matches the
                // reset-view control in the opposite corner.
                if (onOpenOptions != null) {
                    IconButton(
                        onClick = onOpenOptions,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.6f), CircleShape)
                            .testTag(PREVIEW_OPTIONS_BUTTON_TAG),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Preview options",
                            tint = Color.Black.copy(alpha = 0.8f),
                        )
                    }
                }
                if (showOalInPreviewBox && !railsShowOal) {
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
