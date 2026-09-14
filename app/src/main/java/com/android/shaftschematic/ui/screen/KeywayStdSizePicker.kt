// file: app/src/main/java/com/android/shaftschematic/ui/screen/KeywayStdSizePicker.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.android.shaftschematic.geom.KeyStockSize
import com.android.shaftschematic.geom.keyStockTable
import com.android.shaftschematic.geom.suggestedKeyStock
import com.android.shaftschematic.util.LengthFormat
import com.android.shaftschematic.util.UnitSystem

/**
 * KeywayStdSizePicker — the "Standard size…" menu of standard key stock, ONE composable shared by
 * the Body and Taper carousel cards and by `AddBodyDialog` / `AddTaperDialog` (add-dialog-parity
 * rule: it writes W and D, which are geometry, so it is NOT a card-only carve-out).
 *
 * It is a MENU, never an automation: nothing is written until the user opens it and taps an
 * entry, no Ø change fills a field, and an existing W × D is never rewritten behind the user's
 * back. A pick goes out through [onPick] in canonical mm and lands on the SAME commit path a
 * typed value takes, so from that moment the numbers are authored and sacred (golden rule).
 *
 * The table follows the KEYWAY's unit, not the document's — ANSI B17.1 for an inch keyway,
 * DIN 6885-1 for a metric one — because that chip already decides the unit the keyway is typed
 * and printed in. Both tables are provisional (see `geom/KeyStockStandards.kt`).
 *
 * [hostDiaMm] only steers which entry is offered FIRST, marked "Suggested"; every other size
 * stays reachable in table order, because the shop fits the key it has.
 */
@Composable
fun KeywayStdSizePicker(
    unit: UnitSystem,
    hostDiaMm: Float,
    modifier: Modifier = Modifier,
    onPick: (widthMm: Float, depthMm: Float) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val table = remember(unit) { keyStockTable(unit) }
    val suggested = remember(table, hostDiaMm) { suggestedKeyStock(table, hostDiaMm) }
    val ordered = remember(table, suggested) {
        if (suggested == null) table else listOf(suggested) + table.filter { it !== suggested }
    }

    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.testTag("keyway_std_size_button"),
        ) {
            Text("Standard size…", style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ordered.forEachIndexed { i, size ->
                val isSuggested = size === suggested
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(size.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (isSuggested) {
                                    "Suggested for Ø ${keyStockValue(hostDiaMm, unit)} ${abbr(unit)}"
                                } else {
                                    keyStockWxD(size, unit)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (isSuggested) {
                                Text(
                                    keyStockWxD(size, unit),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingIcon = if (isSuggested) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    onClick = { onPick(size.widthMm, size.depthMm); open = false },
                    modifier = Modifier.testTag("keyway_std_size_$i"),
                )
            }
        }
    }
}

/** "W × D" in the keyway's own unit — the numbers the pick is about to write. */
internal fun keyStockWxD(size: KeyStockSize, unit: UnitSystem): String =
    "${keyStockValue(size.widthMm, unit)} × ${keyStockValue(size.depthMm, unit)} ${abbr(unit)}"

/** Shop fractions in inches (the `dispKw` convention), plain trimmed decimals in mm. */
internal fun keyStockValue(mm: Float, unit: UnitSystem): String = when (unit) {
    UnitSystem.INCHES -> LengthFormat.formatInchesSmart(
        inches = mm.toDouble() / 25.4,
        opts = LengthFormat.InchFormatOptions(maxDenominator = 32),
    )
    UnitSystem.MILLIMETERS -> mm.fmtTrim(3)
}
