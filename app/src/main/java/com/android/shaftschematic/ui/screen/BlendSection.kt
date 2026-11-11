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
import com.android.shaftschematic.geom.isValidBlendAngle
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.parseFractionOrDecimal

/**
 * How one body face meets its neighbour — the "Component blend" chips: [SQUARE], or [BLEND] — a
 * shoulder ramping from the neighbour's diameter to the body's own. That is the whole of the
 * blend. A seal area is NOT part of the blend; it is a property of the body section and has its
 * own control (see [BlendSection]).
 */
enum class BlendFaceMode { SQUARE, BLEND }

/** Shop-facing chip text; the enum names stay the stored vocabulary. */
private fun BlendFaceMode.chipLabel(): String = when (this) {
    BlendFaceMode.SQUARE -> "Square"
    BlendFaceMode.BLEND -> "Blend"
}

/**
 * Where a blended face's curve leaves from when a LINER butts it — the "At a liner" chips.
 * [SEAT] (the default) is the derived half-way shoulder between the liner OD and the body Ø;
 * [LINER_OD] runs the curve all the way up to the liner's OD, a short cone with no seat
 * (on-device request). UI-edge vocabulary over the stored `…ToLinerOd` booleans, the
 * [BlendFaceMode]-over-length posture.
 */
enum class BlendLinerTarget { SEAT, LINER_OD }

/** The chip a stored `…ToLinerOd` flag selects. */
fun blendLinerTarget(toLinerOd: Boolean): BlendLinerTarget =
    if (toLinerOd) BlendLinerTarget.LINER_OD else BlendLinerTarget.SEAT

/** The stored flag a chip writes. */
val BlendLinerTarget.toLinerOd: Boolean get() = this == BlendLinerTarget.LINER_OD

private fun BlendLinerTarget.chipLabel(): String = when (this) {
    BlendLinerTarget.SEAT -> "Seat"
    BlendLinerTarget.LINER_OD -> "Liner OD"
}

/**
 * How a blended face is ENTERED — the "Blend by: Length | Angle" chips (on-device request: "some
 * blends have expressions in degrees"). [LENGTH] types the curve's axial length; [ANGLE] types the
 * angle from the shaft axis and an optional corner radius, and the length is DERIVED from the
 * diameter step. UI-edge vocabulary over the stored nullable angle (null = Length), the
 * [BlendFaceMode]-over-length posture.
 */
enum class BlendEntry { LENGTH, ANGLE }

/** The chip a stored angle selects: none is Length mode. */
fun blendEntry(angleDeg: Float?): BlendEntry = if (angleDeg == null) BlendEntry.LENGTH else BlendEntry.ANGLE

private fun BlendEntry.chipLabel(): String = when (this) {
    BlendEntry.LENGTH -> "Length"
    BlendEntry.ANGLE -> "Angle"
}

/**
 * Starting angle when a face is first switched to Angle — the common 45° chamfer. A starting value
 * only, like [defaultBlendMm]: the user types over it, and nothing re-derives it afterwards.
 */
const val DEFAULT_BLEND_ANGLE_DEG = 45f

/** The angle a field's text reads as (degrees; a trailing `°` is allowed), or null when unreadable. */
internal fun parseBlendAngleDeg(raw: String): Float? =
    parseFractionOrDecimal(raw.trim().removeSuffix("°").trim())?.toFloat()

/** The refusal an angle field shows for [raw], or null when it is a usable angle. */
internal fun blendAngleError(raw: String): String? {
    val deg = parseBlendAngleDeg(raw) ?: return "Enter an angle in degrees"
    return if (isValidBlendAngle(deg)) null else BLEND_ANGLE_RANGE_MSG
}

internal const val BLEND_ANGLE_RANGE_MSG = "Angle must be between 0° and 90° from the axis"

/** The refusal a corner-radius field shows for a parsed value, or null when it is usable. Blank is 0. */
internal fun blendCornerRadiusError(mm: Float?): String? = when {
    mm == null -> "Enter a number"
    mm < 0f -> "Radius can't be negative"
    else -> null
}

