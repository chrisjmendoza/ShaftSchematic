package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.ui.config.AddDefaultsConfig
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.model.keywayCount
import com.android.shaftschematic.ui.input.NumericInputField
import com.android.shaftschematic.ui.input.shouldCommitOnBlur
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedCouplerBoltSlot
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.ResolvedThread
import com.android.shaftschematic.ui.resolved.resolvedBodyBaseId
import com.android.shaftschematic.ui.util.buildBodyTitleById
import com.android.shaftschematic.ui.util.buildLinerTitleById
import com.android.shaftschematic.ui.util.buildTaperTitleById
import com.android.shaftschematic.ui.util.buildThreadTitleById
import com.android.shaftschematic.ui.util.startOverlapErrorMm
import com.android.shaftschematic.util.LengthFormat
import com.android.shaftschematic.util.ThreadDesignation
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.parseFractionOrDecimal
import com.android.shaftschematic.util.toMmOrNull
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Carousel height constant
// ─────────────────────────────────────────────────────────────────────────────

internal val CAROUSEL_HEIGHT = 360.dp

// ─────────────────────────────────────────────────────────────────────────────
// Internal data model
// ─────────────────────────────────────────────────────────────────────────────

internal data class RowRef(
    val component: ResolvedComponent,
    val explicitIndex: Int? = null
)

/**
 * Pair every resolved component with the index of the stored component it edits
 * (`null` for auto-bodies, which have no stored row).
 *
 * A stored body trimmed around a taper/thread/liner resolves into several rows whose ids
 * carry a fragment suffix (`"<id>#2"`, …); they all edit the SAME stored body, so bodies
 * are looked up by their base id. Only bodies are ever fragmented.
 *
 * Pure function so the mapping is unit-testable outside composition.
 */
