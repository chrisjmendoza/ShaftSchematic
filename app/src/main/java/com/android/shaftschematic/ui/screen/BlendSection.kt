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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.BlendProfile

/**
 * How one body face is finished: [SQUARE], or [BLEND] — a shoulder ramping from the neighbour's
 * diameter to the body's own. That is the whole of a face finish. A seal area is NOT a finish;
 * it is a property of the body section and has its own control (see [BlendSection]).
 */
enum class BlendFaceMode { SQUARE, BLEND }

/** Shop-facing chip text; the enum names stay the stored vocabulary. */
private fun BlendFaceMode.chipLabel(): String = when (this) {
    BlendFaceMode.SQUARE -> "Square"
    BlendFaceMode.BLEND -> "Blend"
}

/**
 * The face-finish and seal-area controls for a body — ONE structure shared by the carousel cards
 * (explicit and auto) and `AddBodyDialog`.
 *
 * Two independent questions, two sections:
 * - **Face finish** — per face, Square or Blend: whether the body END has a shoulder ramping up
 *   to its neighbour (e.g. to where the liner's full height begins) or is square.
 * - **Seal areas** — per face, on/off plus its own length: the radius cuts the fiberglass seats
 *   into. A seal area belongs to the body SECTION, not to the face finish, so a body can carry
 *   grooves at a square end, grooves behind a blended shoulder, or a shoulder with no grooves
 *   (on-device report: the photographed shaft has grooves on the fiberglassed body and, beyond
 *   them, a ramp up to the liner). The grooves start AT the face on a square end and just
 *   inboard of the ramp on a blended one.
 *
 * Both change the drawn geometry, so they live under the add-dialog-parity invariant, not the
 * card-only carve-out that covers "Show Ø on drawing" and the unit chip. Sharing the composable
 * is what keeps the surfaces from drifting: the length FIELDS are slots because the cards commit
 * on blur while the dialog holds local state until submit, but every control and every visibility
 * condition is decided here, once — a blend length under a blended face, a seal-area length under
 * a face whose seal is on.
 *
 * The seal-area checkboxes sit in their own section, a sibling of "Face finish" — never nested
 * under a blend control (a nested layout hid the seal behind a control nobody would think to tick
 * first — on-device report) and never folded into the finish chips as a third mode (a seal area
 * is a section property, not a face finish — on-device report). The profile row appears only once
 * some face is blended; with both square there is nothing for a profile to describe.
 */
@Composable
fun BlendSection(
    aftMode: BlendFaceMode,
    fwdMode: BlendFaceMode,
    profile: BlendProfile,
    aftSeal: Boolean,
    fwdSeal: Boolean,
    onSetAftMode: (BlendFaceMode) -> Unit,
    onSetFwdMode: (BlendFaceMode) -> Unit,
    onProfile: (BlendProfile) -> Unit,
    onSetAftSeal: (Boolean) -> Unit,
    onSetFwdSeal: (Boolean) -> Unit,
    aftLengthField: @Composable () -> Unit,
    fwdLengthField: @Composable () -> Unit,
    aftSealLengthField: @Composable () -> Unit,
    fwdSealLengthField: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Face finish",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        BlendFaceRow("AFT", aftMode, "body_blend_aft", onSetAftMode)
        if (aftMode == BlendFaceMode.BLEND) aftLengthField()
        BlendFaceRow("FWD", fwdMode, "body_blend_fwd", onSetFwdMode)
        if (fwdMode == BlendFaceMode.BLEND) fwdLengthField()

        if (aftMode == BlendFaceMode.BLEND || fwdMode == BlendFaceMode.BLEND) {
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

        Spacer(Modifier.height(8.dp))
        Text(
            "Seal areas",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SealFaceCheckbox("AFT", aftSeal, "body_seal_aft", onSetAftSeal, Modifier.weight(1f))
            SealFaceCheckbox("FWD", fwdSeal, "body_seal_fwd", onSetFwdSeal, Modifier.weight(1f))
        }
        if (aftSeal) aftSealLengthField()
        if (fwdSeal) fwdSealLengthField()
    }
}

/**
 * One face's seal-area checkbox: the whole row toggles (a 48 dp touch target), the checkbox
 * itself is display-only, matching the card's other checkbox rows.
 */
@Composable
private fun SealFaceCheckbox(
    label: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, color = MaterialTheme.colorScheme.onSurface)
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
 * One labelled row of mutually exclusive chips.
 *
 * Three rules keep the rows readable, all learned on device.
 *
 * The label sits **above** its chips rather than in a leading gutter. Inline, the gutter plus each
 * chip's ~32 dp of internal padding left barely 50 dp for text, so even "Square" ellipsized to
 * "Squ…"; dropping it hands the row its full width and roughly 40% more room per chip. It also
 * removes the alignment trap that caused the first symptom — "FWD" renders wider than "AFT", which
 * alone was enough to push that row's last chip onto a second line while the AFT row fit.
 *
 * The chips **share the remaining width equally**, so the longest label in a row ("Eased cone")
 * sizes every chip in it and none can wrap while its neighbours sit half empty. Text is one line
 * at `labelMedium` — a step down from the chip default, so "Eased cone" clears its box with
 * margin left for a large system font scale; any overflow past that ellipsizes visibly rather
 * than silently growing the row.
 *
 * **Every option keeps a visible outline, selected or not.** M3's default filter chip draws an
 * unselected chip with a transparent container and no border, which left "Square" and "Blend"
 * reading as plain words beside the one chip that looked like a control — so the way to turn a
 * face off did not look tappable at all (on-device: "I can't disable the blend or seal area").
 * The unselected outline is what makes this row read as a segmented choice; do not drop it back
 * to `border = null`.
 */
@Composable
internal fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    tagOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface,
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    )
    val restingBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    val chosenBorder = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { opt ->
                val on = opt == selected
                FilterChip(
                    selected = on,
                    onClick = { onSelect(opt) },
                    label = {
                        Text(
                            labelOf(opt),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    colors = colors,
                    border = if (on) chosenBorder else restingBorder,
                    modifier = Modifier.weight(1f).testTag(tagOf(opt)),
                )
            }
        }
    }
}
