package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.DyePenResult

/**
 * The dye penetrant result chips — Pass | Fail over the ONE `WearRecord.dyePenResult`, shown on
 * the Wear tab, the Consolidated Output tab and the consolidated preview's options panel from this
 * single construction. Selecting a chip marks that box on every sheet that prints the pair; the
 * other box stays present and blank. Tapping the selected chip deselects it, returning both boxes
 * to blank for hand-marking — none or one, never both (on-device request).
 */
@Composable
internal fun DyePenResultChips(
    result: DyePenResult?,
    onSelect: (DyePenResult?) -> Unit,
    label: String = "Dye pen inspection:",
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WearChip("Pass", result == DyePenResult.PASS, modifier = Modifier.testTag("dye_pen_pass")) {
            onSelect(if (result == DyePenResult.PASS) null else DyePenResult.PASS)
        }
        WearChip("Fail", result == DyePenResult.FAIL, modifier = Modifier.testTag("dye_pen_fail")) {
            onSelect(if (result == DyePenResult.FAIL) null else DyePenResult.FAIL)
        }
    }
}
