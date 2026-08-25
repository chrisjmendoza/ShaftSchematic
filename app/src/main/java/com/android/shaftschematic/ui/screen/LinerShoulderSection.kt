// file: app/src/main/java/com/android/shaftschematic/ui/screen/LinerShoulderSection.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.android.shaftschematic.geom.LINER_SHOULDER_STD_RADII_IN
import com.android.shaftschematic.util.UnitSystem
import java.util.Locale
import kotlin.math.abs

/**
 * LinerShoulderSection — the stepped-shoulder controls for a liner, ONE structure shared by the
 * carousel liner card and `AddLinerDialog` (add-dialog-parity rule: shoulders change drawn
 * geometry, so they are NOT a card-only carve-out).
 *
 * Visibility is the CALLER's decision, made from one rule stated here: the section shows when
 * the "Liner shoulders" Settings capability is on, OR when the liner already carries a shoulder
 * — a device pref may hide empty entry fields, never authored work.
 *
 * Per end: a None | Shoulder chip row; a shouldered end reveals its Length + Ø fields (slots —
 * the card commits on blur, the dialog holds local state, the BlendSection pattern) and the
 * edge-radius picker. The radius comes from a standard list ([LINER_SHOULDER_STD_RADII_IN],
 * provisional pending shop input) rather than free entry, and prints only as a footer note.
 * Selecting None zeroes the end's stored values, the blend section's Square posture.
 */
@Composable
fun LinerShoulderSection(
    aftOn: Boolean,
    fwdOn: Boolean,
    aftRadiusMm: Float,
    fwdRadiusMm: Float,
    unit: UnitSystem,
    onSetAftOn: (Boolean) -> Unit,
    onSetFwdOn: (Boolean) -> Unit,
    onSetAftRadiusMm: (Float) -> Unit,
    onSetFwdRadiusMm: (Float) -> Unit,
    aftFields: @Composable () -> Unit,
    fwdFields: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Shoulders",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ShoulderEndRow("AFT", aftOn, "liner_shoulder_aft", onSetAftOn)
        if (aftOn) {
            aftFields()
            ShoulderRadiusPicker("liner_shoulder_aft_radius", aftRadiusMm, unit, onSetAftRadiusMm)
        }
        ShoulderEndRow("FWD", fwdOn, "liner_shoulder_fwd", onSetFwdOn)
        if (fwdOn) {
            fwdFields()
            ShoulderRadiusPicker("liner_shoulder_fwd_radius", fwdRadiusMm, unit, onSetFwdRadiusMm)
        }
    }
}

@Composable
private fun ShoulderEndRow(
    label: String,
    on: Boolean,
    tagPrefix: String,
    onSet: (Boolean) -> Unit,
) {
    ChipRow(
        label = label,
        options = listOf(false, true),
        selected = on,
        labelOf = { if (it) "Shoulder" else "None" },
        tagOf = { "${tagPrefix}_${if (it) "on" else "off"}" },
        onSelect = onSet,
    )
}

/**
 * Edge-radius dropdown over the standard list. Stored canonical mm — a pick stores the exact
 * conversion of its inch value (golden rule: the number never drifts after selection). A stored
 * radius that matches no list entry (hand-edited file) still displays, formatted.
 */
@Composable
private fun ShoulderRadiusPicker(
    tag: String,
    radiusMm: Float,
    unit: UnitSystem,
    onSet: (Float) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.testTag(tag),
        ) {
            Text("Edge radius: ${radiusLabel(radiusMm, unit)}", style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LINER_SHOULDER_STD_RADII_IN.forEach { rIn ->
                val rMm = rIn * 25.4f
                DropdownMenuItem(
                    text = { Text(radiusLabel(rMm, unit)) },
                    onClick = { onSet(rMm); open = false },
                    modifier = Modifier.testTag("${tag}_${(rIn * 64).toInt()}_64"),
                )
            }
        }
    }
}

/** "Sharp", a scale fraction in inches, or compact mm — matching the entry unit. */
internal fun radiusLabel(radiusMm: Float, unit: UnitSystem): String {
    if (radiusMm <= 0f) return "Sharp"
    return if (unit == UnitSystem.INCHES) {
        val rIn = radiusMm / 25.4f
        val listed = LINER_SHOULDER_STD_RADII_IN.firstOrNull { abs(it - rIn) < 1e-4f }
        if (listed != null) {
            var n = (listed * 64).toInt(); var d = 64
            while (n % 2 == 0 && n > 0) { n /= 2; d /= 2 }
            "$n/$d\""
        } else String.format(Locale.US, "%.3f\"", rIn)
    } else {
        String.format(Locale.US, "%.1f mm", radiusMm)
    }
}
