package com.android.shaftschematic.ui.screen

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.autoBlendFor
import com.android.shaftschematic.model.hasKeyway
import com.android.shaftschematic.model.suggestedBodyKeywayEnd
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.util.bodyWarningMessages
import com.android.shaftschematic.ui.util.positiveLengthErrorMm
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.toMmOrNull
import com.android.shaftschematic.util.UnitSystem

// ─────────────────────────────────────────────────────────────────────────────
// BodyPagerCard — the `ResolvedBody` arm of [ComponentPagerCard]
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Carousel editor card for a body — both shapes it takes.
 *
 * An AUTO span (`ResolvedComponentSource.AUTO`) shows derived Start/Length read-only with an
 * editable Ø (a per-section bare-shaft override) and the "Explicit body" checkbox that promotes
 * it; an explicit body shows the editable Start/Length/Ø, the blend/seal faces, and the keyway
 * section. Every control here that changes geometry, position, or a value is mirrored in
 * `AddBodyDialog` by the add-dialog-parity invariant; "Show Ø on drawing", "Show name on
 * drawing", "Compress on drawing", "Shade on drawing", and the "Prints in: in | mm" chip are
 * the documented card-only carve-outs — each changes only how an already-drawn body prints and
 * is reached for after looking at a printed sheet.
 *
 * [f1] and [startValidator] are supplied by [ComponentPagerCard] because the thread card shares
 * them; [startValidator] closes over the spec and document unit that validate an overlap.
 */
