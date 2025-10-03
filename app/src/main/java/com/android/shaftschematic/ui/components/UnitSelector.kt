package com.android.shaftschematic.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.UnitSystem
import androidx.compose.runtime.collectAsState

/**
 * UnitSelector
 *
 * Purpose
 * Inline unit picker for the editor. Changes **UI units only** (model stays mm).
 */
@Composable
fun UnitSelector(vm: ShaftViewModel, modifier: Modifier = Modifier) {
    val unit by vm.unit.collectAsState()

    Row(modifier = modifier) {
        FilterChip(
            selected = unit == UnitSystem.MILLIMETERS,
            onClick = { vm.setUnit(UnitSystem.MILLIMETERS) },
            label = { Text("mm") }
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = unit == UnitSystem.INCHES,
            onClick = { vm.setUnit(UnitSystem.INCHES) },
            label = { Text("in") }
        )
    }
}