internal fun buildCarouselRows(
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>,
): List<RowRef> {
    val bodyIdx   = spec.bodies.withIndex().associate { it.value.id to it.index }
    val taperIdx  = spec.tapers.withIndex().associate { it.value.id to it.index }
    val threadIdx = spec.threads.withIndex().associate { it.value.id to it.index }
    val linerIdx  = spec.liners.withIndex().associate { it.value.id to it.index }
    val slotIdx   = spec.couplerBoltSlots.withIndex().associate { it.value.id to it.index }
    return resolvedComponents.map { comp ->
        val index = when (comp) {
            is ResolvedBody   -> bodyIdx[resolvedBodyBaseId(comp.id)]
            is ResolvedTaper  -> taperIdx[comp.id]
            is ResolvedThread -> threadIdx[comp.id]
            is ResolvedLiner  -> linerIdx[comp.id]
            is ResolvedCouplerBoltSlot -> slotIdx[comp.id]
        }
        RowRef(component = comp, explicitIndex = index)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ComponentCarouselPager
//
// Horizontal pager over resolved components. Handles selection seeding on load,
// swipe-to-select, and programmatic scroll when selection changes externally.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ComponentCarouselPager(
    spec: ShaftSpec,
    resolvedComponents: List<ResolvedComponent>,
    unit: UnitSystem,
    showEdgeArrows: Boolean,
    edgeArrowWidthDp: Int,
    showComponentDebugLabels: Boolean,
    /** The Settings "Show component titles" switch — what an UNSET per-component name toggle follows. */
    componentTitlesDefault: Boolean = true,
    /** The kind-level shade checkboxes — what an UNSET per-component shade toggle follows. */
    componentShadeDefaults: ComponentShadeDefaults = ComponentShadeDefaults(),
    selectedComponentId: String?,
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
    onUpdateTaper: (Int, Float, Float, Float, Float, String) -> Unit,
    onUpdateTaperLabel: (Int, String?) -> Unit,
    onUpdateTaperShowLabel: (Int, Boolean) -> Unit,
    onUpdateTaperShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateTaperKeyway: (index: Int, widthMm: Float, depthMm: Float, lengthMm: Float, offsetFromSetMm: Float, spooned: Boolean) -> Unit,
    onUpdateTaperReference: (Int, LinerAuthoredReference) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float, String?) -> Unit,
    onUpdateThreadLabel: (Int, String?) -> Unit,
    onUpdateThreadShowLabel: (Int, Boolean) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,
    onUpdateLinerShowDia: (Int, Boolean) -> Unit,
    onUpdateLinerShowLabel: (Int, Boolean) -> Unit,
    onUpdateLinerShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateLinerShoulder: (Int, LinerAuthoredReference, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    linerShouldersEnabled: Boolean = false,
    onUpdateLinerLabel: (Int, String?) -> Unit,
    onUpdateLinerReference: (Int, LinerAuthoredReference) -> Unit,
    onUpdateCouplerBoltSlot: (index: Int, startMm: Float, holeDiaMm: Float, count: Int, spacingMm: Float, through: Boolean, depthMm: Float) -> Unit,
    onUpdateCouplerBoltSlotReference: (Int, SlotAuthoredReference) -> Unit,
    onUpdateCouplerBoltSlotShowRail: (Int, Boolean) -> Unit,
    onSetKeyways180Apart: (Boolean) -> Unit,
    onSetKeyways90Apart: (Boolean) -> Unit,
    onSetKeyways90Cw: (Boolean) -> Unit,
    onSetThreadExcludeFromOal: (id: String, excludeFromOAL: Boolean) -> Unit,
    onSetThreadEndPosition: (id: String, isAft: Boolean) -> Unit,
    onRemoveBody: (String) -> Unit,
    onRemoveTaper: (String) -> Unit,
    onRemoveThread: (String) -> Unit,
    onRemoveLiner: (String) -> Unit,
    onRemoveCouplerBoltSlot: (String) -> Unit,
    onSelectComponentById: (String?) -> Unit,
    collidingComponentIds: Set<String> = emptySet(),
    // Mixed per-component units (Settings → Drawing → "Per-component units"). Off by
    // default so a document with the capability disabled draws every card identically
    // to before it existed. `unitOverrides` absent for an id means "follows the
    // document unit"; `onSetComponentUnit(id, null)` clears back to that default.
    perComponentUnitsEnabled: Boolean = false,
    unitOverrides: Map<String, UnitSystem> = emptyMap(),
    onSetComponentUnit: (String, UnitSystem?) -> Unit = { _, _ -> },
    /** Sets (null clears) the unit a component's KEYWAY is authored and printed in. */
    onSetKeywayUnit: (String, UnitSystem?) -> Unit = { _, _ -> },
) {
    val bodyTitleById   = remember(spec.bodies)                    { buildBodyTitleById(spec) }
    val taperTitleById  = remember(spec.tapers)                    { buildTaperTitleById(spec) }
    val linerTitleById  = remember(spec.liners, spec.overallLengthMm) { buildLinerTitleById(spec) }
    val threadTitleById = remember(spec)                           { buildThreadTitleById(spec) }

    val rowsSorted = remember(spec, resolvedComponents) {
        buildCarouselRows(spec, resolvedComponents)
    }

    val pageCount  = rowsSorted.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val scope      = rememberCoroutineScope()

    val arrowWidth             = if (showEdgeArrows) edgeArrowWidthDp.coerceIn(24, 72).dp else 0.dp
    val edgeGap                = if (showEdgeArrows) 1.dp else 0.dp
    val pageGutter             = if (showEdgeArrows) 2.dp else 16.dp
    val componentCardPadding   = if (showEdgeArrows) 4.dp else 8.dp

    var pagerScrollStartedByUser by remember { mutableStateOf(false) }
    var pagerStartPage           by remember { mutableStateOf<Int?>(null) }

    val rowIds = remember(rowsSorted) { rowsSorted.map { it.component.id } }

    // Seed selection on open (nothing selected → FIRST card, the AFT-most component) and
    // self-heal an orphaned selection (id no longer resolves — auto-body ids are
    // position-derived and regenerate on every edit) by adopting the current page WITHOUT
    // scrolling. Keyed on rows + selection, not rows.size: a document open that lands on
    // the same row count must still seed, and a stale seed from the open-time race (rows
    // update a frame after the selection clears) heals itself on the next emission.
    // When components are added we still don't auto-jump — the VM selects the new id, which
    // resolves, so the action is NONE and the selection-following effect repositions.
    LaunchedEffect(rowsSorted, selectedComponentId) {
        when (seedSelectionAction(rowsSorted.size, selectedComponentId, carouselTargetIndex(rowIds, selectedComponentId))) {
            SeedAction.SEED_FIRST -> {
                pagerState.scrollToPage(0)
                onSelectComponentById(rowsSorted.firstOrNull()?.component?.id)
            }
            SeedAction.ADOPT_CURRENT -> {
                val page = pagerState.currentPage.coerceIn(0, rowsSorted.lastIndex)
                onSelectComponentById(rowsSorted.getOrNull(page)?.component?.id)
            }
            SeedAction.NONE -> Unit
        }
    }

    // Follow programmatic selection changes.
    LaunchedEffect(selectedComponentId, rowsSorted) {
        val targetIndex = carouselTargetIndex(rowIds, selectedComponentId)
        if (shouldAnimateToSelection(
                targetIndex,
                pagerState.currentPage,
                pagerState.currentPageOffsetFraction
            )
        ) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    // Detect user swipes and update selection accordingly.
    LaunchedEffect(pagerState.isScrollInProgress, selectedComponentId, rowsSorted) {
        if (pagerState.isScrollInProgress) {
            val selectedIndex = carouselTargetIndex(rowIds, selectedComponentId)
            if (isUserInitiatedScroll(selectedComponentId, selectedIndex, pagerState.currentPage)) {
                pagerScrollStartedByUser = true
                pagerStartPage = pagerState.currentPage
            }
        } else if (pagerScrollStartedByUser) {
            val endPage = pagerState.currentPage
            if (shouldAdoptSwipeSelection(pagerStartPage, endPage)) {
                onSelectComponentById(rowsSorted.getOrNull(endPage)?.component?.id)
            }
            pagerScrollStartedByUser = false
            pagerStartPage = null
        }
    }

    Row(Modifier.fillMaxWidth().height(CAROUSEL_HEIGHT)) {
        if (showEdgeArrows) {
            EdgeNavButton(
                left = true,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                },
                modifier = Modifier.fillMaxHeight().width(arrowWidth).padding(start = edgeGap)
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            // Key pages by component id so per-page state (scroll, focus) follows the
            // component when one is inserted/removed, instead of staying positional.
            key = { page -> rowsSorted.getOrNull(page)?.component?.id ?: page }
        ) { page ->
            Box(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
                val row = rowsSorted.getOrNull(page) ?: return@HorizontalPager
                ComponentPagerCard(
                    spec = spec, unit = unit, row = row, physicalIndex = page,
                    outerPaddingHorizontal = componentCardPadding,
                    showComponentDebugLabels = showComponentDebugLabels,
                    componentTitlesDefault = componentTitlesDefault,
                    componentShadeDefaults = componentShadeDefaults,
                    onAddBody = onAddBody,
                    onSetAutoSectionDia = onSetAutoSectionDia,
                    onSetAutoBlend = onSetAutoBlend,
                    onSetShowAutoBodyDia = onSetShowAutoBodyDia,
                    onUpdateBody = onUpdateBody,
                    onUpdateBodyShowDia = onUpdateBodyShowDia,
                    onUpdateBodyShowLabel = onUpdateBodyShowLabel,
                    onUpdateBodyShade = onUpdateBodyShade,
                    onUpdateBodyCompressOnDrawing = onUpdateBodyCompressOnDrawing,
                    onUpdateBodyBlend = onUpdateBodyBlend,
                    onUpdateBodyLabel = onUpdateBodyLabel,
                    onUpdateBodyKeyway = onUpdateBodyKeyway,
                    onUpdateTaper = onUpdateTaper,
                    onUpdateTaperLabel = onUpdateTaperLabel,
                    onUpdateTaperShowLabel = onUpdateTaperShowLabel,
                    onUpdateTaperShade = onUpdateTaperShade,
                    onUpdateTaperKeyway = onUpdateTaperKeyway,
                    onUpdateTaperReference = onUpdateTaperReference,
                    onUpdateThread = onUpdateThread,
                    onUpdateThreadLabel = onUpdateThreadLabel,
                    onUpdateThreadShowLabel = onUpdateThreadShowLabel,
                    onUpdateLiner = onUpdateLiner,
                    onUpdateLinerShowDia = onUpdateLinerShowDia,
                    onUpdateLinerShowLabel = onUpdateLinerShowLabel,
                    onUpdateLinerShade = onUpdateLinerShade,
                    onUpdateLinerShoulder = onUpdateLinerShoulder,
                    linerShouldersEnabled = linerShouldersEnabled,
                    onUpdateLinerLabel = onUpdateLinerLabel,
                    onUpdateLinerReference = onUpdateLinerReference,
                    onUpdateCouplerBoltSlot = onUpdateCouplerBoltSlot,
                    onUpdateCouplerBoltSlotReference = onUpdateCouplerBoltSlotReference,
                    onUpdateCouplerBoltSlotShowRail = onUpdateCouplerBoltSlotShowRail,
                    onSetKeyways180Apart = onSetKeyways180Apart,
                    onSetKeyways90Apart = onSetKeyways90Apart,
                    onSetKeyways90Cw = onSetKeyways90Cw,
                    bodyTitleById = bodyTitleById, taperTitleById = taperTitleById,
                    linerTitleById = linerTitleById, threadTitleById = threadTitleById,
                    onSetThreadExcludeFromOal = onSetThreadExcludeFromOal,
                    onSetThreadEndPosition = onSetThreadEndPosition,
                    onRemoveBody = onRemoveBody, onRemoveTaper = onRemoveTaper,
                    onRemoveThread = onRemoveThread, onRemoveLiner = onRemoveLiner,
                    onRemoveCouplerBoltSlot = onRemoveCouplerBoltSlot,
                    collidingComponentIds = collidingComponentIds,
                    perComponentUnitsEnabled = perComponentUnitsEnabled,
                    unitOverrides = unitOverrides,
                    onSetComponentUnit = onSetComponentUnit,
                    onSetKeywayUnit = onSetKeywayUnit,
                )
            }
        }

        if (showEdgeArrows) {
            EdgeNavButton(
                left = false,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                        )
                    }
                },
                modifier = Modifier.fillMaxHeight().width(arrowWidth).padding(end = edgeGap)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Keyway clocking — spec-level drawing notes ("Keyways 180° apart" / "Keyways 90°
// apart" + CW/CCW direction from the AFT keyway), surfaced on any keyway-bearing
// card once the shaft has two or more keyways (below that neither flag means
// anything). The two toggles are mutually exclusive; the ViewModel setters own
// that exclusion (enabling one clears the other) — this section only reflects
// current spec state and dispatches the user's tap.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun KeywayClockingSection(
    spec: ShaftSpec,
    onSetKeyways180Apart: (Boolean) -> Unit,
    onSetKeyways90Apart: (Boolean) -> Unit,
    onSetKeyways90Cw: (Boolean) -> Unit,
) {
    if (spec.keywayCount() < 2) return
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(
                value = spec.keyways180Apart,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = onSetKeyways180Apart
            ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Keyways 180° apart", modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = spec.keyways180Apart,
            onCheckedChange = null
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(
                value = spec.keyways90Apart,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = onSetKeyways90Apart
            ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Keyways 90° apart", modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = spec.keyways90Apart,
            onCheckedChange = null
        )
    }
    if (spec.keyways90Apart) {
        val clockingChipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color.Black,
            selectedLabelColor = Color.White,
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "From AFT keyway, viewed from aft:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilterChip(selected = spec.keyways90Cw, onClick = { onSetKeyways90Cw(true) },
                label = { Text("CW") }, colors = clockingChipColors,
                border = if (spec.keyways90Cw) BorderStroke(1.dp, Color.Black) else null)
            FilterChip(selected = !spec.keyways90Cw, onClick = { onSetKeyways90Cw(false) },
                label = { Text("CCW") }, colors = clockingChipColors,
                border = if (!spec.keyways90Cw) BorderStroke(1.dp, Color.Black) else null)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-component unit chip — Settings → Drawing → "Per-component units". Governs how
// THIS component prints (below-shaft callouts, footer, etc.), never the units its own
// fields are entered in above: those always stay in the document unit. Absent from
// `unitOverrides` reads as "follows the document unit"; tapping the already-selected
// chip is a no-op (there is nothing to clear), and choosing the unit that already
// equals the document unit clears the override instead of storing a redundant one.
//
// It sits at the BOTTOM of every card that has it, under the values it governs: it is a
// post-hoc display choice reached for after looking at a printed sheet, not something to
// decide before typing a dimension, and giving it the top of the card read as a priority
// it does not have (on-device report). The "Explicit body" checkbox is the opposite case
// and keeps the top — it changes what the card IS, and moving it would make it jump when
// checking it swaps an auto card for an explicit one.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UnitChoiceChips(
    label: String,
    effective: UnitSystem,
    /** Choosing this unit CLEARS the override instead of storing a redundant one. */
    inheritedUnit: UnitSystem,
    onChoose: (UnitSystem?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Color.Black, selectedLabelColor = Color.White,
        containerColor = Color.Transparent, labelColor = MaterialTheme.colorScheme.onSurface
    )
    fun choose(u: UnitSystem) = onChoose(if (u == inheritedUnit) null else u)
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilterChip(selected = effective == UnitSystem.INCHES,
            onClick = { choose(UnitSystem.INCHES) },
            label = { Text("in") }, colors = chipColors,
            border = if (effective == UnitSystem.INCHES) BorderStroke(1.dp, Color.Black) else null)
        FilterChip(selected = effective == UnitSystem.MILLIMETERS,
            onClick = { choose(UnitSystem.MILLIMETERS) },
            label = { Text("mm") }, colors = chipColors,
            border = if (effective == UnitSystem.MILLIMETERS) BorderStroke(1.dp, Color.Black) else null)
    }
}

@Composable
internal fun ComponentUnitChip(
    componentId: String,
    documentUnit: UnitSystem,
    unitOverrides: Map<String, UnitSystem>,
    onSetComponentUnit: (String, UnitSystem?) -> Unit,
) {
    UnitChoiceChips(
        label = "Prints in:",
        effective = unitOverrides[componentId] ?: documentUnit,
        inheritedUnit = documentUnit,
        onChoose = { onSetComponentUnit(componentId, it) },
    )
}

/**
 * The unit a KEYWAY is authored and printed in — the keyway analogue of the Add Thread dialog's
 * Imperial/Metric mode, and the other half of the mixed-unit case a European shaft brings.
 *
 * Unlike [ComponentUnitChip] this governs the KW fields' own entry too, so it sits WITH those
 * fields rather than at the foot of the card: it changes what the number you are about to type
 * means. Choosing the component's own unit clears the override, so the keyway goes back to
 * following its parent.
 */
@Composable
internal fun KeywayUnitChip(
    componentId: String,
    documentUnit: UnitSystem,
    unitOverrides: Map<String, UnitSystem>,
    onSetKeywayUnit: (String, UnitSystem?) -> Unit,
) {
    val units = DisplayUnits(documentUnit, unitOverrides)
    UnitChoiceChips(
        label = "Keyway in:",
        effective = units.keywayUnitFor(componentId),
        inheritedUnit = units.unitFor(componentId),
        onChoose = { onSetKeywayUnit(componentId, it) },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// EdgeNavButton — left/right arrow for the pager
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EdgeNavButton(left: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scrim = androidx.compose.ui.graphics.Brush.verticalGradient(
        0f   to MaterialTheme.colorScheme.surface.copy(alpha = 0.0f),
        0.5f to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        1f   to MaterialTheme.colorScheme.surface.copy(alpha = 0.0f)
    )
    Box(
        modifier.background(scrim, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = if (left) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Text(
            text = if (left) "◀" else "▶",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ComponentPagerCard — per-component editor content, dispatched by type
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ComponentPagerCard(
    spec: ShaftSpec,
    unit: UnitSystem,
    row: RowRef,
    physicalIndex: Int,
    outerPaddingHorizontal: Dp,
    showComponentDebugLabels: Boolean,
    componentTitlesDefault: Boolean = true,
    componentShadeDefaults: ComponentShadeDefaults = ComponentShadeDefaults(),
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
    onUpdateTaper: (Int, Float, Float, Float, Float, String) -> Unit,
    onUpdateTaperLabel: (Int, String?) -> Unit,
    onUpdateTaperShowLabel: (Int, Boolean) -> Unit,
    onUpdateTaperShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateTaperKeyway: (index: Int, widthMm: Float, depthMm: Float, lengthMm: Float, offsetFromSetMm: Float, spooned: Boolean) -> Unit,
    onUpdateTaperReference: (Int, LinerAuthoredReference) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float, String?) -> Unit,
    onUpdateThreadLabel: (Int, String?) -> Unit,
    onUpdateThreadShowLabel: (Int, Boolean) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,
    onUpdateLinerShowDia: (Int, Boolean) -> Unit,
    onUpdateLinerShowLabel: (Int, Boolean) -> Unit,
    onUpdateLinerShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateLinerShoulder: (Int, LinerAuthoredReference, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    linerShouldersEnabled: Boolean = false,
    onUpdateLinerLabel: (Int, String?) -> Unit,
    onUpdateLinerReference: (Int, LinerAuthoredReference) -> Unit,
    onUpdateCouplerBoltSlot: (index: Int, startMm: Float, holeDiaMm: Float, count: Int, spacingMm: Float, through: Boolean, depthMm: Float) -> Unit,
    onUpdateCouplerBoltSlotReference: (Int, SlotAuthoredReference) -> Unit,
    onUpdateCouplerBoltSlotShowRail: (Int, Boolean) -> Unit,
    onSetKeyways180Apart: (Boolean) -> Unit,
    onSetKeyways90Apart: (Boolean) -> Unit,
    onSetKeyways90Cw: (Boolean) -> Unit,
    bodyTitleById: Map<String, String>,
    taperTitleById: Map<String, String>,
    linerTitleById: Map<String, String>,
    threadTitleById: Map<String, String>,
    onSetThreadExcludeFromOal: (id: String, excludeFromOAL: Boolean) -> Unit,
    onSetThreadEndPosition: (id: String, isAft: Boolean) -> Unit,
    onRemoveBody: (String) -> Unit,
    onRemoveTaper: (String) -> Unit,
    onRemoveThread: (String) -> Unit,
    onRemoveLiner: (String) -> Unit,
    onRemoveCouplerBoltSlot: (String) -> Unit,
    collidingComponentIds: Set<String> = emptySet(),
    perComponentUnitsEnabled: Boolean = false,
    unitOverrides: Map<String, UnitSystem> = emptyMap(),
    onSetComponentUnit: (String, UnitSystem?) -> Unit = { _, _ -> },
    /** Sets (null clears) the unit a component's KEYWAY is authored and printed in. */
    onSetKeywayUnit: (String, UnitSystem?) -> Unit = { _, _ -> },
) {
    fun f1(mm: Float): String = "%.1f".format(mm)

    fun startValidator(selfId: String, selfKind: ComponentKind, selfLengthMm: Float): (String) -> String? {
        return fun(raw: String): String? {
            val startMm = toMmOrNull(raw, unit) ?: return "Enter a number"
            return startOverlapErrorMm(spec, selfId, selfKind, selfLengthMm, startMm)
        }
    }

    val component    = row.component
    val explicitIndex = row.explicitIndex

    when (component) {

        // ── Body ──────────────────────────────────────────────────────────────
        is ResolvedBody -> BodyPagerCard(
            component = component,
            explicitIndex = explicitIndex,
            spec = spec,
            unit = unit,
            physicalIndex = physicalIndex,
            outerPaddingHorizontal = outerPaddingHorizontal,
            showComponentDebugLabels = showComponentDebugLabels,
            componentTitlesDefault = componentTitlesDefault,
            componentShadeDefaults = componentShadeDefaults,
            bodyTitleById = bodyTitleById,
            f1 = ::f1,
            startValidator = ::startValidator,
            onAddBody = onAddBody,
            onSetAutoSectionDia = onSetAutoSectionDia,
            onSetAutoBlend = onSetAutoBlend,
            onSetShowAutoBodyDia = onSetShowAutoBodyDia,
            onUpdateBody = onUpdateBody,
            onUpdateBodyShowDia = onUpdateBodyShowDia,
            onUpdateBodyShowLabel = onUpdateBodyShowLabel,
            onUpdateBodyShade = onUpdateBodyShade,
            onUpdateBodyCompressOnDrawing = onUpdateBodyCompressOnDrawing,
            onUpdateBodyBlend = onUpdateBodyBlend,
            onUpdateBodyLabel = onUpdateBodyLabel,
            onUpdateBodyKeyway = onUpdateBodyKeyway,
            onSetKeyways180Apart = onSetKeyways180Apart,
            onSetKeyways90Apart = onSetKeyways90Apart,
            onSetKeyways90Cw = onSetKeyways90Cw,
            onRemoveBody = onRemoveBody,
            collidingComponentIds = collidingComponentIds,
            perComponentUnitsEnabled = perComponentUnitsEnabled,
            unitOverrides = unitOverrides,
            onSetComponentUnit = onSetComponentUnit,
            onSetKeywayUnit = onSetKeywayUnit,
        )

        // ── Taper ─────────────────────────────────────────────────────────────
        is ResolvedTaper -> TaperPagerCard(
            component = component,
            explicitIndex = explicitIndex,
            spec = spec,
            unit = unit,
            physicalIndex = physicalIndex,
            outerPaddingHorizontal = outerPaddingHorizontal,
            showComponentDebugLabels = showComponentDebugLabels,
            componentTitlesDefault = componentTitlesDefault,
            componentShadeDefaults = componentShadeDefaults,
            taperTitleById = taperTitleById,
            f1 = ::f1,
            onUpdateTaper = onUpdateTaper,
            onUpdateTaperLabel = onUpdateTaperLabel,
            onUpdateTaperShowLabel = onUpdateTaperShowLabel,
            onUpdateTaperShade = onUpdateTaperShade,
            onUpdateTaperKeyway = onUpdateTaperKeyway,
            onUpdateTaperReference = onUpdateTaperReference,
            onSetKeyways180Apart = onSetKeyways180Apart,
            onSetKeyways90Apart = onSetKeyways90Apart,
            onSetKeyways90Cw = onSetKeyways90Cw,
            onRemoveTaper = onRemoveTaper,
            collidingComponentIds = collidingComponentIds,
            perComponentUnitsEnabled = perComponentUnitsEnabled,
            unitOverrides = unitOverrides,
            onSetComponentUnit = onSetComponentUnit,
            onSetKeywayUnit = onSetKeywayUnit,
        )

        // ── Thread ────────────────────────────────────────────────────────────
        is ResolvedThread -> ThreadPagerCard(
            component = component,
            explicitIndex = explicitIndex,
            spec = spec,
            unit = unit,
            physicalIndex = physicalIndex,
            outerPaddingHorizontal = outerPaddingHorizontal,
            showComponentDebugLabels = showComponentDebugLabels,
            componentTitlesDefault = componentTitlesDefault,
            threadTitleById = threadTitleById,
            f1 = ::f1,
            startValidator = ::startValidator,
            onUpdateThread = onUpdateThread,
            onUpdateThreadLabel = onUpdateThreadLabel,
            onUpdateThreadShowLabel = onUpdateThreadShowLabel,
            onSetThreadExcludeFromOal = onSetThreadExcludeFromOal,
            onSetThreadEndPosition = onSetThreadEndPosition,
            onRemoveThread = onRemoveThread,
            collidingComponentIds = collidingComponentIds,
            perComponentUnitsEnabled = perComponentUnitsEnabled,
            unitOverrides = unitOverrides,
            onSetComponentUnit = onSetComponentUnit,
        )

        // ── Liner ─────────────────────────────────────────────────────────────
        is ResolvedLiner -> LinerPagerCard(
            component = component,
            explicitIndex = explicitIndex,
            spec = spec,
            unit = unit,
            physicalIndex = physicalIndex,
            outerPaddingHorizontal = outerPaddingHorizontal,
            showComponentDebugLabels = showComponentDebugLabels,
            componentTitlesDefault = componentTitlesDefault,
            componentShadeDefaults = componentShadeDefaults,
            linerTitleById = linerTitleById,
            f1 = ::f1,
            onUpdateLiner = onUpdateLiner,
            onUpdateLinerShowDia = onUpdateLinerShowDia,
            onUpdateLinerShowLabel = onUpdateLinerShowLabel,
            onUpdateLinerShade = onUpdateLinerShade,
            onUpdateLinerShoulder = onUpdateLinerShoulder,
            linerShouldersEnabled = linerShouldersEnabled,
            onUpdateLinerLabel = onUpdateLinerLabel,
            onUpdateLinerReference = onUpdateLinerReference,
            onRemoveLiner = onRemoveLiner,
            collidingComponentIds = collidingComponentIds,
            perComponentUnitsEnabled = perComponentUnitsEnabled,
            unitOverrides = unitOverrides,
            onSetComponentUnit = onSetComponentUnit,
        )

        // ── Coupler bolt slot ──────────────────────────────────────────────────
        is ResolvedCouplerBoltSlot -> CouplerBoltSlotPagerCard(
            component = component,
            explicitIndex = explicitIndex,
            spec = spec,
            unit = unit,
            outerPaddingHorizontal = outerPaddingHorizontal,
            showComponentDebugLabels = showComponentDebugLabels,
            f1 = ::f1,
            onUpdateCouplerBoltSlot = onUpdateCouplerBoltSlot,
            onUpdateCouplerBoltSlotReference = onUpdateCouplerBoltSlotReference,
            onUpdateCouplerBoltSlotShowRail = onUpdateCouplerBoltSlotShowRail,
            onRemoveCouplerBoltSlot = onRemoveCouplerBoltSlot,
        )
    }
}

/**
 * Switch row for a per-component schematic display flag — "Show Ø on drawing" (sitting directly
 * under the Ø field it modifies) and "Show name on drawing".
 *
 * Draw-only: neither switch touches a stored value. Hiding a Ø is for a surface whose diameter
 * could not be measured where the callout would land (a body under fiberglass, a sleeved run) —
 * the printed anchor then moves to the longest still-visible component sharing that Ø. Hiding a
 * name drops that component's label from the schematic's label pass and nothing else.
 *
 * Deliberately card-only, with no Add-dialog counterpart: like the coupler slot's
 * "Show dimension rail" these are post-hoc display choices made after seeing a printed sheet,
 * not properties of the component being added. See `docs/contracts/AddComponentDialogs.md`.
 */
/**
 * The three kind-level shade checkboxes (Settings → Drawing, mirrored in both PDF options
 * sheets) as one immutable holder — the DEFAULT each card's unset "Shade on drawing" toggle
 * displays. Carried together because every card row needs exactly one of them and threading
 * three loose Booleans through the pager would read as three unrelated flags.
 *
 * A body's default is plain `shadedBodies`: `shadeExplicitBodiesOnly` narrows AUTO runs only,
 * and every card carrying this row is an explicit component.
 */
data class ComponentShadeDefaults(
    val bodies: Boolean = false,
    val tapers: Boolean = false,
    val liners: Boolean = false,
)

@Composable
internal fun ShowDiaToggleRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        // The tag rides on the Switch — the clickable control — so a test's performClick on
        // it toggles instead of landing on an inert Row.
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

/**
 * The card title, renamed in place — the one implementation behind every component card's
 * [ComponentCard.titleContent].
 *
 * Tapping the title swaps it for a text field seeded with the stored [label] (the computed
 * [title] shows as the placeholder, so clearing the field restores the derived name). The edit
 * commits the TRIMMED text — blank meaning "no custom label", i.e. `null` — on IME Done and on
 * focus loss, the latter only once the field has actually held focus: Compose delivers an
 * initial unfocused callback on attach, and committing on it would write a label with no user
 * edit (the [com.android.shaftschematic.ui.input.shouldCommitOnBlur] baseline rule).
 *
 * The trailing pencil is discoverability only — it opens the same editor as the title tap. Tapping
 * the title alone was not discoverable (on-device report).
 */
@Composable
internal fun EditableCardTitle(
    componentId: String,
    title: String,
    label: String?,
    onCommitLabel: (String?) -> Unit,
) {
    var editing by rememberSaveable(componentId) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var hasFocusedOnce by remember(componentId) { mutableStateOf(false) }

    if (!editing) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { editing = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Rename",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp).testTag("card_title_edit"),
            )
        }
    } else {
        var text by remember(componentId, label) { mutableStateOf(label.orEmpty()) }
        LaunchedEffect(componentId) { focusRequester.requestFocus() }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text(title) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onCommitLabel(text.trim().takeIf { it.isNotEmpty() })
                editing = false
            }),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                .onFocusChanged { f ->
                    if (f.isFocused) hasFocusedOnce = true
                    if (hasFocusedOnce && !f.isFocused) {
                        onCommitLabel(text.trim().takeIf { it.isNotEmpty() })
                        editing = false
                    }
                }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ComponentCard — shared card chrome for all component editors
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ComponentCard(
    title: String,
    titleContent: (@Composable () -> Unit)? = null,
    debugText: String? = null,
    errorMessage: String? = null,
    warningMessage: String? = null,
    componentId: String? = null,
    componentKind: ComponentKind? = null,
    outerPaddingHorizontal: Dp = 8.dp,
    onRemove: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = outerPaddingHorizontal),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // The Remove button lives IN the title row so it scrolls with the card —
                // floated over the Box it would sit transparently on top of whatever the
                // card content scrolled beneath it (on-device report).
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (titleContent != null) titleContent()
                        else Text(title, style = MaterialTheme.typography.titleMedium)
                    }
                    if (onRemove != null) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.testTag("card_remove_button")
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (debugText != null) {
                    Text(debugText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (errorMessage != null) {
                    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                        Text(errorMessage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                if (warningMessage != null) {
                    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                        Text(warningMessage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                content()

                // Explicit commit affordance for the card's numeric fields. Fields commit on
                // blur and on IME Done, but chips, toggles, and checkboxes never TAKE focus —
                // so a value typed and followed by a chip tap sits uncommitted in a still-
                // focused field with nothing visible wrong (on-device report: a body keyway
                // length that never landed). Save force-clears focus, which drives the one
                // existing commit path (`shouldCommitOnBlur`); it adds no second commit
                // pipeline, and with nothing focused it is a no-op. Card-only by design —
                // the Add dialogs commit through their own Add button.
                run {
                    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(
                            onClick = { focusManager.clearFocus(force = true) },
                            modifier = Modifier.testTag("card_save_button"),
                        ) { Text("Save") }
                    }
                }
            }

        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Carousel-private helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Numeric input field with commit-on-blur, fraction support, and optional inline validator. */
@Composable
internal fun CommitNum(
    label: String,
    initialDisplay: String,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = true,
    showValidationErrors: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    allowColon: Boolean = false,
    enabled: Boolean = true,
    externalIssueText: String? = null,
    parseValid: (String) -> Boolean = { parseFractionOrDecimal(it) != null },
    validator: ((String) -> String?)? = null,
    onCommit: (String) -> Unit
) {
    NumericInputField(
        label = label,
        initialText = initialDisplay,
        modifier = Modifier.let { if (fillMaxWidth) it.fillMaxWidth() else it }.then(modifier),
        enabled = enabled,
        externalIssueText = externalIssueText,
        allowNegative = false,
        allowFraction = true,
        allowColon = allowColon,
        showValidationErrors = showValidationErrors,
        keyboardType = keyboardType,
        validator = validator,
        parseValid = parseValid,
        onCommit = onCommit
    )
}

/**
 * Free-text commit-on-blur field for the metric thread designation (e.g. "M20×2.5").
 * Unlike [CommitNum] this does not filter input to numeric characters — a designation
 * carries a leading "M" and a "×"/"x" separator — so it commits the raw typed text
 * verbatim and lets the caller parse it (`ThreadDesignation.parse`).
 */
@Composable
internal fun CommitDesignationField(
    label: String,
    initialText: String,
    onCommit: (String) -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    var textWhenFocused by remember(initialText) { mutableStateOf<String?>(null) }
    val isValid = ThreadDesignation.parse(text) != null
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        isError = !isValid,
        supportingText = if (!isValid) { { Text("e.g. M20×2.5") } } else null,
        modifier = Modifier.fillMaxWidth()
            .onFocusChanged { f ->
                if (f.isFocused) {
                    textWhenFocused = text
                } else if (shouldCommitOnBlur(textWhenFocused, text)) {
                    onCommit(text)
                    textWhenFocused = null
                }
            }
    )
}

/** Display keyway dimensions, preferring shop fractions in imperial. */
internal fun dispKw(mm: Float, unit: UnitSystem): String = when (unit) {
    UnitSystem.INCHES -> if (kotlin.math.abs(mm) < 1e-6f) "0" else
        LengthFormat.formatInchesSmart(inches = mm.toDouble() / 25.4, opts = LengthFormat.InchFormatOptions(maxDenominator = 32))
    UnitSystem.MILLIMETERS -> disp(mm, unit)
}

internal fun Float.fmtTrim(d: Int) = "%.${d}f".format(this).trimEnd('0').trimEnd('.')

internal fun pitchMmToTpi(pitchMm: Float): Float = if (pitchMm > 0f) 25.4f / pitchMm else 0f

/**
 * Starting blend length when a face is first switched on: the 2 in preset, or a quarter of a
 * body too short to host it. A starting value only — the user types over it, and nothing
 * re-derives it afterwards.
 */
internal fun defaultBlendMm(bodyLengthMm: Float): Float {
    val preset = AddDefaultsConfig.BLEND_LEN_IN * 25.4f
    return if (bodyLengthMm > 0f) minOf(preset, bodyLengthMm * 0.25f) else preset
}

/** The stored (length, seal) pair read back as the face's mode. */
internal fun blendFaceMode(lengthMm: Float, seal: Boolean): BlendFaceMode = when {
    lengthMm <= 0f -> BlendFaceMode.SQUARE
    seal -> BlendFaceMode.SEAL
    else -> BlendFaceMode.BLEND
}

/**
 * The blend length a mode change should commit: zero for a square face, otherwise whatever the
 * face already carried, falling back to the starting preset. Switching Blend ↔ Seal area keeps
 * the typed length — the two differ only by the cuts.
 */
internal fun blendLenForMode(mode: BlendFaceMode, currentMm: Float, bodyLengthMm: Float): Float =
    if (mode == BlendFaceMode.SQUARE) 0f
    else currentMm.takeIf { it > 0f } ?: defaultBlendMm(bodyLengthMm)
