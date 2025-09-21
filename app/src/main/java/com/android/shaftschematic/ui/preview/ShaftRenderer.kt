package com.android.shaftschematic.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem

/**
 * ShaftRenderer (template)
 *
 * Draws the live shaft preview. Replace the contents with your actual renderer
 * (Canvas-based drawing, Layout coordinates, etc.). The screen draws the grid
 * underneath this composable.
 */
@Composable
fun ShaftRenderer(
    spec: ShaftSpec,
    unit: UnitSystem,
    modifier: Modifier = Modifier
) {
    // TODO: Replace with your real drawing. This is just a visible placeholder.
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("ShaftRenderer goes here (unit=$unit, overall=${spec.overallLengthMm}mm)")
    }
}
