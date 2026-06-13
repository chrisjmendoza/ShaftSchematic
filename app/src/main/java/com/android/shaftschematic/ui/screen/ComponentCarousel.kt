package com.android.shaftschematic.ui.screen

import android.util.Log
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.input.NumericInputField
import com.android.shaftschematic.ui.input.taperSetLetMapping
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.ResolvedLiner
import com.android.shaftschematic.ui.resolved.ResolvedTaper
import com.android.shaftschematic.ui.resolved.ResolvedThread
import com.android.shaftschematic.ui.util.bodyWarningMessage
import com.android.shaftschematic.ui.util.buildBodyTitleById
import com.android.shaftschematic.ui.util.buildLinerTitleById
import com.android.shaftschematic.ui.util.buildTaperTitleById
import com.android.shaftschematic.ui.util.buildThreadTitleById
import com.android.shaftschematic.ui.util.linerWarningMessage
import com.android.shaftschematic.ui.util.startOverlapErrorMm
import com.android.shaftschematic.ui.util.taperWarningMessage
import com.android.shaftschematic.ui.util.threadWarningMessage
import com.android.shaftschematic.util.LengthFormat
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
    componentOrder: List<ComponentKey>,
    showEdgeArrows: Boolean,
    edgeArrowWidthDp: Int,
    showComponentDebugLabels: Boolean,
    selectedComponentId: String?,
    onAddBody: (Float, Float, Float) -> Unit,
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateTaper: (Int, Float, Float, Float, Float, String) -> Unit,
    onUpdateTaperKeyway: (index: Int, widthMm: Float, depthMm: Float, lengthMm: Float, offsetFromSetMm: Float, spooned: Boolean) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,
    onUpdateLinerLabel: (Int, String?) -> Unit,
    onUpdateLinerReference: (Int, LinerAuthoredReference) -> Unit,
    onSetThreadExcludeFromOal: (id: String, excludeFromOAL: Boolean) -> Unit,
    onSetThreadEndPosition: (id: String, isAft: Boolean) -> Unit,
    onRemoveBody: (String) -> Unit,
    onRemoveTaper: (String) -> Unit,
    onRemoveThread: (String) -> Unit,
    onRemoveLiner: (String) -> Unit,
    onSelectComponentById: (String?) -> Unit,
    collidingComponentIds: Set<String> = emptySet(),
) {
    val bodyTitleById   = remember(spec.bodies)                    { buildBodyTitleById(spec) }
    val taperTitleById  = remember(spec.tapers)                    { buildTaperTitleById(spec) }
    val linerTitleById  = remember(spec.liners, spec.overallLengthMm) { buildLinerTitleById(spec) }
    val threadTitleById = remember(spec)                           { buildThreadTitleById(spec) }

    val rowsSorted = remember(spec, resolvedComponents) {
        val bodyIdx   = spec.bodies.withIndex().associate { it.value.id to it.index }
        val taperIdx  = spec.tapers.withIndex().associate { it.value.id to it.index }
        val threadIdx = spec.threads.withIndex().associate { it.value.id to it.index }
        val linerIdx  = spec.liners.withIndex().associate { it.value.id to it.index }
        resolvedComponents.mapNotNull { comp ->
            val index = when (comp) {
                is ResolvedBody   -> bodyIdx[comp.id]
                is ResolvedTaper  -> taperIdx[comp.id]
                is ResolvedThread -> threadIdx[comp.id]
                is ResolvedLiner  -> linerIdx[comp.id]
            }
            RowRef(component = comp, explicitIndex = index)
        }
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

    // Auto-jump to newest card after insertion; seed selection on initial load.
    LaunchedEffect(rowsSorted.size) {
        if (rowsSorted.isNotEmpty()) {
            val newPage = rowsSorted.size - 1
            pagerState.scrollToPage(newPage)
            if (selectedComponentId == null) {
                onSelectComponentById(rowsSorted.getOrNull(newPage)?.component?.id)
            }
        }
    }

    // Follow programmatic selection changes.
    LaunchedEffect(selectedComponentId, rowsSorted) {
        val targetIndex = selectedComponentId?.let { id ->
            rowsSorted.indexOfFirst { it.component.id == id }
        } ?: -1
        if (targetIndex >= 0 &&
            (pagerState.currentPage != targetIndex || pagerState.currentPageOffsetFraction != 0f)
        ) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    // Detect user swipes and update selection accordingly.
    LaunchedEffect(pagerState.isScrollInProgress, selectedComponentId, rowsSorted) {
        if (pagerState.isScrollInProgress) {
            val selectedIndex = selectedComponentId?.let { id ->
                rowsSorted.indexOfFirst { it.component.id == id }
            } ?: -1
            if (selectedComponentId == null || selectedIndex == pagerState.currentPage) {
                pagerScrollStartedByUser = true
                pagerStartPage = pagerState.currentPage
            }
        } else if (pagerScrollStartedByUser) {
            val endPage = pagerState.currentPage
            if (pagerStartPage != endPage) {
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
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) { page ->
            Box(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
                val row = rowsSorted.getOrNull(page) ?: return@HorizontalPager
                ComponentPagerCard(
                    spec = spec, unit = unit, row = row, physicalIndex = page,
                    outerPaddingHorizontal = componentCardPadding,
                    showComponentDebugLabels = showComponentDebugLabels,
                    onAddBody = onAddBody,
                    onUpdateBody = onUpdateBody, onUpdateTaper = onUpdateTaper,
                    onUpdateTaperKeyway = onUpdateTaperKeyway,
                    onUpdateThread = onUpdateThread, onUpdateLiner = onUpdateLiner,
                    onUpdateLinerLabel = onUpdateLinerLabel,
                    onUpdateLinerReference = onUpdateLinerReference,
                    bodyTitleById = bodyTitleById, taperTitleById = taperTitleById,
                    linerTitleById = linerTitleById, threadTitleById = threadTitleById,
                    onSetThreadExcludeFromOal = onSetThreadExcludeFromOal,
                    onSetThreadEndPosition = onSetThreadEndPosition,
                    onRemoveBody = onRemoveBody, onRemoveTaper = onRemoveTaper,
                    onRemoveThread = onRemoveThread, onRemoveLiner = onRemoveLiner,
                    collidingComponentIds = collidingComponentIds,
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
    onAddBody: (Float, Float, Float) -> Unit,
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateTaper: (Int, Float, Float, Float, Float, String) -> Unit,
    onUpdateTaperKeyway: (index: Int, widthMm: Float, depthMm: Float, lengthMm: Float, offsetFromSetMm: Float, spooned: Boolean) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,
    onUpdateLinerLabel: (Int, String?) -> Unit,
    onUpdateLinerReference: (Int, LinerAuthoredReference) -> Unit,
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
    collidingComponentIds: Set<String> = emptySet(),
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
        is ResolvedBody -> {
            if (component.source == ResolvedComponentSource.AUTO) {
                var startMm  by remember(component.id) { mutableStateOf(component.startMmPhysical) }
                var lengthMm by remember(component.id) { mutableStateOf(component.endMmPhysical - component.startMmPhysical) }
                var diaMm    by remember(component.id) { mutableStateOf(component.diaMm) }
                var promoted by remember(component.id) { mutableStateOf(false) }

                fun promoteIfNeeded() {
                    if (!promoted && startMm >= 0f && lengthMm > 0f && diaMm > 0f) {
                        promoted = true; onAddBody(startMm, lengthMm, diaMm)
                    }
                }

                ComponentCard(
                    title = "Body (auto)",
                    debugText = if (showComponentDebugLabels) "id=${component.id} • startMm=${f1(component.startMmPhysical)} • endMm=${f1(component.endMmPhysical)}" else null,
                    outerPaddingHorizontal = outerPaddingHorizontal,
                ) {
                    CommitNum("Start (${abbr(unit)})", disp(startMm, unit)) { s ->
                        toMmOrNull(s, unit)?.let { startMm = it; promoteIfNeeded() }
                    }
                    CommitNum("Length (${abbr(unit)})", disp(lengthMm, unit)) { s ->
                        toMmOrNull(s, unit)?.let { lengthMm = it; promoteIfNeeded() }
                    }
                    CommitNum("Ø (${abbr(unit)})", disp(diaMm, unit)) { s ->
                        toMmOrNull(s, unit)?.let { diaMm = it; promoteIfNeeded() }
                    }
                }
                return
            }

            val idx = explicitIndex ?: return
            val b   = spec.bodies.getOrNull(idx) ?: return
            ComponentCard(
                title = bodyTitleById[b.id] ?: "Body",
                debugText = if (showComponentDebugLabels) "id=${b.id} • startMm=${f1(b.startFromAftMm)} • endMm=${f1(b.startFromAftMm + b.lengthMm)}" else null,
                errorMessage = if (b.id in collidingComponentIds) "Overlaps another component" else null,
                warningMessage = bodyWarningMessage(b),
                componentId = b.id, componentKind = ComponentKind.BODY,
                outerPaddingHorizontal = outerPaddingHorizontal,
                onRemove = {
                    Log.d("ShaftUI", "Body delete clicked: id=${b.id}, rowIndex=$idx, physicalIndex=$physicalIndex")
                    onRemoveBody(b.id)
                }
            ) {
                CommitNum("Start (${abbr(unit)})", disp(b.startFromAftMm, unit), validator = startValidator(b.id, ComponentKind.BODY, b.lengthMm)) { s ->
                    toMmOrNull(s, unit)?.let { onUpdateBody(idx, it, b.lengthMm, b.diaMm) }
                }
                CommitNum("Length (${abbr(unit)})", disp(b.lengthMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let { onUpdateBody(idx, b.startFromAftMm, it, b.diaMm) }
                }
                CommitNum("Ø (${abbr(unit)})", disp(b.diaMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let { onUpdateBody(idx, b.startFromAftMm, b.lengthMm, it) }
                }
            }
        }

        // ── Taper ─────────────────────────────────────────────────────────────
        is ResolvedTaper -> {
            val idx    = explicitIndex ?: return
            val t      = spec.tapers.getOrNull(idx) ?: return
            val endMap = taperSetLetMapping(t, spec.overallLengthMm)
            ComponentCard(
                title = taperTitleById[t.id] ?: "Taper",
                debugText = if (showComponentDebugLabels) "id=${t.id} • startMm=${f1(t.startFromAftMm)} • endMm=${f1(t.startFromAftMm + t.lengthMm)}" else null,
                errorMessage = if (t.id in collidingComponentIds) "Overlaps another component" else null,
                warningMessage = taperWarningMessage(t),
                componentId = t.id, componentKind = ComponentKind.TAPER,
                outerPaddingHorizontal = outerPaddingHorizontal,
                onRemove = {
                    Log.d("ShaftUI", "Taper delete clicked: id=${t.id}, rowIndex=$idx, physicalIndex=$physicalIndex")
                    onRemoveTaper(t.id)
                }
            ) {
                CommitNum("Start (${abbr(unit)})", disp(t.startFromAftMm, unit), validator = startValidator(t.id, ComponentKind.TAPER, t.lengthMm)) { s ->
                    toMmOrNull(s, unit)?.let { onUpdateTaper(idx, it, t.lengthMm, t.startDiaMm, t.endDiaMm, t.taperRateText) }
                }
                CommitNum("Length (${abbr(unit)})", disp(t.lengthMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let { onUpdateTaper(idx, t.startFromAftMm, it, t.startDiaMm, t.endDiaMm, t.taperRateText) }
                }
                CommitNum("${endMap.leftCode} Ø (${abbr(unit)})", disp(t.startDiaMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let { onUpdateTaper(idx, t.startFromAftMm, t.lengthMm, it, t.endDiaMm, t.taperRateText) }
                }
                CommitNum("${endMap.rightCode} Ø (${abbr(unit)})", disp(t.endDiaMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let { onUpdateTaper(idx, t.startFromAftMm, t.lengthMm, t.startDiaMm, it, t.taperRateText) }
                }
                CommitNum("Rate (1:12, 3/4, or decimal)", t.taperRateText.ifBlank { "" }) { s ->
                    onUpdateTaper(idx, t.startFromAftMm, t.lengthMm, t.startDiaMm, t.endDiaMm, s.trim())
                }

                // Keyway fields
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CommitNum("KW W (${abbr(unit)})", dispKw(t.keywayWidthMm, unit), modifier = Modifier.weight(1f), fillMaxWidth = false) { s ->
                        val v = if (s.isBlank()) 0f else (toMmOrNull(s, unit) ?: return@CommitNum)
                        onUpdateTaperKeyway(idx, v, t.keywayDepthMm, t.keywayLengthMm, t.keywayOffsetFromSetMm, t.keywaySpooned)
                    }
                    Text("×", style = MaterialTheme.typography.titleMedium)
                    CommitNum("KW D (${abbr(unit)})", dispKw(t.keywayDepthMm, unit), modifier = Modifier.weight(1f), fillMaxWidth = false) { s ->
                        val v = if (s.isBlank()) 0f else (toMmOrNull(s, unit) ?: return@CommitNum)
                        onUpdateTaperKeyway(idx, t.keywayWidthMm, v, t.keywayLengthMm, t.keywayOffsetFromSetMm, t.keywaySpooned)
                    }
                }
                CommitNum("KW L (${abbr(unit)})", dispKw(t.keywayLengthMm, unit)) { s ->
                    val v = if (s.isBlank()) 0f else (toMmOrNull(s, unit) ?: return@CommitNum)
                    onUpdateTaperKeyway(idx, t.keywayWidthMm, t.keywayDepthMm, v, t.keywayOffsetFromSetMm, t.keywaySpooned)
                }
                CommitNum("KW Offset from SET (${abbr(unit)})", dispKw(t.keywayOffsetFromSetMm, unit)) { s ->
                    val v = if (s.isBlank()) 0f else (toMmOrNull(s, unit) ?: return@CommitNum)
                    onUpdateTaperKeyway(idx, t.keywayWidthMm, t.keywayDepthMm, t.keywayLengthMm, v, t.keywaySpooned)
                }

                val isFloating = t.keywayOffsetFromSetMm > 0f
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(
                            value = t.keywaySpooned, enabled = !isFloating,
                            role = androidx.compose.ui.semantics.Role.Switch,
                            onValueChange = { checked ->
                                onUpdateTaperKeyway(idx, t.keywayWidthMm, t.keywayDepthMm, t.keywayLengthMm, t.keywayOffsetFromSetMm, checked)
                            }
                        ).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isFloating) "Keyway spooned (N/A — floating)" else "Keyway spooned",
                        modifier = Modifier.weight(1f),
                        color = if (isFloating) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    androidx.compose.material3.Switch(
                        checked = t.keywaySpooned && !isFloating,
                        enabled = !isFloating,
                        onCheckedChange = null
                    )
                }
            }
        }

        // ── Thread ────────────────────────────────────────────────────────────
        is ResolvedThread -> {
            val idx        = explicitIndex ?: return
            val th         = spec.threads.getOrNull(idx) ?: return
            val tpiDisplay = pitchMmToTpi(th.pitchMm).fmtTrim(3)
            ComponentCard(
                title = threadTitleById[th.id] ?: "Thread",
                debugText = if (showComponentDebugLabels) "id=${th.id} • startMm=${f1(th.startFromAftMm)} • endMm=${f1(th.startFromAftMm + th.lengthMm)}" else null,
                errorMessage = if (th.excludeFromOAL) null else (
                    startOverlapErrorMm(spec, th.id, ComponentKind.THREAD, th.lengthMm, th.startFromAftMm)
                        ?: if (th.id in collidingComponentIds) "Overlaps another component" else null
                ),
                warningMessage = threadWarningMessage(th),
                componentId = th.id, componentKind = ComponentKind.THREAD,
                outerPaddingHorizontal = outerPaddingHorizontal,
                onRemove = {
                    Log.d("ShaftUI", "Thread delete clicked: id=${th.id}, rowIndex=$idx, physicalIndex=$physicalIndex")
                    onRemoveThread(th.id)
                }
            ) {
                val includeInOal = !th.excludeFromOAL
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(
                            value = includeInOal,
                            role = androidx.compose.ui.semantics.Role.Switch,
                            onValueChange = { checked -> onSetThreadExcludeFromOal(th.id, !checked) }
                        ).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Include thread in OAL", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = includeInOal, onCheckedChange = null)
                }
                if (!includeInOal) {
                    // AFT / FWD end selector — replaces the start input for excluded threads
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Thread end:", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val chipColors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.Black, selectedLabelColor = Color.White,
                            containerColor = Color.Transparent, labelColor = MaterialTheme.colorScheme.onSurface
                        )
                        FilterChip(selected = th.isAftEnd,
                            onClick = { onSetThreadEndPosition(th.id, true) },
                            label = { Text("AFT") }, colors = chipColors,
                            border = if (th.isAftEnd) BorderStroke(1.dp, Color.Black) else null)
                        FilterChip(selected = !th.isAftEnd,
                            onClick = { onSetThreadEndPosition(th.id, false) },
                            label = { Text("FWD") }, colors = chipColors,
                            border = if (!th.isAftEnd) BorderStroke(1.dp, Color.Black) else null)
                    }
                } else {
                    CommitNum("Start (${abbr(unit)})", disp(th.startFromAftMm, unit), validator = startValidator(th.id, ComponentKind.THREAD, th.lengthMm)) { s ->
                        toMmOrNull(s, unit)?.let { onUpdateThread(idx, it, th.lengthMm, th.majorDiaMm, th.pitchMm) }
                    }
                }
                CommitNum("Major Ø (${abbr(unit)})", disp(th.majorDiaMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let { onUpdateThread(idx, th.startFromAftMm, th.lengthMm, it, th.pitchMm) }
                }
                CommitNum("TPI", tpiDisplay) { s ->
                    parseFractionOrDecimal(s)?.takeIf { it > 0f }?.let { tpi ->
                        onUpdateThread(idx, th.startFromAftMm, th.lengthMm, th.majorDiaMm, tpiToPitchMm(tpi))
                    }
                }
                CommitNum("Length (${abbr(unit)})", disp(th.lengthMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let { onUpdateThread(idx, th.startFromAftMm, it, th.majorDiaMm, th.pitchMm) }
                }
            }
        }

        // ── Liner ─────────────────────────────────────────────────────────────
        is ResolvedLiner -> {
            val idx            = explicitIndex ?: return
            val ln             = spec.liners.getOrNull(idx) ?: return
            val computedTitle  = linerTitleById[ln.id] ?: "Liner"
            var editingTitle   by rememberSaveable(ln.id) { mutableStateOf(false) }
            val focusRequester = remember { FocusRequester() }
            var hasFocusedOnce by remember(ln.id) { mutableStateOf(false) }
            val isFwdRef       = ln.authoredReference == LinerAuthoredReference.FWD
            val authoredStartMm = if (isFwdRef) {
                spec.overallLengthMm - ln.startFromAftMm - ln.lengthMm
            } else {
                ln.startFromAftMm
            }

            ComponentCard(
                title = computedTitle,
                titleContent = {
                    if (!editingTitle) {
                        Text(
                            computedTitle,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth().clickable { editingTitle = true },
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        var text by remember(ln.id, ln.label) { mutableStateOf(ln.label.orEmpty()) }
                        LaunchedEffect(ln.id) { focusRequester.requestFocus() }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            placeholder = { Text(computedTitle) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                onUpdateLinerLabel(idx, text.trim().takeIf { it.isNotEmpty() })
                                editingTitle = false
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                                .onFocusChanged { f ->
                                    if (f.isFocused) hasFocusedOnce = true
                                    if (hasFocusedOnce && !f.isFocused) {
                                        onUpdateLinerLabel(idx, text.trim().takeIf { it.isNotEmpty() })
                                        editingTitle = false
                                    }
                                }
                        )
                    }
                },
                debugText = if (showComponentDebugLabels) "id=${ln.id} • startMm=${f1(ln.startFromAftMm)} • endMm=${f1(ln.startFromAftMm + ln.lengthMm)}" else null,
                errorMessage = startOverlapErrorMm(spec, ln.id, ComponentKind.LINER, ln.lengthMm, ln.startFromAftMm)
                    ?: if (ln.id in collidingComponentIds) "Overlaps another component" else null,
                warningMessage = linerWarningMessage(ln),
                componentId = ln.id, componentKind = ComponentKind.LINER,
                outerPaddingHorizontal = outerPaddingHorizontal,
                onRemove = {
                    Log.d("ShaftUI", "Liner delete clicked: id=${ln.id}, rowIndex=$idx, physicalIndex=$physicalIndex")
                    onRemoveLiner(ln.id)
                }
            ) {
                // AFT / FWD reference toggle
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
                    FilterChip(selected = !isFwdRef, onClick = { onUpdateLinerReference(idx, LinerAuthoredReference.AFT) },
                        label = { Text("AFT") }, colors = selectedColors,
                        border = if (!isFwdRef) BorderStroke(1.dp, Color.Black) else null)
                    FilterChip(selected = isFwdRef, onClick = { onUpdateLinerReference(idx, LinerAuthoredReference.FWD) },
                        label = { Text("FWD") }, colors = selectedColors,
                        border = if (isFwdRef) BorderStroke(1.dp, Color.Black) else null)
                }

                CommitNum(
                    label = "Start from ${if (isFwdRef) "FWD" else "AFT"} (${abbr(unit)})",
                    initialDisplay = disp(authoredStartMm, unit),
                    validator = { raw ->
                        val authoredMm = toMmOrNull(raw, unit) ?: return@CommitNum "Enter a number"
                        val physStart  = if (isFwdRef) spec.overallLengthMm - authoredMm - ln.lengthMm else authoredMm
                        startOverlapErrorMm(spec, ln.id, ComponentKind.LINER, ln.lengthMm, physStart)
                    }
                ) { s ->
                    val authoredMm = toMmOrNull(s, unit) ?: return@CommitNum
                    val physStart  = if (isFwdRef) spec.overallLengthMm - authoredMm - ln.lengthMm else authoredMm
                    onUpdateLiner(idx, physStart, ln.lengthMm, ln.odMm)
                }
                CommitNum("Length (${abbr(unit)})", disp(ln.lengthMm, unit)) { s ->
                    val newLen    = toMmOrNull(s, unit) ?: return@CommitNum
                    val physStart = if (isFwdRef) {
                        val authored = spec.overallLengthMm - ln.startFromAftMm - ln.lengthMm
                        spec.overallLengthMm - authored - newLen
                    } else {
                        ln.startFromAftMm
                    }
                    onUpdateLiner(idx, physStart, newLen, ln.odMm)
                }
                CommitNum("Outer Ø (${abbr(unit)})", disp(ln.odMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let { onUpdateLiner(idx, ln.startFromAftMm, ln.lengthMm, it) }
                }
            }
        }
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
                if (titleContent != null) titleContent()
                else Text(title, style = MaterialTheme.typography.titleMedium)

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
            }

            if (onRemove != null) {
                IconButton(
                    onClick = {
                        Log.d("ShaftUIButton", "Delete IconButton onClick fired for id=${componentId ?: "<unknown>"} (componentType=${componentKind?.name ?: "<unknown>"})")
                        onRemove()
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                        .pointerInput(componentId, componentKind) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.firstOrNull()?.pressed
                                    if (pressed != null) {
                                        Log.d("ShaftUIButton", "Pointer event on delete button: pressed=$pressed for id=${componentId ?: "<unknown>"} (componentType=${componentKind?.name ?: "<unknown>"})")
                                    }
                                }
                            }
                        }
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
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
private fun CommitNum(
    label: String,
    initialDisplay: String,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = true,
    showValidationErrors: Boolean = true,
    validator: ((String) -> String?)? = null,
    onCommit: (String) -> Unit
) {
    NumericInputField(
        label = label,
        initialText = initialDisplay,
        modifier = Modifier.let { if (fillMaxWidth) it.fillMaxWidth() else it }.then(modifier),
        allowNegative = false,
        allowFraction = true,
        showValidationErrors = showValidationErrors,
        keyboardType = KeyboardType.Decimal,
        validator = validator,
        parseValid = { parseFractionOrDecimal(it) != null },
        onCommit = onCommit
    )
}

/** Display keyway dimensions, preferring shop fractions in imperial. */
private fun dispKw(mm: Float, unit: UnitSystem): String = when (unit) {
    UnitSystem.INCHES -> if (kotlin.math.abs(mm) < 1e-6f) "0" else
        LengthFormat.formatInchesSmart(inches = mm.toDouble() / 25.4, opts = LengthFormat.InchFormatOptions(maxDenominator = 32))
    UnitSystem.MILLIMETERS -> disp(mm, unit)
}

private fun Float.fmtTrim(d: Int) = "%.${d}f".format(this).trimEnd('0').trimEnd('.')

internal fun pitchMmToTpi(pitchMm: Float): Float = if (pitchMm > 0f) 25.4f / pitchMm else 0f
