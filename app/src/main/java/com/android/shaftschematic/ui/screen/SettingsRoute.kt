// file: app/src/main/java/com/android/shaftschematic/ui/screen/SettingsRoute.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.UiEvent
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.PreviewColorRole
import com.android.shaftschematic.util.PreviewColorSetting
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
    onOpenDeveloperOptions: () -> Unit,
) {
    val unit by vm.unit.collectAsState()
    val showGrid by vm.showGrid.collectAsState()
    val showComponentArrows by vm.showComponentArrows.collectAsState()
    val componentArrowWidthDp by vm.componentArrowWidthDp.collectAsState()
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
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (page) {
                                SettingsPage.MAIN -> onBack()
                                SettingsPage.PREVIEW_COLORS -> page = SettingsPage.MAIN
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = showGrid, onCheckedChange = { vm.setShowGrid(it) })
                        Spacer(Modifier.width(8.dp))
                        Text("Show Grid in Preview")
                    }

                    HorizontalDivider()
                    Text("PDF Export", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = openPdfAfterExport,
                            onCheckedChange = { vm.setOpenPdfAfterExport(it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Open PDF after export")
                    }

                    ListItem(
                        headlineContent = { Text("Preview Colors") },
                        supportingContent = { Text("Customize preview component colors") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { page = SettingsPage.PREVIEW_COLORS }
                    )

                    HorizontalDivider()
                    Text("Editor Screen", style = MaterialTheme.typography.titleMedium)
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
                    ListItem(
                        headlineContent = { Text("Restore sample shafts") },
                        supportingContent = { Text("Re-add bundled examples to Saved (won't overwrite your files)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.restoreSampleShafts() }
                    )

                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("About ShaftSchematic") },
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
                }
            }
        }
    }
}

private enum class SettingsPage { MAIN, PREVIEW_COLORS }

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