/**
 * The read-only line under an Angle-mode face's angle field: the cone length the angle derives from
 * the face's diameter step ([coneLenMm], from `angleBlendConeLengthMm`), or a hint that there is no
 * step to chamfer yet. [display] formats a length in the field unit (the cards' `disp`).
 */
internal fun angleBlendNote(coneLenMm: Float?, unit: UnitSystem, display: (Float, UnitSystem) -> String): String =
    if (coneLenMm == null) ANGLE_BLEND_NO_STEP_NOTE
    else "Length ${display(coneLenMm, unit)} ${abbr(unit)} from the step"

internal const val ANGLE_BLEND_NO_STEP_NOTE =
    "No diameter step at this face — the length follows the step once there is one"

/**
 * The component-blend and seal-area controls for a body — ONE structure shared by the carousel
 * cards (explicit and auto) and `AddBodyDialog`.
 *
 * Two independent questions, two sections:
 * - **Component blend** — per face, Square or Blend: whether the body END has a shoulder ramping
 *   up to its neighbour (e.g. to where the liner's full height begins) or is square. A blended
 *   face also says where the curve leaves from when a liner butts it ("At a liner": the derived
 *   half-way Seat, or the full Liner OD).
 * - **Seal areas** — per face, on/off plus its own length: the radius cuts the fiberglass seats
 *   into. A seal area belongs to the body SECTION, not to the blend, so a body can carry
 *   grooves at a square end, grooves behind a blended shoulder, or a shoulder with no grooves
 *   (on-device report: the photographed shaft has grooves on the fiberglassed body and, beyond
 *   them, a ramp up to the liner). The seal area is measured from the face either way, so it
 *   sits in the same place whether the end is square or blended.
 *
 * Both change the drawn geometry, so they live under the add-dialog-parity invariant, not the
 * card-only carve-out that covers "Show Ø on drawing" and the unit chip. Sharing the composable
 * is what keeps the surfaces from drifting: the length FIELDS are slots because the cards commit
 * on blur while the dialog holds local state until submit, but every control and every visibility
 * condition is decided here, once — a blend length and the "At a liner" chips under a blended
 * face, a seal-area length under a face whose seal is on. The "At a liner" row shows whether or
 * not a liner butts the face today: the dialog cannot know the neighbour, and one rule on both
 * surfaces beats two.
 *
 * The seal-area checkboxes sit in their own section, a sibling of "Component blend" — never
 * nested under a blend control (a nested layout hid the seal behind a control nobody would think
 * to tick first — on-device report) and never folded into the Square | Blend chips as a third
 * mode (a seal area is a section property, not part of the blend — on-device report). The profile
 * row appears only once some face is blended BY LENGTH; with both square there is nothing for a
 * profile to describe, and an Angle-mode face is a straight cone (its stored profile is kept).
 *
 * A blended face is entered one of two ways — "Blend by: Length | Angle" ([BlendEntry]). Length
 * shows the length field; Angle shows the angle field, the derived length read-only under it
 * ([aftAngleNote]/[fwdAngleNote], computed by the host because only it knows the shaft), and the
 * optional "Corner radius" field. The "At a liner" chips show in both: they decide the step an
 * angle climbs as surely as where a length's curve leaves from.
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
    aftLinerTarget: BlendLinerTarget,
    fwdLinerTarget: BlendLinerTarget,
    onSetAftLinerTarget: (BlendLinerTarget) -> Unit,
    onSetFwdLinerTarget: (BlendLinerTarget) -> Unit,
    aftLengthField: @Composable () -> Unit,
    fwdLengthField: @Composable () -> Unit,
    aftSealLengthField: @Composable () -> Unit,
    fwdSealLengthField: @Composable () -> Unit,
    aftEntry: BlendEntry,
    fwdEntry: BlendEntry,
    onSetAftEntry: (BlendEntry) -> Unit,
    onSetFwdEntry: (BlendEntry) -> Unit,
    aftAngleField: @Composable () -> Unit,
    fwdAngleField: @Composable () -> Unit,
    aftRadiusField: @Composable () -> Unit,
    fwdRadiusField: @Composable () -> Unit,
    aftAngleNote: String,
    fwdAngleNote: String,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Component blend",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        BlendFaceRow("AFT", aftMode, "body_blend_aft", onSetAftMode)
        if (aftMode == BlendFaceMode.BLEND) {
            BlendFaceEntry(
                entry = aftEntry, tagPrefix = "body_blend_aft", onSetEntry = onSetAftEntry,
                lengthField = aftLengthField, angleField = aftAngleField,
                angleNote = aftAngleNote, radiusField = aftRadiusField,
            )
            BlendLinerTargetRow(aftLinerTarget, "body_blend_aft_liner_target", onSetAftLinerTarget)
        }
        BlendFaceRow("FWD", fwdMode, "body_blend_fwd", onSetFwdMode)
        if (fwdMode == BlendFaceMode.BLEND) {
            BlendFaceEntry(
                entry = fwdEntry, tagPrefix = "body_blend_fwd", onSetEntry = onSetFwdEntry,
                lengthField = fwdLengthField, angleField = fwdAngleField,
                angleNote = fwdAngleNote, radiusField = fwdRadiusField,
            )
            BlendLinerTargetRow(fwdLinerTarget, "body_blend_fwd_liner_target", onSetFwdLinerTarget)
        }

        if ((aftMode == BlendFaceMode.BLEND && aftEntry == BlendEntry.LENGTH) ||
            (fwdMode == BlendFaceMode.BLEND && fwdEntry == BlendEntry.LENGTH)
        ) {
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
        if (aftSeal && fwdSeal) CardFieldGap()
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
    // In an Add dialog a tick is an edit for its discard guard; on a card there is none.
    val edits = LocalDialogEdits.current
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { on ->
                    edits?.mark()
                    onCheckedChange(on)
                },
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

/**
 * One blended face's entry: the "Blend by: Length | Angle" chips, then either the length field or
 * the angle field, its derived-length line and the corner-radius field.
 */
