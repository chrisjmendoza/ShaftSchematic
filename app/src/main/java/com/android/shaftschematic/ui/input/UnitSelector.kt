package com.android.shaftschematic.ui.input

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.util.UnitSystem

@Composable
fun UnitSelector(
    unit: UnitSystem,
    onSelect: (UnitSystem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .semantics { contentDescription = "Unit selector" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selected uses FilledTonal to match your grid toggle’s “active” look
        val MmSelected = unit == UnitSystem.MILLIMETERS
        val inchesSelected = unit == UnitSystem.INCHES

        val shape = MaterialTheme.shapes.large

        if (MmSelected) {
            FilledTonalButton(
                onClick = { /* no-op when already selected */ },
                shape = shape,
                modifier = Modifier.weight(1f)
            ) { Text("mm") }
        } else {
            OutlinedButton(
                onClick = { onSelect(UnitSystem.MILLIMETERS) },
                shape = shape,
                modifier = Modifier.weight(1f)
            ) { Text("mm") }
        }

        if (inchesSelected) {
            FilledTonalButton(
                onClick = { /* no-op when already selected */ },
                shape = shape,
                modifier = Modifier.weight(1f)
            ) { Text("inch") }
        } else {
            OutlinedButton(
                onClick = { onSelect(UnitSystem.INCHES) },
                shape = shape,
                modifier = Modifier.weight(1f)
            ) { Text("inch") }
        }
    }
}
