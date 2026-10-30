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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.autoBlendFor
import com.android.shaftschematic.model.hasAnyKeywayValue
import com.android.shaftschematic.model.hasKeyway
import com.android.shaftschematic.model.suggestedBodyKeywayEnd
import com.android.shaftschematic.ui.input.bodyPhysStartForNewLength
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.bodyAtShaftEnd
import com.android.shaftschematic.ui.resolved.effectiveBodyDimensionRail
import com.android.shaftschematic.ui.util.bodyWarningMessages
import com.android.shaftschematic.ui.util.positiveDiaErrorMm
import com.android.shaftschematic.ui.util.positiveLengthErrorMm
import com.android.shaftschematic.ui.util.startOverlapErrorMm
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
 * editable Ø — committed to "This section" (a per-section override) or "All auto sections" (the
 * shaft-wide bare-shaft Ø) through a scope dialog whenever [autoSectionCount] > 1 — and the
 * "Explicit body" checkbox that promotes it; an explicit body shows the "Measure From: AFT | FWD"
 * chips (the liner card's row — the Start field shows and accepts the distance from the chosen
 * end, a switch never moves the body, and a FWD body keeps its FWD distance through an OAL change),
 * the editable Start/Length/Ø, the component blends and seal areas, and the keyway
 * section. That section is gated by the shared "Keyway" checkbox ([KeywayGateRow]), seeded open
 * whenever any of W/D/L is typed; unticking it with values present confirms through
 * [RemoveKeywayConfirmDialog] before clearing them. Inside the gate: KW from AFT | FWD, the keyway
 * unit chip, W × D, KW L, the "Captured keyway" toggle with its "KW Inset from AFT/FWD" field
 * shown only while captured, "Keyway spooned", and the clocking section.
 * Every control here that changes geometry, position, or a value is mirrored in
 * `AddBodyDialog` by the add-dialog-parity invariant; "Show Ø on drawing", "Show name on
 * drawing", "Show dimension rail", "Compress on drawing", "Shade on drawing", and the
 * "Prints in: in | mm" chip are the documented card-only carve-outs — each changes only how an already-drawn body prints and
 * is reached for after looking at a printed sheet.
 *
 * [f1] is supplied by [ComponentPagerCard] because the thread card shares it. The Start field
 * validates the PHYSICAL start it converts to (`startOverlapErrorMm`, the liner card's posture) —
 * for a body that is the `≥ 0` gate only, since bodies are collision-exempt.
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
    onAddBody: (Float, Float, Float) -> Unit,
    onSetAutoSectionDia: (spanStartMm: Float, spanEndMm: Float, diaMm: Float) -> Unit,
    onSetAllAutoSectionsDia: (diaMm: Float) -> Unit,
    /** How many AUTO body sections the shaft resolves to — decides whether the Ø asks for a scope. */
    autoSectionCount: Int,
    onSetAutoBlend: (spanStartMm: Float, spanEndMm: Float, end: LinerAuthoredReference, lengthMm: Float, profile: BlendProfile, seal: Boolean, sealLenMm: Float, toLinerOd: Boolean) -> Unit,
    onSetShowAutoBodyDia: (Boolean) -> Unit,
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    /** Sets an explicit body's "Measure From" reference — never moves its stored start. */
    onUpdateBodyReference: (Int, LinerAuthoredReference) -> Unit,
    onUpdateBodyShowDia: (Int, Boolean) -> Unit,
    onUpdateBodyShowLabel: (Int, Boolean) -> Unit,
    onUpdateBodyShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateBodyCompressOnDrawing: (Int, Boolean) -> Unit,
    onUpdateBodyShowDimensionRail: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateBodyBlend: (index: Int, blendAftMm: Float, blendFwdMm: Float, profile: BlendProfile, sealAft: Boolean, sealFwd: Boolean, sealAftLenMm: Float, sealFwdLenMm: Float, toLinerOdAft: Boolean, toLinerOdFwd: Boolean) -> Unit,
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
        // way to control its position. The Ø field IS editable, and the commit chooses
        // its scope: with more than one auto section a dialog asks "This section" (an
        // AutoDiaOverride anchored in this span — neighbouring sections keep theirs) or
        // "All auto sections" (the shaft-wide bare-shaft Ø, clearing every section's
        // own); with one section the value is the shaft's Ø and applies directly. No
        // choice promotes or touches positioning.
        val startMm  = component.startMmPhysical
        val lengthMm = component.endMmPhysical - component.startMmPhysical
        val diaMm    = component.diaMm
        var promoted by remember(component.id) { mutableStateOf(false) }
        // The typed Ø awaiting a scope choice; null while no dialog is up.
        var pendingDiaMm by remember(component.id) { mutableStateOf<Float?>(null) }
        // Bumped when the scope dialog is dismissed: re-keys the Ø field so it shows the
        // STORED value again — a value that was not applied never stays in the box.
        var diaFieldResetNonce by remember(component.id) { mutableStateOf(0) }
        // The Ø field still holds focus after an IME-Done commit, and a later blur would
        // commit the same text again — reopening the dialog after a choice, or, on Cancel,
        // re-committing from the field the re-key disposes. Clearing focus as the dialog
        // opens lets that blur land now, while it can only repeat the pending value.
        val focusManager = LocalFocusManager.current
        val scopeDialogOpen = pendingDiaMm != null
        LaunchedEffect(scopeDialogOpen) {
            if (scopeDialogOpen) focusManager.clearFocus()
        }

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
            FieldPair(
                first = { CommitNum("Start (${abbr(unit)})", disp(startMm, unit), enabled = false) { } },
                second = { CommitNum("Length (${abbr(unit)})", disp(lengthMm, unit), enabled = false) { } },
            )
            key(diaFieldResetNonce) {
                CommitNum("Diameter (${abbr(unit)})", disp(diaMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        if (autoSectionCount <= 1) onSetAllAutoSectionsDia(it) else pendingDiaMm = it
                    }
                }
            }
            pendingDiaMm?.let { v ->
                val dismiss: () -> Unit = {
                    pendingDiaMm = null
                    diaFieldResetNonce += 1
                }
                AlertDialog(
                    onDismissRequest = dismiss,
                    modifier = Modifier.testTag("autobody_dia_scope_dialog"),
                    title = { Text("Apply diameter to…") },
                    text = {
                        Text(
                            "This shaft has $autoSectionCount auto-body sections. Change only " +
                                "this section, or every auto-body section together?"
                        )
                    },
                    confirmButton = {
                        Row {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    pendingDiaMm = null
                                    onSetAutoSectionDia(component.startMmPhysical, component.endMmPhysical, v)
                                },
                                modifier = Modifier.testTag("autobody_dia_this_section"),
                            ) { Text("This section") }
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    pendingDiaMm = null
                                    onSetAllAutoSectionsDia(v)
                                },
                                modifier = Modifier.testTag("autobody_dia_all_sections"),
                            ) { Text("All auto sections") }
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = dismiss) { Text("Cancel") }
                    },
                )
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
            // An anchor may carry a seal with no blend (lengthMm 0): the finish and the seal
            // area are independent, so a finish change passes the seal pair through untouched
            // and a seal toggle passes the blend length through untouched.
            BlendSection(
                aftMode = blendFaceMode(aftBlend?.lengthMm ?: 0f),
                fwdMode = blendFaceMode(fwdBlend?.lengthMm ?: 0f),
                profile = autoProfile,
                aftSeal = aftBlend?.seal == true,
                fwdSeal = fwdBlend?.seal == true,
                aftLinerTarget = blendLinerTarget(aftBlend?.toLinerOd == true),
                fwdLinerTarget = blendLinerTarget(fwdBlend?.toLinerOd == true),
                // Shown only while the face is Blend, so its anchor exists; every other field of
                // the anchor passes through verbatim.
                onSetAftLinerTarget = { t ->
                    aftBlend?.let {
                        onSetAutoBlend(component.startMmPhysical, component.endMmPhysical,
                            LinerAuthoredReference.AFT, it.lengthMm, it.profile, it.seal, it.sealLenMm,
                            t.toLinerOd)
                    }
                },
                onSetFwdLinerTarget = { t ->
                    fwdBlend?.let {
                        onSetAutoBlend(component.startMmPhysical, component.endMmPhysical,
                            LinerAuthoredReference.FWD, it.lengthMm, it.profile, it.seal, it.sealLenMm,
                            t.toLinerOd)
                    }
                },
                onSetAftMode = { m ->
                    onSetAutoBlend(
                        component.startMmPhysical, component.endMmPhysical,
                        LinerAuthoredReference.AFT,
                        blendLenForMode(m, aftBlend?.lengthMm ?: 0f, lengthMm),
                        autoProfile, aftBlend?.seal ?: false, aftBlend?.sealLenMm ?: 0f,
                        aftBlend?.toLinerOd ?: false,
                    )
                },
                onSetFwdMode = { m ->
                    onSetAutoBlend(
                        component.startMmPhysical, component.endMmPhysical,
                        LinerAuthoredReference.FWD,
                        blendLenForMode(m, fwdBlend?.lengthMm ?: 0f, lengthMm),
                        autoProfile, fwdBlend?.seal ?: false, fwdBlend?.sealLenMm ?: 0f,
                        fwdBlend?.toLinerOd ?: false,
                    )
                },
                // Unticking a seal on a square face clears the anchor (length 0, no seal).
                onSetAftSeal = { on ->
                    onSetAutoBlend(
                        component.startMmPhysical, component.endMmPhysical,
                        LinerAuthoredReference.AFT, aftBlend?.lengthMm ?: 0f, autoProfile, on,
                        sealLenForSeal(on, aftBlend?.sealLenMm ?: 0f, lengthMm),
                        aftBlend?.toLinerOd ?: false,
                    )
                },
                onSetFwdSeal = { on ->
                    onSetAutoBlend(
                        component.startMmPhysical, component.endMmPhysical,
                        LinerAuthoredReference.FWD, fwdBlend?.lengthMm ?: 0f, autoProfile, on,
                        sealLenForSeal(on, fwdBlend?.sealLenMm ?: 0f, lengthMm),
                        fwdBlend?.toLinerOd ?: false,
                    )
                },
                onProfile = { p ->
                    aftBlend?.let {
                        onSetAutoBlend(component.startMmPhysical, component.endMmPhysical,
                            LinerAuthoredReference.AFT, it.lengthMm, p, it.seal, it.sealLenMm, it.toLinerOd)
                    }
                    fwdBlend?.let {
                        onSetAutoBlend(component.startMmPhysical, component.endMmPhysical,
                            LinerAuthoredReference.FWD, it.lengthMm, p, it.seal, it.sealLenMm, it.toLinerOd)
                    }
                },
                aftLengthField = {
                    CommitNum("Blend AFT (${abbr(unit)})", disp(aftBlend?.lengthMm ?: 0f, unit)) { str ->
                        toMmOrNull(str, unit)?.let {
                            onSetAutoBlend(component.startMmPhysical, component.endMmPhysical,
                                LinerAuthoredReference.AFT, it, autoProfile, aftBlend?.seal ?: false,
                                aftBlend?.sealLenMm ?: 0f, aftBlend?.toLinerOd ?: false)
                        }
                    }
                },
                fwdLengthField = {
                    CommitNum("Blend FWD (${abbr(unit)})", disp(fwdBlend?.lengthMm ?: 0f, unit)) { str ->
                        toMmOrNull(str, unit)?.let {
                            onSetAutoBlend(component.startMmPhysical, component.endMmPhysical,
                                LinerAuthoredReference.FWD, it, autoProfile, fwdBlend?.seal ?: false,
                                fwdBlend?.sealLenMm ?: 0f, fwdBlend?.toLinerOd ?: false)
                        }
                    }
                },
                // Shown only while the face's seal is on, so its anchor exists; the null check
                // keeps a commit racing a clear from resurrecting an anchor.
                aftSealLengthField = {
                    CommitNum("Seal area AFT (${abbr(unit)})", disp(aftBlend?.sealLenMm ?: 0f, unit)) { str ->
                        val blend = aftBlend
                        val mm = toMmOrNull(str, unit)
                        if (blend != null && mm != null) {
                            onSetAutoBlend(component.startMmPhysical, component.endMmPhysical,
                                LinerAuthoredReference.AFT, blend.lengthMm, autoProfile, blend.seal, mm, blend.toLinerOd)
                        }
                    }
                },
                fwdSealLengthField = {
                    CommitNum("Seal area FWD (${abbr(unit)})", disp(fwdBlend?.sealLenMm ?: 0f, unit)) { str ->
                        val blend = fwdBlend
                        val mm = toMmOrNull(str, unit)
                        if (blend != null && mm != null) {
                            onSetAutoBlend(component.startMmPhysical, component.endMmPhysical,
                                LinerAuthoredReference.FWD, blend.lengthMm, autoProfile, blend.seal, mm, blend.toLinerOd)
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
    val isFwdRef = b.authoredReference == LinerAuthoredReference.FWD
    val authoredStartMm = if (isFwdRef) {
        spec.overallLengthMm - b.startFromAftMm - b.lengthMm
    } else {
        b.startFromAftMm
    }
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
        warningMessage = bodyWarningMessages(spec, b, unit).joinToString("; ").ifEmpty { null },
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
        // AFT / FWD reference toggle — the liner card's row. A switch changes what the Start
        // field shows and what an OAL change keeps; the stored start never moves.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Measure From:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val selectedColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color.Black, selectedLabelColor = Color.White,
                containerColor = Color.Transparent, labelColor = MaterialTheme.colorScheme.onSurface
            )
            FilterChip(selected = !isFwdRef, onClick = { onUpdateBodyReference(idx, LinerAuthoredReference.AFT) },
                label = { Text("AFT") }, colors = selectedColors,
                border = if (!isFwdRef) BorderStroke(1.dp, Color.Black) else null,
                modifier = Modifier.testTag("body_ref_aft"))
            FilterChip(selected = isFwdRef, onClick = { onUpdateBodyReference(idx, LinerAuthoredReference.FWD) },
                label = { Text("FWD") }, colors = selectedColors,
                border = if (isFwdRef) BorderStroke(1.dp, Color.Black) else null,
                modifier = Modifier.testTag("body_ref_fwd"))
        }
        FieldPair(
            first = {
                CommitNum(
                    label = "Start from ${if (isFwdRef) "FWD" else "AFT"} (${abbr(unit)})",
                    initialDisplay = disp(authoredStartMm, unit),
                    validator = { raw ->
                        val authoredMm = toMmOrNull(raw, unit) ?: return@CommitNum "Enter a number"
                        val physStart = if (isFwdRef) spec.overallLengthMm - authoredMm - b.lengthMm else authoredMm
                        startOverlapErrorMm(spec, b.id, ComponentKind.BODY, b.lengthMm, physStart)
                    }
                ) { s ->
                    val authoredMm = toMmOrNull(s, unit) ?: return@CommitNum
                    val physStart = if (isFwdRef) spec.overallLengthMm - authoredMm - b.lengthMm else authoredMm
                    onUpdateBody(idx, physStart, b.lengthMm, b.diaMm)
                }
            },
            second = {
                // A FWD-measured body keeps its FWD end and grows AFT-ward; ONE derivation,
                // bodyPhysStartForNewLength, places the new span.
                CommitNum(
                    "Length (${abbr(unit)})", disp(b.lengthMm, unit),
                    validator = { raw -> positiveLengthErrorMm(toMmOrNull(raw, unit)) },
                ) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateBody(idx, bodyPhysStartForNewLength(b, it, spec.overallLengthMm), it, b.diaMm)
                    }
                }
            },
        )
        // An explicit body's Ø must be > 0, as AddBodyDialog requires; a 0 drew a hairline body
        // with no word of it (emulator QA). The AUTO card's Ø stays ungated: ≤ 0 there clears
        // that section's override.
        CommitNum(
            "Diameter (${abbr(unit)})", disp(b.diaMm, unit),
            validator = { raw -> positiveDiaErrorMm(toMmOrNull(raw, unit)) },
        ) { s ->
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
            caption = if (b.showNameOnDrawing == null) COMPONENT_NAMES_DEFAULT_CAPTION else null,
            testTag = "body_show_label_toggle",
            onCheckedChange = { onUpdateBodyShowLabel(idx, it) },
        )
        // The body's own length span. Unset follows the body's position — on at a shaft end
        // (where a coupling goes), off mid-shaft — and the caption says which applies; a tap
        // stores the choice, which then holds wherever the body sits.
        val atShaftEnd = bodyAtShaftEnd(b, spec)
        ShowDiaToggleRow(
            label = "Show dimension rail",
            checked = effectiveBodyDimensionRail(b, spec),
            caption = if (b.showDimensionRail == null) bodyDimensionRailDefaultCaption(atShaftEnd) else null,
            testTag = "body_dimension_rail_toggle",
            onCheckedChange = { onUpdateBodyShowDimensionRail(idx, it) },
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

        // Blend — a machined smooth transition into whatever the face steps to — and the
        // seal areas, a separate per-face section property. Silhouette only: the rails keep
        // dimensioning the stored span, so nothing here moves a value or a neighbour. A
        // finish change leaves the seal flags untouched and a seal toggle leaves the blend
        // lengths untouched. Mirrored in AddBodyDialog by contract.
        BlendSection(
            aftMode = blendFaceMode(b.blendAftMm),
            fwdMode = blendFaceMode(b.blendFwdMm),
            profile = b.blendProfile,
            aftSeal = b.blendAftSeal,
            fwdSeal = b.blendFwdSeal,
            aftLinerTarget = blendLinerTarget(b.blendAftToLinerOd),
            fwdLinerTarget = blendLinerTarget(b.blendFwdToLinerOd),
            onSetAftLinerTarget = { t ->
                onUpdateBodyBlend(
                    idx, b.blendAftMm, b.blendFwdMm, b.blendProfile, b.blendAftSeal, b.blendFwdSeal,
                    b.blendAftSealLenMm, b.blendFwdSealLenMm, t.toLinerOd, b.blendFwdToLinerOd,
                )
            },
            onSetFwdLinerTarget = { t ->
                onUpdateBodyBlend(
                    idx, b.blendAftMm, b.blendFwdMm, b.blendProfile, b.blendAftSeal, b.blendFwdSeal,
                    b.blendAftSealLenMm, b.blendFwdSealLenMm, b.blendAftToLinerOd, t.toLinerOd,
                )
            },
            onSetAftMode = { m ->
                onUpdateBodyBlend(
                    idx, blendLenForMode(m, b.blendAftMm, b.lengthMm), b.blendFwdMm,
                    b.blendProfile, b.blendAftSeal, b.blendFwdSeal,
                    b.blendAftSealLenMm, b.blendFwdSealLenMm,
                    b.blendAftToLinerOd, b.blendFwdToLinerOd,
                )
            },
            onSetFwdMode = { m ->
                onUpdateBodyBlend(
                    idx, b.blendAftMm, blendLenForMode(m, b.blendFwdMm, b.lengthMm),
                    b.blendProfile, b.blendAftSeal, b.blendFwdSeal,
                    b.blendAftSealLenMm, b.blendFwdSealLenMm,
                    b.blendAftToLinerOd, b.blendFwdToLinerOd,
                )
            },
            onSetAftSeal = { on ->
                onUpdateBodyBlend(
                    idx, b.blendAftMm, b.blendFwdMm, b.blendProfile, on, b.blendFwdSeal,
                    sealLenForSeal(on, b.blendAftSealLenMm, b.lengthMm), b.blendFwdSealLenMm,
                    b.blendAftToLinerOd, b.blendFwdToLinerOd,
                )
            },
            onSetFwdSeal = { on ->
                onUpdateBodyBlend(
                    idx, b.blendAftMm, b.blendFwdMm, b.blendProfile, b.blendAftSeal, on,
                    b.blendAftSealLenMm, sealLenForSeal(on, b.blendFwdSealLenMm, b.lengthMm),
                    b.blendAftToLinerOd, b.blendFwdToLinerOd,
                )
            },
            onProfile = { p ->
                onUpdateBodyBlend(
                    idx, b.blendAftMm, b.blendFwdMm, p, b.blendAftSeal, b.blendFwdSeal,
                    b.blendAftSealLenMm, b.blendFwdSealLenMm,
                    b.blendAftToLinerOd, b.blendFwdToLinerOd,
                )
            },
            aftLengthField = {
                CommitNum("Blend AFT (${abbr(unit)})", disp(b.blendAftMm, unit)) { str ->
                    toMmOrNull(str, unit)?.let {
                        onUpdateBodyBlend(
                            idx, it, b.blendFwdMm, b.blendProfile, b.blendAftSeal, b.blendFwdSeal,
                            b.blendAftSealLenMm, b.blendFwdSealLenMm,
                            b.blendAftToLinerOd, b.blendFwdToLinerOd,
                        )
                    }
                }
            },
            fwdLengthField = {
                CommitNum("Blend FWD (${abbr(unit)})", disp(b.blendFwdMm, unit)) { str ->
                    toMmOrNull(str, unit)?.let {
                        onUpdateBodyBlend(
                            idx, b.blendAftMm, it, b.blendProfile, b.blendAftSeal, b.blendFwdSeal,
                            b.blendAftSealLenMm, b.blendFwdSealLenMm,
                            b.blendAftToLinerOd, b.blendFwdToLinerOd,
                        )
                    }
                }
            },
            aftSealLengthField = {
                CommitNum("Seal area AFT (${abbr(unit)})", disp(b.blendAftSealLenMm, unit)) { str ->
                    toMmOrNull(str, unit)?.let {
                        onUpdateBodyBlend(
                            idx, b.blendAftMm, b.blendFwdMm, b.blendProfile, b.blendAftSeal, b.blendFwdSeal,
                            it, b.blendFwdSealLenMm,
                            b.blendAftToLinerOd, b.blendFwdToLinerOd,
                        )
                    }
                }
            },
            fwdSealLengthField = {
                CommitNum("Seal area FWD (${abbr(unit)})", disp(b.blendFwdSealLenMm, unit)) { str ->
                    toMmOrNull(str, unit)?.let {
                        onUpdateBodyBlend(
                            idx, b.blendAftMm, b.blendFwdMm, b.blendProfile, b.blendAftSeal, b.blendFwdSeal,
                            b.blendAftSealLenMm, it,
                            b.blendAftToLinerOd, b.blendFwdToLinerOd,
                        )
                    }
                }
            },
        )

        // Keyway — the whole section is gated behind the shared "Keyway" checkbox, so the
        // fields only appear once turned on (intermediate shafts with fitted couplings carry a
        // keyway in a plain end body). Mirrors the taper keyway section, with an AFT/FWD end
        // reference. The gate is derived, never stored: it seeds open for ANY typed W/D/L, so a
        // half-typed keyway keeps its fields on screen. Ticking only reveals the fields — the
        // model is untouched until a value commits.
        var kwEnabled by remember(b.id) { mutableStateOf(b.hasAnyKeywayValue) }
        // Stored values ALWAYS hold the section open, whatever the local tick says: an undo of a
        // confirmed removal brings the values back after the tick was cleared, and the only way
        // to close a section that holds values is the confirmed removal below.
        val kwOpen = kwEnabled || b.hasAnyKeywayValue
        var showRemoveKwDialog by remember(b.id) { mutableStateOf(false) }
        KeywayGateRow(checked = kwOpen, testTag = "body_kw_gate") { checked ->
            // Unticking with typed keyway values confirms first: one tap would otherwise erase
            // up to four typed values — the same reason the demote-to-auto checkbox above
            // confirms.
            when (keywayGateAction(checked, b.hasAnyKeywayValue)) {
                KeywayGateAction.REVEAL -> kwEnabled = true
                KeywayGateAction.CONFIRM_REMOVE -> showRemoveKwDialog = true
                KeywayGateAction.HIDE -> kwEnabled = false
            }
        }
        if (showRemoveKwDialog) {
            RemoveKeywayConfirmDialog(
                onConfirm = {
                    showRemoveKwDialog = false
                    onUpdateBodyKeyway(idx, 0f, 0f, 0f, 0f, b.keywayEnd, false)
                    kwEnabled = false
                },
                onDismiss = { showRemoveKwDialog = false },
            )
        }

        if (kwOpen) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                CommitNum("KW W (${abbr(kwUnit)})", dispKw(b.keywayWidthMm, kwUnit), modifier = Modifier.weight(1f), fillMaxWidth = false) { s ->
                    val v = if (s.isBlank()) 0f else (toMmOrNull(s, kwUnit) ?: return@CommitNum)
                    onUpdateBodyKeyway(idx, v, b.keywayDepthMm, b.keywayLengthMm, b.keywayOffsetFromEndMm, kwEnd, b.keywaySpooned)
                }
                KeywayTimesSign()
                CommitNum("KW D (${abbr(kwUnit)})", dispKw(b.keywayDepthMm, kwUnit), modifier = Modifier.weight(1f), fillMaxWidth = false) { s ->
                    val v = if (s.isBlank()) 0f else (toMmOrNull(s, kwUnit) ?: return@CommitNum)
                    onUpdateBodyKeyway(idx, b.keywayWidthMm, v, b.keywayLengthMm, b.keywayOffsetFromEndMm, kwEnd, b.keywaySpooned)
                }
            }
            // KW L / Inset parse in `kwUnit` like KW W/D — the keyway-unit chip governs what
            // EVERY keyway number means; parsing these two in the document unit under a kwUnit
            // label read a metric keyway's length as inches.
            CommitNum("KW L (${abbr(kwUnit)})", dispKw(b.keywayLengthMm, kwUnit)) { s ->
                val v = if (s.isBlank()) 0f else (toMmOrNull(s, kwUnit) ?: return@CommitNum)
                onUpdateBodyKeyway(idx, b.keywayWidthMm, b.keywayDepthMm, v, b.keywayOffsetFromEndMm, kwEnd, b.keywaySpooned)
            }

            // Captured ⇔ inset > 0: the toggle is derived from the stored offset, never stored
            // itself. The last inset cleared or typed is remembered for this body so re-capturing
            // restores it.
            val isCaptured = b.keywayOffsetFromEndMm > 0f
            var rememberedInsetMm by remember(b.id) { mutableStateOf(0f) }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("body_kw_captured")
                    .toggleable(
                        value = isCaptured,
                        role = androidx.compose.ui.semantics.Role.Switch,
                        onValueChange = { on ->
                            if (!on && b.keywayOffsetFromEndMm > 0f) rememberedInsetMm = b.keywayOffsetFromEndMm
                            val inset = keywayInsetForCaptured(on, b.keywayOffsetFromEndMm, rememberedInsetMm)
                            onUpdateBodyKeyway(idx, b.keywayWidthMm, b.keywayDepthMm, b.keywayLengthMm, inset, kwEnd, b.keywaySpooned)
                        }
                    ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Captured keyway", modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = isCaptured, onCheckedChange = null)
            }
            if (isCaptured) {
                // A ≤ 0 inset is not a way to un-capture: the toggle is the one control that
                // clears the inset, so the field refuses it and says so.
                CommitNum(
                    "KW Inset from ${if (isKwFwd) "FWD" else "AFT"} (${abbr(kwUnit)})",
                    dispKw(b.keywayOffsetFromEndMm, kwUnit),
                    validator = { raw -> capturedInsetErrorMm(toMmOrNull(raw, kwUnit)) },
                ) { s ->
                    val v = toMmOrNull(s, kwUnit) ?: return@CommitNum
                    rememberedInsetMm = v
                    onUpdateBodyKeyway(idx, b.keywayWidthMm, b.keywayDepthMm, b.keywayLengthMm, v, kwEnd, b.keywaySpooned)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(
                        value = b.keywaySpooned, enabled = !isCaptured,
                        role = androidx.compose.ui.semantics.Role.Switch,
                        onValueChange = { checked ->
                            onUpdateBodyKeyway(idx, b.keywayWidthMm, b.keywayDepthMm, b.keywayLengthMm, b.keywayOffsetFromEndMm, kwEnd, checked)
                        }
                    ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCaptured) "Keyway spooned (N/A — captured)" else "Keyway spooned",
                    modifier = Modifier.weight(1f),
                    color = if (isCaptured) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.material3.Switch(
                    checked = b.keywaySpooned && !isCaptured,
                    enabled = !isCaptured,
                    onCheckedChange = null
                )
            }

            // Inside the gate: a body with no keyway does not carry the clocking toggles even
            // when the shaft has two keyways elsewhere — they appear on the cards that carry
            // the keyways, which is where the control belongs.
            KeywayClockingSection(spec, onSetKeyways180Apart, onSetKeyways90Apart, onSetKeyways90Cw)
        }

        if (perComponentUnitsEnabled) {
            ComponentUnitChip(b.id, unit, unitOverrides, onSetComponentUnit)
        }
    }
}

/** Caption under "Show dimension rail" while the body follows its position default. */
internal fun bodyDimensionRailDefaultCaption(atShaftEnd: Boolean): String =
    if (atShaftEnd) BODY_RAIL_DEFAULT_END_CAPTION else BODY_RAIL_DEFAULT_MID_CAPTION

internal const val BODY_RAIL_DEFAULT_END_CAPTION = "On — body is at a shaft end"
internal const val BODY_RAIL_DEFAULT_MID_CAPTION = "Off — mid-shaft body"