@Composable
private fun BlendFaceEntry(
    entry: BlendEntry,
    tagPrefix: String,
    onSetEntry: (BlendEntry) -> Unit,
    lengthField: @Composable () -> Unit,
    angleField: @Composable () -> Unit,
    angleNote: String,
    radiusField: @Composable () -> Unit,
) {
    ChipRow(
        label = "Blend by",
        options = BlendEntry.values().toList(),
        selected = entry,
        labelOf = { it.chipLabel() },
        tagOf = { "${tagPrefix}_entry_${it.name}" },
        onSelect = onSetEntry,
    )
    // On a card the fields carry no empty supporting line, so the card's row gap goes under each
    // one ([CardFieldGap]); the angle note sits under its field where that line would, the
    // supporting line's own 4 dp below the box on a card.
    when (entry) {
        BlendEntry.LENGTH -> {
            lengthField()
            CardFieldGap()
        }
        BlendEntry.ANGLE -> {
            angleField()
            Text(
                angleNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp, top = LocalCardRowGap.current / 2, bottom = 4.dp)
                    .testTag("${tagPrefix}_angle_note"),
            )
            radiusField()
            CardFieldGap()
        }
    }
}

/** One blended face's "At a liner" chips: Seat (the default) | Liner OD. */
@Composable
private fun BlendLinerTargetRow(
    target: BlendLinerTarget,
    tagPrefix: String,
    onSelect: (BlendLinerTarget) -> Unit,
) {
    ChipRow(
        label = "At a liner",
        options = BlendLinerTarget.values().toList(),
        selected = target,
        labelOf = { it.chipLabel() },
        tagOf = { "${tagPrefix}_${it.name}" },
        onSelect = onSelect,
    )
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
    // In an Add dialog a move to another option is an edit for its discard guard.
    val edits = LocalDialogEdits.current

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
                    onClick = {
                        if (!on) edits?.mark()
                        onSelect(opt)
                    },
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
