// file: app/src/main/java/com/android/shaftschematic/ui/screen/SettingsRoute.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.UnitSystem
import androidx.compose.runtime.collectAsState

/**
 * SettingsRoute
 *
 * Purpose
 * Minimal settings: measurement units for UI labels and grid visibility in Preview.
 *
 * Contract
 * - Unit selection must not mutate the model (mm-only). It only changes UI presentation.
 * - Grid is a visual-only toggle for Preview.
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

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).padding(16.dp),
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
}
