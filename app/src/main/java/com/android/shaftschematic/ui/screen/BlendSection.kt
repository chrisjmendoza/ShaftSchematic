// file: app/src/main/java/com/android/shaftschematic/ui/screen/BlendSection.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.BlendProfile

/**
 * The blend controls for a body face — ONE structure shared by the carousel card and
 * `AddBodyDialog`.
 *
 * A blend changes the drawn geometry, so it lives under the add-dialog-parity invariant, not
 * the card-only carve-out that covers "Show Ø on drawing" and the unit chip. Sharing the
 * composable is what keeps the two surfaces from drifting: the length FIELD is a slot because
 * the card commits on blur while the dialog holds local state until submit, but every control
 * and every visibility condition is decided here, once.
 *
 * The profile chips appear only once a face is blended — with both faces square there is
 * nothing for a profile to describe.
 */
@Composable
fun BlendSection(
    aftOn: Boolean,
    fwdOn: Boolean,
    profile: BlendProfile,
    onToggleAft: (Boolean) -> Unit,
    onToggleFwd: (Boolean) -> Unit,
    onProfile: (BlendProfile) -> Unit,
    aftLengthField: @Composable () -> Unit,
    fwdLengthField: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Blend",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BlendFaceRow("Blend AFT face", aftOn, "body_blend_aft_toggle", onToggleAft)
        if (aftOn) aftLengthField()
        BlendFaceRow("Blend FWD face", fwdOn, "body_blend_fwd_toggle", onToggleFwd)
        if (fwdOn) fwdLengthField()

        if (aftOn || fwdOn) {
            Spacer(Modifier.height(8.dp))
            val selectedColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BlendProfile.values().forEach { p ->
                    val on = p == profile
                    FilterChip(
                        selected = on,
                        onClick = { onProfile(p) },
                        label = { Text(p.chipLabel()) },
                        colors = selectedColors,
                        border = if (on) BorderStroke(1.dp, Color.Black) else null,
                        modifier = Modifier.testTag("body_blend_profile_${p.name}"),
                    )
                }
            }
        }
    }
}

/** Shop-facing chip text; the enum names stay the stored vocabulary. */
private fun BlendProfile.chipLabel(): String = when (this) {
    BlendProfile.OGEE -> "S-curve"
    BlendProfile.FILLET -> "Fillet"
    BlendProfile.EASED_CONE -> "Eased cone"
}

@Composable
private fun BlendFaceRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.testTag(testTag))
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}
