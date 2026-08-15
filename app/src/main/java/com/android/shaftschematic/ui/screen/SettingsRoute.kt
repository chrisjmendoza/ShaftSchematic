
package com.android.shaftschematic.ui.screen
import androidx.compose.material3.RadioButton
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.pdf.PdfExportMode
// file: app/src/main/java/com/android/shaftschematic/ui/screen/SettingsRoute.kt

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import kotlin.math.roundToInt
import com.android.shaftschematic.BuildConfig
import com.android.shaftschematic.io.ShaftBackup
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.UiEvent
import com.android.shaftschematic.ui.viewmodel.*
import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.geom.defaultShaftHeightPt
import com.android.shaftschematic.settings.AppThemeMode
import com.android.shaftschematic.settings.PDF_CURVE_HEIGHT_MAX_IN
import com.android.shaftschematic.settings.PDF_CURVE_HEIGHT_MIN_IN
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.PreviewColorRole
import com.android.shaftschematic.util.PreviewColorSetting
import com.android.shaftschematic.util.UndercutShadeColor
import com.android.shaftschematic.util.UndercutShadeIntensity
import com.android.shaftschematic.util.UnitSystem

/**
 * SettingsRoute
 *
 * Purpose
 * User preferences that affect UI presentation and export UX.
 *
 * This screen is intentionally “preferences-only”:
 * - Units: changes formatting/labels, never the underlying model geometry (mm-only).
 * - Preview: visual aids (grid) and preview-only styling controls.
 * - Drawing: what every drawing looks like — default drawn shaft height and the
 *   body S-break threshold. Main page, not the PDF Export sub-page: they define the
 *   drawing, not the export.
 * - PDF export: user experience around exporting (not PDF styling).
 * - Editor screen: editor presentation toggles (e.g., component card affordances).
 *
 * Contract
 * - Unit selection must not mutate the model (mm-only). It only changes UI presentation.
 * - Settings are persisted via the ViewModel/SettingsStore; this route is a UI surface.
 * - Preview styling must never leak into PDF output (PDF has its own styling).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    vm: ShaftViewModel,
    onBack: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
) {
    val unit by vm.unit.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val highContrast by vm.highContrast.collectAsState()
    val undercutStyle by vm.undercutStyle.collectAsState()
    val showGrid by vm.showGrid.collectAsState()
    val showComponentArrows by vm.showComponentArrows.collectAsState()
    val componentArrowWidthDp by vm.componentArrowWidthDp.collectAsState()
    val showHighlightSelection by vm.showHighlightSelection.collectAsState()
    val achievementsEnabled by vm.achievementsEnabled.collectAsState()
    val devOptionsEnabled by vm.devOptionsEnabled.collectAsState()

    val previewOutline by vm.previewOutlineSetting.collectAsState()
    val previewBodyFill by vm.previewBodyFillSetting.collectAsState()
    val previewTaperFill by vm.previewTaperFillSetting.collectAsState()
    val previewLinerFill by vm.previewLinerFillSetting.collectAsState()
    val previewThreadFill by vm.previewThreadFillSetting.collectAsState()
    val previewThreadHatch by vm.previewThreadHatchSetting.collectAsState()
    val previewBlackWhiteOnly by vm.previewBlackWhiteOnly.collectAsState()

    val openPdfAfterExport by vm.openPdfAfterExport.collectAsState()
    val pdfTieringMode by vm.pdfTieringMode.collectAsState()
    val pdfShowComponentTitles by vm.pdfShowComponentTitles.collectAsState()
    val pdfShadedBodies by vm.pdfShadedBodies.collectAsState()
    val pdfShadedTapers by vm.pdfShadedTapers.collectAsState()
    val pdfShadedLiners by vm.pdfShadedLiners.collectAsState()
    val pdfExportMode by vm.pdfExportMode.collectAsState()
    val lineThicknessScale by vm.lineThicknessScale.collectAsState()
    val pdfCurveLoHeightIn by vm.pdfCurveLoHeightIn.collectAsState()
    val pdfCurveHiHeightIn by vm.pdfCurveHiHeightIn.collectAsState()
    val pdfSBreakThresholdFrac by vm.pdfSBreakThresholdFrac.collectAsState()
    val pdfWearTraceDepthFrac by vm.pdfWearTraceDepthFrac.collectAsState()
    val pdfArrowSizePt by vm.pdfArrowSizePt.collectAsState()
    val pdfFractionStyle by vm.pdfFractionStyle.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Collect one-shot VM events for transient UI feedback (e.g., restore samples).
    LaunchedEffect(Unit) {
        vm.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbarMessage -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> Unit
            }
        }
    }

    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (page) {
                            SettingsPage.MAIN -> "Settings"
                            SettingsPage.PREVIEW_COLORS -> "Preview Colors"
                            SettingsPage.PDF_EXPORT -> "PDF Export"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (page) {
                                SettingsPage.MAIN -> onBack()
                                SettingsPage.PREVIEW_COLORS -> page = SettingsPage.MAIN
                                SettingsPage.PDF_EXPORT -> page = SettingsPage.MAIN
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { pad ->
        when (page) {
            SettingsPage.MAIN -> {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(pad)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Units", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = unit == UnitSystem.MILLIMETERS,
                            onClick = { vm.setUnit(UnitSystem.MILLIMETERS) },
                            label = { Text("Millimeters") }
                        )
                        FilterChip(
                            selected = unit == UnitSystem.INCHES,
                            onClick = { vm.setUnit(UnitSystem.INCHES) },
                            label = { Text("Inches") }
                        )
                    }

                    HorizontalDivider()
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { vm.setThemeMode(mode) },
                                label = { Text(mode.uiLabel()) }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = highContrast,
                            onCheckedChange = { vm.setHighContrast(it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("High contrast")
                    }
                    Text(
                        "High contrast boosts figure/ground separation for bright sunlight or " +
                            "low-vision use. Drawing sheets and PDFs always stay white with dark ink.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("PDF Export Options") },
                        supportingContent = { Text("Template mode and PDF toggles") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { page = SettingsPage.PDF_EXPORT }
                    )

                    // Drawing sits on the main page, not under PDF Export: these two
                    // shape the drawing itself — how tall a shaft prints and when a
                    // foreshortened body admits it — while the PDF Export sub-page
                    // holds export plumbing (open-after-export, shading, template mode).
                    HorizontalDivider()
                    Text("Drawing", style = MaterialTheme.typography.titleMedium)

                    Text(
                        "Default drawing size",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                    Text(
                        "Drawn shaft height on paper at 100% “Shaft height”. Set what a " +
                            "4″ and an 8″ shaft draw; sizes between and beyond follow " +
                            "the line, capped at 1.5″ on paper.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CurveAnchorControl(
                        label = "4″ shaft draws",
                        valueIn = pdfCurveLoHeightIn,
                        onCommit = { vm.setPdfCurveLoHeightIn(it) },
                    )
                    CurveAnchorControl(
                        label = "8″ shaft draws",
                        valueIn = pdfCurveHiHeightIn,
                        onCommit = { vm.setPdfCurveHiHeightIn(it) },
                    )
                    if (pdfCurveHiHeightIn < pdfCurveLoHeightIn) {
                        Text(
                            "8″ is set below 4″ — larger shafts never draw smaller, so " +
                                "drawings flatten the line at the 4″ value.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val sixInHeight = defaultShaftHeightPt(
                            152.4f, pdfCurveLoHeightIn * 72f, pdfCurveHiHeightIn * 72f
                        ) / 72f
                        Text(
                            "Example: a 6″ shaft draws ${fmtCurveInches(sixInHeight)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                vm.setPdfCurveLoHeightIn(PdfPrefs().curveLoHeightIn)
                                vm.setPdfCurveHiHeightIn(PdfPrefs().curveHiHeightIn)
                            },
                            enabled = pdfCurveLoHeightIn != PdfPrefs().curveLoHeightIn ||
                                pdfCurveHiHeightIn != PdfPrefs().curveHiHeightIn,
                        ) {
                            Text(
                                "Standard (${fmtCurveInches(PdfPrefs().curveLoHeightIn)} / " +
                                    fmtCurveInches(PdfPrefs().curveHiHeightIn) + ")"
                            )
                        }
                    }

                    SBreakThresholdControl(
                        frac = pdfSBreakThresholdFrac,
                        onCommit = { vm.setPdfSBreakThresholdFrac(it) },
                    )

                    // The default a wear document follows until it pins its own value from the
                    // Wear tab's slider (WearRecord.traceDepthFrac).
                    WearTraceDepthControl(
                        frac = pdfWearTraceDepthFrac,
                        onCommit = { vm.setPdfWearTraceDepthFrac(it) },
                    )

                    // Same picker both PDF options sheets carry — one PdfPrefs.arrowSizePt.
                    DimensionArrowSizeChips(
                        arrowSizePt = pdfArrowSizePt,
                        onCommit = { vm.setPdfArrowSizePt(it) },
                    )

                    // Same picker both PDF options sheets carry — one PdfPrefs.fractionStyle.
                    // Unlike the rest of this section it also restyles the on-screen sheets:
                    // one renderer draws every fraction in the app.
                    FractionStyleChips(
                        fractionStyle = pdfFractionStyle,
                        onCommit = { vm.setPdfFractionStyle(it) },
                    )

                    HorizontalDivider()
                    Text("Editor Screen", style = MaterialTheme.typography.titleMedium)

                    LineThicknessControl(
                        scale = lineThicknessScale,
                        onScaleChange = { vm.setLineThicknessScale(it) }
                    )

                    ListItem(
                        headlineContent = { Text("Preview Colors") },
                        supportingContent = { Text("Customize preview component colors") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { page = SettingsPage.PREVIEW_COLORS }
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = showGrid, onCheckedChange = { vm.setShowGrid(it) })
                        Spacer(Modifier.width(8.dp))
                        Text("Show Grid in Preview")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = showHighlightSelection,
                            onCheckedChange = { vm.setShowHighlightSelection(it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Highlight Selected Component in Preview")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = showComponentArrows,
                            onCheckedChange = { vm.setShowComponentArrows(it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Show Left/Right Arrows on Component Cards")
                    }

                    // Keep it simple: three preset sizes.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val options = listOf(
                            32 to "Small",
                            40 to "Medium",
                            56 to "Large"
                        )
                        options.forEach { (dp, label) ->
                            FilterChip(
                                selected = componentArrowWidthDp == dp,
                                onClick = { vm.setComponentArrowWidthDp(dp) },
                                enabled = showComponentArrows,
                                label = { Text(label) }
                            )
                        }
                    }

                    HorizontalDivider()
                    Text("Achievements", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = achievementsEnabled,
                            onCheckedChange = { vm.setAchievementsEnabled(it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Enable Achievements")
                    }
                    ListItem(
                        headlineContent = { Text("View Achievements") },
                        supportingContent =
                            if (achievementsEnabled) null
                            else ({ Text("Enable achievements to view the list") }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = achievementsEnabled, onClick = onOpenAchievements)
                    )

                    HorizontalDivider()
                    Text("Data", style = MaterialTheme.typography.titleMedium)

                    val backupLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/zip")
                    ) { uri -> uri?.let(vm::backupAllShaftsTo) }
                    val restoreLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri -> uri?.let(vm::restoreShaftsFromBackup) }

                    ListItem(
                        headlineContent = { Text("Back up all shafts…") },
                        supportingContent = { Text("Save every shaft as one zip to a location you choose (Drive, Downloads, SD card)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                backupLauncher.launch(
                                    ShaftBackup.defaultBackupFilename(System.currentTimeMillis())
                                )
                            }
                    )
                    ListItem(
                        headlineContent = { Text("Restore from backup…") },
                        supportingContent = { Text("Import shafts from a backup zip — never overwrites, collisions are renamed") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                restoreLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                    )
                                )
                            }
                    )
                    ListItem(
                        headlineContent = { Text("Restore sample shafts") },
                        supportingContent = { Text("Re-add bundled examples to Saved (won't overwrite your files)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.restoreSampleShafts() }
                    )

                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Help & FAQ") },
                        supportingContent = { Text("How-to guides and common questions") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenHelp)
                    )

                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("About ShaftSchematic") },
                        supportingContent = {
                            Text(
                                "v${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenAbout)
                    )

                    if (devOptionsEnabled) {
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("Developer Options") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenDeveloperOptions)
                        )
                    }
                }
            }

            SettingsPage.PREVIEW_COLORS -> {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(pad)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Applies to Preview only", style = MaterialTheme.typography.bodySmall)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = previewBlackWhiteOnly,
                            onCheckedChange = { vm.setPreviewBlackWhiteOnly(it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Black/White Only")
                    }

                    if (previewBlackWhiteOnly) {
                        Text(
                            "Color fills disabled; outlines forced to black.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    HorizontalDivider()

                    PreviewColorRow(
                        title = "Outline",
                        value = previewOutline,
                        onChanged = vm::setPreviewOutlineSetting,
                        enabled = !previewBlackWhiteOnly
                    )
                    PreviewColorRow(
                        title = "Body Fill",
                        value = previewBodyFill,
                        onChanged = vm::setPreviewBodyFillSetting,
                        enabled = !previewBlackWhiteOnly
                    )
                    PreviewColorRow(
                        title = "Taper Fill",
                        value = previewTaperFill,
                        onChanged = vm::setPreviewTaperFillSetting,
                        enabled = !previewBlackWhiteOnly
                    )
                    PreviewColorRow(
                        title = "Liner Fill",
                        value = previewLinerFill,
                        onChanged = vm::setPreviewLinerFillSetting,
                        enabled = !previewBlackWhiteOnly
                    )
                    PreviewColorRow(
                        title = "Thread Fill",
                        value = previewThreadFill,
                        onChanged = vm::setPreviewThreadFillSetting,
                        enabled = !previewBlackWhiteOnly
                    )
                    PreviewColorRow(
                        title = "Thread Hatch",
                        value = previewThreadHatch,
                        onChanged = vm::setPreviewThreadHatchSetting,
                        enabled = !previewBlackWhiteOnly
                    )

                    HorizontalDivider()
                    Text("Undercut Drawing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Styles the on-screen Undercut Drawing. The printed PDF keeps standard drawing colors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = undercutStyle.lineArt,
                            onCheckedChange = { vm.setUndercutLineArt(it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Line art (no shading)")
                    }
                    if (undercutStyle.lineArt) {
                        Text(
                            "Everything draws white with black outlines only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text("Shade color", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UndercutShadeColor.entries.forEach { color ->
                            FilterChip(
                                selected = undercutStyle.shadeColor == color,
                                onClick = { vm.setUndercutShadeColor(color) },
                                enabled = !undercutStyle.lineArt,
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(color = color.base, shape = CircleShape)
                                        )
                                        Text(color.uiLabel())
                                    }
                                }
                            )
                        }
                    }

                    Text("Shade intensity", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UndercutShadeIntensity.entries.forEach { intensity ->
                            FilterChip(
                                selected = undercutStyle.intensity == intensity,
                                onClick = { vm.setUndercutShadeIntensity(intensity) },
                                enabled = !undercutStyle.lineArt,
                                label = { Text(intensity.uiLabel()) }
                            )
                        }
                    }
                }
            }

            SettingsPage.PDF_EXPORT -> {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(pad)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = openPdfAfterExport,
                            onCheckedChange = { vm.setOpenPdfAfterExport(it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Open PDF after export")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = pdfShowComponentTitles,
                            onCheckedChange = { vm.setPdfShowComponentTitles(it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Show component titles in PDF")
                    }

                    // Deliberately not the shared `ShadeInPdfChecks` block the two PDF
                    // options sheets use: this page stacks its rows in a spacedBy(12.dp)
                    // column with a padded heading, so adopting the sheets' tighter block
                    // would restyle the page. Same prefs, same setters.
                    Text(
                        "Shade in PDF",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pdfShadedBodies, onCheckedChange = { vm.setPdfShadedBodies(it) })
                        Spacer(Modifier.width(8.dp))
                        Text("Bodies")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pdfShadedTapers, onCheckedChange = { vm.setPdfShadedTapers(it) })
                        Spacer(Modifier.width(8.dp))
                        Text("Tapers")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pdfShadedLiners, onCheckedChange = { vm.setPdfShadedLiners(it) })
                        Spacer(Modifier.width(8.dp))
                        Text("Liners")
                    }

                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = pdfExportMode == PdfExportMode.Template,
                            onCheckedChange = { enabled ->
                                vm.setPdfExportMode(
                                    if (enabled) PdfExportMode.Template else PdfExportMode.Standard
                                )
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Template (shaft only)")
                    }

                    // --- PDF Tiering Mode ---
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Dimension tiering reference",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    Text(
                        "Controls whether dimensions reference AFT, FWD, or choose automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                    )
                    val tieringOptions = listOf(
                        PdfTieringMode.AUTO to "Auto (closest end)",
                        PdfTieringMode.AFT to "AFT (force AFT SET)",
                        PdfTieringMode.FWD to "FWD (force FWD SET)"
                    )
                    tieringOptions.forEach { (mode, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp)) {
                            RadioButton(
                                selected = pdfTieringMode == mode,
                                onClick = { vm.setPdfTieringMode(mode) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}

private enum class SettingsPage { MAIN, PREVIEW_COLORS, PDF_EXPORT }

/**
 * One sizing-curve anchor: label + value readout and a slider over the settable range,
 * quantized to 1/16″. Commits once on release — the drag is local, same posture as
 * [LineThicknessControl] (per-frame commits would write DataStore every frame).
 */