@Composable
internal fun BodyPagerCard(
    component: ResolvedBody,
    explicitIndex: Int?,
    spec: ShaftSpec,
    unit: UnitSystem,
    physicalIndex: Int,
    outerPaddingHorizontal: Dp,
    showComponentDebugLabels: Boolean,
    componentTitlesDefault: Boolean = true,
    componentShadeDefaults: ComponentShadeDefaults = ComponentShadeDefaults(),
    bodyTitleById: Map<String, String>,
    f1: (Float) -> String,
    startValidator: (String, ComponentKind, Float) -> (String) -> String?,
    onAddBody: (Float, Float, Float) -> Unit,
    onSetAutoSectionDia: (spanStartMm: Float, spanEndMm: Float, diaMm: Float) -> Unit,
    onSetAutoBlend: (spanStartMm: Float, spanEndMm: Float, end: LinerAuthoredReference, lengthMm: Float, profile: BlendProfile, seal: Boolean) -> Unit,
    onSetShowAutoBodyDia: (Boolean) -> Unit,
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateBodyShowDia: (Int, Boolean) -> Unit,
    onUpdateBodyShowLabel: (Int, Boolean) -> Unit,
    onUpdateBodyShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateBodyCompressOnDrawing: (Int, Boolean) -> Unit,
    onUpdateBodyBlend: (index: Int, blendAftMm: Float, blendFwdMm: Float, profile: BlendProfile, sealAft: Boolean, sealFwd: Boolean) -> Unit,
    onUpdateBodyLabel: (Int, String?) -> Unit,
    onUpdateBodyKeyway: (index: Int, widthMm: Float, depthMm: Float, lengthMm: Float, offsetFromEndMm: Float, end: LinerAuthoredReference, spooned: Boolean) -> Unit,
    onSetKeyways180Apart: (Boolean) -> Unit,
    onSetKeyways90Apart: (Boolean) -> Unit,
    onSetKeyways90Cw: (Boolean) -> Unit,
    onRemoveBody: (String) -> Unit,
    collidingComponentIds: Set<String>,
    perComponentUnitsEnabled: Boolean,
    unitOverrides: Map<String, UnitSystem>,
    onSetComponentUnit: (String, UnitSystem?) -> Unit,
    onSetKeywayUnit: (String, UnitSystem?) -> Unit,
) {
    if (component.source == ResolvedComponentSource.AUTO) {
        // Auto-body Start/Length are derived from the resolve layer and shown
        // read-only (greyed); making the body explicit via the checkbox is the only
        // way to control its position. The Ø field IS editable: it sets THIS
        // section's bare-shaft Ø (an AutoDiaOverride anchored in this span) without
        // promoting or touching positioning — neighbouring auto sections keep theirs.
        val startMm  = component.startMmPhysical
        val lengthMm = component.endMmPhysical - component.startMmPhysical
        val diaMm    = component.diaMm
        var promoted by remember(component.id) { mutableStateOf(false) }

        // Explicit promotion via checkbox: turns this derived fill into a real,
        // editable Body (needed to add a keyway to a line-shaft end span, or to
        // lock the span in). The resulting Body carries the auto-body's current
        // derived Start/Length/Ø. This is the sole promotion path (field edits are
        // disabled), guarded by `promoted` so it fires once.
        fun promoteNow() {
            if (!promoted && startMm >= 0f && lengthMm > 0f && diaMm > 0f) {
                promoted = true; onAddBody(startMm, lengthMm, diaMm)
            }
        }

        ComponentCard(
            title = "Body (auto)",
            debugText = if (showComponentDebugLabels) "id=${component.id} • startMm=${f1(component.startMmPhysical)} • endMm=${f1(component.endMmPhysical)}" else null,
            outerPaddingHorizontal = outerPaddingHorizontal,
        ) {
            // Checkbox sits ABOVE the fields, matching its position on the
            // explicit-body card, so it doesn't jump when checked.
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(
                        value = promoted,
                        enabled = !promoted,
                        role = androidx.compose.ui.semantics.Role.Checkbox,
                        onValueChange = { checked -> if (checked) promoteNow() }
                    ).padding(vertical = 4.dp)
                    .testTag("body_explicit_checkbox"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Explicit body",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.material3.Checkbox(
                    checked = promoted,
                    enabled = !promoted,
                    onCheckedChange = null
                )
            }
            CommitNum("Start (${abbr(unit)})", disp(startMm, unit), enabled = false) { }
            CommitNum("Length (${abbr(unit)})", disp(lengthMm, unit), enabled = false) { }
            CommitNum("Ø (${abbr(unit)})", disp(diaMm, unit)) { s ->
                toMmOrNull(s, unit)?.let {
                    onSetAutoSectionDia(component.startMmPhysical, component.endMmPhysical, it)
                }
            }
            // One flag for every auto span — the bare shaft is one piece of stock, so
            // it carries one visibility even where sections differ in Ø.
            ShowDiaToggleRow(
                label = "Show bare-shaft Ø on drawing",
                checked = spec.showAutoBodyDia,
                testTag = "autobody_show_dia_toggle",
                onCheckedChange = onSetShowAutoBodyDia,
            )

            // Blend — available here as well as on explicit bodies. An auto span
            // re-derives its extent from whatever surrounds it, so a blend anchored to
            // the span survives edits that would strand one authored against a
            // promoted body's fixed boundary (a template whose liners move).
            val aftBlend = spec.autoBlends.autoBlendFor(
                component.startMmPhysical, component.endMmPhysical, LinerAuthoredReference.AFT)
            val fwdBlend = spec.autoBlends.autoBlendFor(
                component.startMmPhysical, component.endMmPhysical, LinerAuthoredReference.FWD)
            val autoProfile = aftBlend?.profile ?: fwdBlend?.profile ?: BlendProfile.OGEE
            BlendSection(
                aftMode = blendFaceMode(aftBlend?.lengthMm ?: 0f, aftBlend?.seal == true),
                fwdMode = blendFaceMode(fwdBlend?.lengthMm ?: 0f, fwdBlend?.seal == true),
                profile = autoProfile,
                onSetAftMode = { m ->
                    onSetAutoBlend(
                        component.startMmPhysical, component.endMmPhysical,
                        LinerAuthoredReference.AFT,
                        blendLenForMode(m, aftBlend?.lengthMm ?: 0f, lengthMm),
                        autoProfile, m == BlendFaceMode.SEAL,
                    )
                },
                onSetFwdMode = { m ->
                    onSetAutoBlend(
                        component.startMmPhysical, component.endMmPhysical,
                        LinerAuthoredReference.FWD,
                        blendLenForMode(m, fwdBlend?.lengthMm ?: 0f, lengthMm),
                        autoProfile, m == BlendFaceMode.SEAL,
                    )
                },
                onProfile = { p ->
                    aftBlend?.let {
                        onSetAutoBlend(component.startMmPhysical, component.endMmPhysical,
                            LinerAuthoredReference.AFT, it.lengthMm, p, it.seal)
                    }
                    fwdBlend?.let {
                        onSetAutoBlend(component.startMmPhysical, component.endMmPhysical,
                            LinerAuthoredReference.FWD, it.lengthMm, p, it.seal)
                    }
                },
                aftLengthField = {
                    CommitNum("Blend AFT (${abbr(unit)})", disp(aftBlend?.lengthMm ?: 0f, unit)) { str ->
                        toMmOrNull(str, unit)?.let {
                            onSetAutoBlend(component.startMmPhysical, component.endMmPhysical,
                                LinerAuthoredReference.AFT, it, autoProfile, aftBlend?.seal ?: false)
                        }
                    }
                },
                fwdLengthField = {
                    CommitNum("Blend FWD (${abbr(unit)})", disp(fwdBlend?.lengthMm ?: 0f, unit)) { str ->
                        toMmOrNull(str, unit)?.let {
                            onSetAutoBlend(component.startMmPhysical, component.endMmPhysical,
                                LinerAuthoredReference.FWD, it, autoProfile, fwdBlend?.seal ?: false)
                        }
                    }
                },
            )
        }
        return
    }

    val idx = explicitIndex ?: return
    val b   = spec.bodies.getOrNull(idx) ?: return
    val computedBodyTitle = bodyTitleById[b.id] ?: "Body"
    var showDemoteDialog by remember(b.id) { mutableStateOf(false) }
    ComponentCard(
        title = computedBodyTitle,
        titleContent = {
            EditableCardTitle(
                componentId = b.id,
                title = computedBodyTitle,
                label = b.label,
                onCommitLabel = { onUpdateBodyLabel(idx, it) },
            )
        },
        debugText = if (showComponentDebugLabels) "id=${b.id} • startMm=${f1(b.startFromAftMm)} • endMm=${f1(b.startFromAftMm + b.lengthMm)}" else null,
        errorMessage = if (b.id in collidingComponentIds) "Overlaps another component" else null,
        warningMessage = bodyWarningMessages(spec, b).joinToString("; ").ifEmpty { null },
        componentId = b.id, componentKind = ComponentKind.BODY,
        outerPaddingHorizontal = outerPaddingHorizontal,
        onRemove = {
            Log.d("ShaftUI", "Body delete clicked: id=${b.id}, rowIndex=$idx, physicalIndex=$physicalIndex")
            onRemoveBody(b.id)
        }
    ) {
        // Explicit-body toggle (checked). Unchecking demotes this body back to an
        // auto-fill span, but only after confirmation — the same trash/delete
        // pipeline (onRemoveBody) does the removal, and the resolve layer regenerates
        // the auto span. Guarded by a dialog so an accidental tap can't wipe stored size.
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(
                    value = true,
                    role = androidx.compose.ui.semantics.Role.Checkbox,
                    onValueChange = { checked -> if (!checked) showDemoteDialog = true }
                ).padding(vertical = 4.dp)
                .testTag("body_explicit_checkbox"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Explicit body", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            androidx.compose.material3.Checkbox(checked = true, onCheckedChange = null)
        }
        if (showDemoteDialog) {
            AlertDialog(
                onDismissRequest = { showDemoteDialog = false },
                title = { Text("Make body automatic?") },
                text = {
                    Text(
                        buildString {
                            append("This body's stored size will be replaced by the auto-fill span that regenerates from the surrounding components.")
                            if (b.hasKeyway) append(" Its keyway will be removed too.")
                        }
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showDemoteDialog = false; onRemoveBody(b.id) },
                        modifier = Modifier.testTag("body_demote_confirm")
                    ) { Text("Make automatic") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showDemoteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        CommitNum("Start (${abbr(unit)})", disp(b.startFromAftMm, unit), validator = startValidator(b.id, ComponentKind.BODY, b.lengthMm)) { s ->
            toMmOrNull(s, unit)?.let { onUpdateBody(idx, it, b.lengthMm, b.diaMm) }
        }
        CommitNum(
            "Length (${abbr(unit)})", disp(b.lengthMm, unit),
            validator = { raw -> positiveLengthErrorMm(toMmOrNull(raw, unit)) },
        ) { s ->
            toMmOrNull(s, unit)?.let { onUpdateBody(idx, b.startFromAftMm, it, b.diaMm) }
        }
        CommitNum("Ø (${abbr(unit)})", disp(b.diaMm, unit)) { s ->
            toMmOrNull(s, unit)?.let { onUpdateBody(idx, b.startFromAftMm, b.lengthMm, it) }
        }
        ShowDiaToggleRow(
            label = "Show Ø on drawing",
            checked = b.showDiaOnDrawing,
            testTag = "body_show_dia_toggle",
            onCheckedChange = { onUpdateBodyShowDia(idx, it) },
        )
        ShowDiaToggleRow(
            label = "Show name on drawing",
            checked = b.showNameOnDrawing ?: componentTitlesDefault,
            testTag = "body_show_label_toggle",
            onCheckedChange = { onUpdateBodyShowLabel(idx, it) },
        )
        // Off by default on a newly authored body: a named section reads at TRUE
        // proportion. Ticking it lets this body foreshorten and carry the S-break again —
        // the escape hatch for a body long enough that pinning it starves the drawn height
        // of the rest of the shaft.
        ShowDiaToggleRow(
            label = "Compress on drawing",
            checked = b.compressOnDrawing,
            testTag = "body_compress_toggle",
            onCheckedChange = { onUpdateBodyCompressOnDrawing(idx, it) },
        )
        // Unset follows the kind's Settings checkbox; ticking it shades THIS body with that
        // checkbox off (the on-device case: one named section grey, the rest of the drawing
        // clean), and unticking bares it with the checkbox on.
        ShowDiaToggleRow(
            label = "Shade on drawing",
            checked = b.shadeOnDrawing ?: componentShadeDefaults.bodies,
            testTag = "body_shade_toggle",
            onCheckedChange = { onUpdateBodyShade(idx, it) },
        )

        // Blend — a machined smooth transition into whatever the face steps to.
        // Silhouette only: the rails keep dimensioning the stored span, so nothing
        // here moves a value or a neighbour. Mirrored in AddBodyDialog by contract.
        val aftMode = blendFaceMode(b.blendAftMm, b.blendAftSeal)
        val fwdMode = blendFaceMode(b.blendFwdMm, b.blendFwdSeal)
        BlendSection(
            aftMode = aftMode,
            fwdMode = fwdMode,
            profile = b.blendProfile,
            onSetAftMode = { m ->
                onUpdateBodyBlend(
                    idx, blendLenForMode(m, b.blendAftMm, b.lengthMm), b.blendFwdMm,
                    b.blendProfile, m == BlendFaceMode.SEAL, b.blendFwdSeal,
                )
            },
            onSetFwdMode = { m ->
                onUpdateBodyBlend(
                    idx, b.blendAftMm, blendLenForMode(m, b.blendFwdMm, b.lengthMm),
                    b.blendProfile, b.blendAftSeal, m == BlendFaceMode.SEAL,
                )
            },
            onProfile = { p ->
                onUpdateBodyBlend(idx, b.blendAftMm, b.blendFwdMm, p, b.blendAftSeal, b.blendFwdSeal)
            },
            aftLengthField = {
                CommitNum("Blend AFT (${abbr(unit)})", disp(b.blendAftMm, unit)) { str ->
                    toMmOrNull(str, unit)?.let {
                        onUpdateBodyBlend(idx, it, b.blendFwdMm, b.blendProfile, b.blendAftSeal, b.blendFwdSeal)
                    }
                }
            },
            fwdLengthField = {
                CommitNum("Blend FWD (${abbr(unit)})", disp(b.blendFwdMm, unit)) { str ->
                    toMmOrNull(str, unit)?.let {
                        onUpdateBodyBlend(idx, b.blendAftMm, it, b.blendProfile, b.blendAftSeal, b.blendFwdSeal)
                    }
                }
            },
        )

        // Keyway — gated behind a checkbox so the fields only appear once turned on
        // (intermediate shafts with fitted couplings carry a keyway in a plain end
        // body). Mirrors the taper keyway section, with an AFT/FWD end reference.
        var kwEnabled by remember(b.id) { mutableStateOf(b.hasKeyway) }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(
                    value = kwEnabled,
                    role = androidx.compose.ui.semantics.Role.Checkbox,
                    onValueChange = { checked ->
                        kwEnabled = checked
                        // Unchecking removes the keyway; checking just reveals the fields.
                        if (!checked && b.hasKeyway) {
                            onUpdateBodyKeyway(idx, 0f, 0f, 0f, 0f, b.keywayEnd, false)
                        }
                    }
                ).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Keyway", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            androidx.compose.material3.Checkbox(checked = kwEnabled, onCheckedChange = null)
        }

        if (kwEnabled) {
            val kwSelectedColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color.Black,
                selectedLabelColor = Color.White,
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurface
            )
            // Until a keyway value is typed, the AFT/FWD chips ride a local draft seeded by
            // `suggestedBodyKeywayEnd` — opposite the shaft's existing keyway when one side
            // is taken (a new body keyway defaulting onto the side an aft taper keyway
            // already holds reads as a second aft keyway — on-device report). The model is
            // untouched until a real value commits; from then on `b.keywayEnd` is the truth
            // (the remember key flips with `b.hasKeyway`, re-deriving the draft from it).
            var kwEndDraft by remember(b.id, b.hasKeyway) {
                mutableStateOf(if (b.hasKeyway) b.keywayEnd else spec.suggestedBodyKeywayEnd(excludeBodyId = b.id))
            }
            val kwEnd = if (b.hasKeyway) b.keywayEnd else kwEndDraft
            val isKwFwd = kwEnd == LinerAuthoredReference.FWD
            fun setKwEnd(end: LinerAuthoredReference) {
                kwEndDraft = end
                if (b.hasKeyway) {
                    onUpdateBodyKeyway(idx, b.keywayWidthMm, b.keywayDepthMm, b.keywayLengthMm, b.keywayOffsetFromEndMm, end, b.keywaySpooned)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("KW from:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(selected = !isKwFwd,
                    onClick = { setKwEnd(LinerAuthoredReference.AFT) },
                    label = { Text("AFT") }, colors = kwSelectedColors,
                    border = if (!isKwFwd) BorderStroke(1.dp, Color.Black) else null)
                FilterChip(selected = isKwFwd,
                    onClick = { setKwEnd(LinerAuthoredReference.FWD) },
                    label = { Text("FWD") }, colors = kwSelectedColors,
                    border = if (isKwFwd) BorderStroke(1.dp, Color.Black) else null)
            }
            // The keyway's own unit: European stock is metric on an otherwise imperial
            // shaft, so these four fields are entered AND printed in `kwUnit`, which falls
            // back to the body's unit and then the document's when there is no override.
            val kwUnit = DisplayUnits(unit, unitOverrides).keywayUnitFor(b.id)
            if (perComponentUnitsEnabled) {
                KeywayUnitChip(b.id, unit, unitOverrides, onSetKeywayUnit)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CommitNum("KW W (${abbr(kwUnit)})", dispKw(b.keywayWidthMm, kwUnit), modifier = Modifier.weight(1f), fillMaxWidth = false) { s ->
                    val v = if (s.isBlank()) 0f else (toMmOrNull(s, kwUnit) ?: return@CommitNum)
                    onUpdateBodyKeyway(idx, v, b.keywayDepthMm, b.keywayLengthMm, b.keywayOffsetFromEndMm, kwEnd, b.keywaySpooned)
                }
                Text("×", style = MaterialTheme.typography.titleMedium)
                CommitNum("KW D (${abbr(kwUnit)})", dispKw(b.keywayDepthMm, kwUnit), modifier = Modifier.weight(1f), fillMaxWidth = false) { s ->
                    val v = if (s.isBlank()) 0f else (toMmOrNull(s, kwUnit) ?: return@CommitNum)
                    onUpdateBodyKeyway(idx, b.keywayWidthMm, v, b.keywayLengthMm, b.keywayOffsetFromEndMm, kwEnd, b.keywaySpooned)
                }
            }
            // KW L / Offset parse in `kwUnit` like KW W/D — the keyway-unit chip governs what
            // EVERY keyway number means; parsing these two in the document unit under a kwUnit
            // label read a metric keyway's length as inches.
            CommitNum("KW L (${abbr(kwUnit)})", dispKw(b.keywayLengthMm, kwUnit)) { s ->
                val v = if (s.isBlank()) 0f else (toMmOrNull(s, kwUnit) ?: return@CommitNum)
                onUpdateBodyKeyway(idx, b.keywayWidthMm, b.keywayDepthMm, v, b.keywayOffsetFromEndMm, kwEnd, b.keywaySpooned)
            }
            CommitNum("KW Offset from ${if (isKwFwd) "FWD" else "AFT"} (${abbr(kwUnit)})", dispKw(b.keywayOffsetFromEndMm, kwUnit)) { s ->
                val v = if (s.isBlank()) 0f else (toMmOrNull(s, kwUnit) ?: return@CommitNum)
                onUpdateBodyKeyway(idx, b.keywayWidthMm, b.keywayDepthMm, b.keywayLengthMm, v, kwEnd, b.keywaySpooned)
            }

            val isKwFloating = b.keywayOffsetFromEndMm > 0f
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(
                        value = b.keywaySpooned, enabled = !isKwFloating,
                        role = androidx.compose.ui.semantics.Role.Switch,
                        onValueChange = { checked ->
                            onUpdateBodyKeyway(idx, b.keywayWidthMm, b.keywayDepthMm, b.keywayLengthMm, b.keywayOffsetFromEndMm, kwEnd, checked)
                        }
                    ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isKwFloating) "Keyway spooned (N/A — floating)" else "Keyway spooned",
                    modifier = Modifier.weight(1f),
                    color = if (isKwFloating) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.material3.Switch(
                    checked = b.keywaySpooned && !isKwFloating,
                    enabled = !isKwFloating,
                    onCheckedChange = null
                )
            }
        }

        KeywayClockingSection(spec, onSetKeyways180Apart, onSetKeyways90Apart, onSetKeyways90Cw)

        if (perComponentUnitsEnabled) {
            ComponentUnitChip(b.id, unit, unitOverrides, onSetComponentUnit)
        }
    }
}
