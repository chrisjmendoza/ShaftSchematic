// file: app/src/main/java/com/android/shaftschematic/ui/screen/BlendSection.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.BlendProfile

/**
 * How one body face is finished. Three mutually exclusive states, because that is how the shop
 * describes a face: it is square, or it is blended, or it is a seal area.
 *
 * [SEAL] **includes** the blend — the radius cuts the fiberglass seats into are machined across
 * the blended section running up to the liner, so there is nowhere to put them on a square face.
 * Presenting the two as alternatives is therefore a small, deliberate fiction: it matches the
 * shop's mental model (a seal area is a thing you add, not a modifier on something else) and
 * costs nothing, since the stored model still carries a length and a flag independently.
 */
enum class BlendFaceMode { SQUARE, BLEND, SEAL }

/** Shop-facing chip text; the enum names stay the stored vocabulary. */
private fun BlendFaceMode.chipLabel(): String = when (this) {
    BlendFaceMode.SQUARE -> "Square"
    BlendFaceMode.BLEND -> "Blend"
    BlendFaceMode.SEAL -> "Seal area"
}

/**
 * The face-finish controls for a body — ONE structure shared by the carousel cards (explicit and
 * auto) and `AddBodyDialog`.
 *
 * A blend changes the drawn geometry, so it lives under the add-dialog-parity invariant, not the
 * card-only carve-out that covers "Show Ø on drawing" and the unit chip. Sharing the composable
 * is what keeps the surfaces from drifting: the length FIELD is a slot because the cards commit
 * on blur while the dialog holds local state until submit, but every control and every visibility
 * condition is decided here, once.
 *
 * Each face gets one chip row rather than a checkbox with a nested one. The nesting hid the seal
 * area behind a control nobody would think to tick first (on-device: "I was thinking a body could
 * have a blend OR a seal area") — and a mode row also reads better beside the profile chips, which
 * are already chips. The profile row itself appears only once some face is finished; with both
 * square there is nothing for a profile to describe.
 */
@Composable
fun BlendSection(
    aftMode: BlendFaceMode,
    fwdMode: BlendFaceMode,
    profile: BlendProfile,
    onSetAftMode: (BlendFaceMode) -> Unit,
    onSetFwdMode: (BlendFaceMode) -> Unit,
    onProfile: (BlendProfile) -> Unit,
    aftLengthField: @Composable () -> Unit,
    fwdLengthField: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Face finish",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        BlendFaceRow("AFT", aftMode, "body_blend_aft", onSetAftMode)
        if (aftMode != BlendFaceMode.SQUARE) aftLengthField()
        BlendFaceRow("FWD", fwdMode, "body_blend_fwd", onSetFwdMode)
        if (fwdMode != BlendFaceMode.SQUARE) fwdLengthField()

        if (aftMode != BlendFaceMode.SQUARE || fwdMode != BlendFaceMode.SQUARE) {
            Spacer(Modifier.height(8.dp))
            ChipRow(
                label = "Shape",
                options = BlendProfile.values().toList(),
                selected = profile,
                labelOf = { it.chipLabel() },
                tagOf = { "body_blend_profile_${it.name}" },
                onSelect = onProfile,
            )
        }
    }
}

/** Shop-facing chip text for a curve shape. */
private fun BlendProfile.chipLabel(): String = when (this) {
    BlendProfile.OGEE -> "S-curve"
    BlendProfile.FILLET -> "Fillet"
    BlendProfile.EASED_CONE -> "Eased cone"
}

@Composable
private fun BlendFaceRow(
    label: String,
    mode: BlendFaceMode,
    tagPrefix: String,
    onSelect: (BlendFaceMode) -> Unit,
) {
    ChipRow(
        label = label,
        options = BlendFaceMode.values().toList(),
        selected = mode,
        labelOf = { it.chipLabel() },
        tagOf = { "${tagPrefix}_${it.name.lowercase()}" },
        onSelect = onSelect,
    )
}

/**
 * One labelled row of mutually exclusive chips, styled like the card's other chip pairs
 * (KW from AFT|FWD, keyway clocking) so the section does not read as a foreign control.
 */
@Composable
private fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    tagOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.forEach { opt ->
            val on = opt == selected
            FilterChip(
                selected = on,
                onClick = { onSelect(opt) },
                label = { Text(labelOf(opt)) },
                colors = colors,
                border = if (on) BorderStroke(1.dp, Color.Black) else null,
                modifier = Modifier.testTag(tagOf(opt)),
            )
        }
    }
}