@Composable
private fun CurveAnchorControl(
    label: String,
    valueIn: Float,
    onCommit: (Float) -> Unit,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: valueIn
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label  ${fmtCurveInches(shown)}", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtCurveInches(PDF_CURVE_HEIGHT_MIN_IN), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = shown,
                onValueChange = { drag = (it * 16f).roundToInt() / 16f },
                onValueChangeFinished = {
                    drag?.let(onCommit)
                    drag = null
                },
                valueRange = PDF_CURVE_HEIGHT_MIN_IN..PDF_CURVE_HEIGHT_MAX_IN,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Text(fmtCurveInches(PDF_CURVE_HEIGHT_MAX_IN), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Body S-break threshold on the Settings page: the shared [SBreakThresholdSlider] — the same
 * control the two PDF options sheets carry — plus the explanatory caption this page has room
 * for. The slider itself (title, value, Default button, Never/Always track, 5% steps,
 * commit-on-release) lives once, in `ShaftHeightSlider.kt`.
 */
@Composable
private fun SBreakThresholdControl(
    frac: Float,
    onCommit: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SBreakThresholdSlider(frac = frac, onCommit = onCommit)
        Text(
            "A body run shows the S-break once it draws shorter than this much of its " +
                "true length. At Never, compression stays hidden and only very long runs " +
                "break. Bodies only — liners and tapers never break.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Worn-profile trace exaggeration on the Settings page: the shared [WearTraceDepthSlider] — the
 * same control the Wear tab carries — plus a reset to the shipped high end and the explanatory
 * caption this page has room for. A document that pins its own value from the Wear tab stops
 * following this one.
 */
@Composable
private fun WearTraceDepthControl(
    frac: Float,
    onCommit: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WearTraceDepthSlider(
            frac = frac,
            onCommit = onCommit,
            title = "Wear depth exaggeration",
            trailing = {
                TextButton(
                    onClick = { onCommit(WEAR_TRACE_MAX_DEPTH_FRAC) },
                    enabled = frac != WEAR_TRACE_MAX_DEPTH_FRAC,
                ) { Text("Default (${fmtTraceDepthPct(WEAR_TRACE_MAX_DEPTH_FRAC)})") }
            },
        )
        Text(
            "On the wear document, the deepest measured wear draws at this fraction of the " +
                "liner radius so a hairline cut still reads. Drawing only — printed Ø values " +
                "never change, and wear deeper than this keeps its true proportion.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Trimmed paper inches with the double-prime mark: 0.75″, 0.5″, 1.0625″. */
private fun fmtCurveInches(v: Float): String =
    String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.') + "″"

@Composable
private fun LineThicknessControl(
    scale: Float,
    onScaleChange: (Float) -> Unit,
) {
    // Local text field state — synced from scale when it changes externally
    var fieldText by remember(scale) { mutableStateOf((scale * 100).roundToInt().toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Line Thickness",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onScaleChange(1f) },
                enabled = scale != 1f,
            ) { Text("Default (100%)") }
        }
        Text(
            "Applies to preview and PDF output. 100% = default thin weight; 200% = original thick weight.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("50%", style = MaterialTheme.typography.bodySmall)
            // Track the drag locally; commit once on release so each drag frame
            // doesn't write DataStore (and re-render any open PDF preview).
            var sliderDrag by remember { mutableStateOf<Float?>(null) }
            Slider(
                value = sliderDrag ?: scale,
                onValueChange = { v ->
                    sliderDrag = v
                    fieldText = (v * 100).roundToInt().toString()
                },
                onValueChangeFinished = {
                    // Slider commits share the 100% detent with the options-sheet
                    // control; typed values in the % field are never snapped.
                    sliderDrag?.let { onScaleChange(snappedLineThickness(it)) }
                    sliderDrag = null
                },
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f),
            )
            Text("200%", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = fieldText,
                onValueChange = { raw ->
                    fieldText = raw
                    val parsed = raw.trim().trimEnd('%').toIntOrNull()
                    if (parsed != null) onScaleChange(parsed.coerceIn(50, 200) / 100f)
                },
                modifier = Modifier
                    .width(72.dp)
                    .onFocusChanged { focus ->
                        if (!focus.isFocused) {
                            val parsed = fieldText.trim().trimEnd('%').toIntOrNull()
                            val clamped = parsed?.coerceIn(50, 200) ?: (scale * 100).roundToInt()
                            onScaleChange(clamped / 100f)
                            fieldText = clamped.toString()
                        }
                    },
                suffix = { Text("%") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(onDone = {
                    val parsed = fieldText.trim().trimEnd('%').toIntOrNull()
                    val clamped = parsed?.coerceIn(50, 200) ?: (scale * 100).roundToInt()
                    onScaleChange(clamped / 100f)
                    fieldText = clamped.toString()
                }),
            )
        }
    }
}

@Composable
private fun PreviewColorRow(
    title: String,
    value: PreviewColorSetting,
    onChanged: (PreviewColorSetting) -> Unit,
    enabled: Boolean,
) {
    var presetExpanded by remember { mutableStateOf(false) }
    var paletteExpanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val swatch = value.resolve(scheme)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = if (swatch == Color.Transparent) scheme.surfaceVariant else swatch,
                        shape = CircleShape
                    )
            )
            Text(title)
        }

        Column(horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { presetExpanded = true }, enabled = enabled) {
                    Text(value.preset.uiLabel())
                }
                if (value.preset == PreviewColorPreset.CUSTOM) {
                    OutlinedButton(onClick = { paletteExpanded = true }, enabled = enabled) {
                        Text("Palette")
                    }
                }
            }

            DropdownMenu(expanded = presetExpanded, onDismissRequest = { presetExpanded = false }) {
                listOf(
                    PreviewColorPreset.STAINLESS,
                    PreviewColorPreset.STEEL,
                    PreviewColorPreset.BRONZE,
                    PreviewColorPreset.TRANSPARENT,
                    PreviewColorPreset.CUSTOM,
                ).forEach { preset ->
                    val presetSwatch = PreviewColorSetting(preset = preset, customRole = value.customRole).resolve(scheme)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(
                                            color = if (presetSwatch == Color.Transparent) scheme.surfaceVariant else presetSwatch,
                                            shape = CircleShape
                                        )
                                )
                                Text(preset.uiLabel())
                            }
                        },
                        onClick = {
                            onChanged(value.copy(preset = preset))
                            presetExpanded = false
                            if (preset == PreviewColorPreset.CUSTOM) paletteExpanded = true
                        }
                    )
                }
            }

            DropdownMenu(expanded = paletteExpanded, onDismissRequest = { paletteExpanded = false }) {
                PreviewColorRole.entries.forEach { role ->
                    val roleSwatch = role.resolve(scheme)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(
                                            color = if (roleSwatch == Color.Transparent) scheme.surfaceVariant else roleSwatch,
                                            shape = CircleShape
                                        )
                                )
                                Text(role.uiLabel())
                            }
                        },
                        onClick = {
                            val inferredPreset = when (role) {
                                PreviewColorRole.TRANSPARENT -> PreviewColorPreset.TRANSPARENT
                                PreviewColorRole.SURFACE_VARIANT -> PreviewColorPreset.STAINLESS
                                PreviewColorRole.OUTLINE -> PreviewColorPreset.STEEL
                                PreviewColorRole.TERTIARY -> PreviewColorPreset.BRONZE
                                else -> PreviewColorPreset.CUSTOM
                            }
                            if (inferredPreset == PreviewColorPreset.CUSTOM) {
                                onChanged(value.copy(preset = PreviewColorPreset.CUSTOM, customRole = role))
                            } else {
                                // Preserve the existing customRole so returning to Custom
                                // keeps the last true custom selection.
                                onChanged(value.copy(preset = inferredPreset))
                            }
                            paletteExpanded = false
                        }
                    )
                }
            }
        }
    }
}
