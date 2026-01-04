// file: app/src/main/java/com/android/shaftschematic/ui/screen/SettingsRoute.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
fun SettingsRoute(vm: ShaftViewModel, onBack: () -> Unit) {
    val unit by vm.unit.collectAsState()
    val showGrid by vm.showGrid.collectAsState()
    val showComponentArrows by vm.showComponentArrows.collectAsState()
    val componentArrowWidthDp by vm.componentArrowWidthDp.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                }
            )
        }
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
            Divider()
            Row {
                Switch(checked = showGrid, onCheckedChange = { vm.setShowGrid(it) })
                Spacer(Modifier.width(8.dp))
                Text("Show grid in preview")
            }

            Divider()
            Text("Components", style = MaterialTheme.typography.titleMedium)
            Row {
                Switch(
                    checked = showComponentArrows,
                    onCheckedChange = { vm.setShowComponentArrows(it) }
                )
                Spacer(Modifier.width(8.dp))
                Text("Show left/right arrows")
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
        }
    }
}
