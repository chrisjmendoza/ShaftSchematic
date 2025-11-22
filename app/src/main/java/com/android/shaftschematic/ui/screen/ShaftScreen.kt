package com.android.shaftschematic.ui.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.config.AddDefaultsConfig
import com.android.shaftschematic.ui.config.defaultBodyLenMm
import com.android.shaftschematic.ui.config.defaultLinerLenMm
import com.android.shaftschematic.ui.config.defaultTaperLenMm
import com.android.shaftschematic.ui.config.defaultThreadLenMm
import com.android.shaftschematic.ui.config.defaultThreadMajorDiaMm
import com.android.shaftschematic.ui.config.defaultThreadPitchMm
import com.android.shaftschematic.ui.dialog.InlineAddChooserDialog
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.launch

/**
 * ShaftScreen — Editor surface
 *
 * Responsibilities
 * • Header row (unit selector + grid toggle; unit selector disables when locked)
 * • Preview drawing (white square; optional grid; fixed-height band)
 * • Free-to-End badge overlay (top-start of preview; red on oversize)
 * • Overall length input (ghost “0”; commits on blur/Done; auto when not manual)
 * • Project fields (commit-on-blur / IME Done)
 * • Component carousel (edit & remove) — honors cross-type ID order
 * • Add-component FAB floating above IME & nav bar
 *
 * Contract / Invariants
 * • Canonical model units are millimeters (mm) — convert only at UI edge.
 * • No geometry-based resorting. When provided, UI renders strictly by componentOrder (IDs).
 * • IME safety: scroll area uses imePadding; FAB uses ime ∪ navigationBars insets.
 * • No file I/O or routing here.
 */
@Composable
fun ShaftScreen(
    // Ordering (from VM via Route)
    componentOrder: List<ComponentKey> = emptyList(),
    onMoveComponentUp: (String) -> Unit = {},      // reserved for future Move UI
    onMoveComponentDown: (String) -> Unit = {},    // reserved for future Move UI

    // State
    spec: ShaftSpec,
    unit: UnitSystem,
    overallIsManual: Boolean,
    unitLocked: Boolean,
    customer: String,
    vessel: String,
    jobNumber: String,
    notes: String,
    showGrid: Boolean,

    // Setters
    onSetUnit: (UnitSystem) -> Unit,
    onToggleGrid: (Boolean) -> Unit,
    onSetCustomer: (String) -> Unit,
    onSetVessel: (String) -> Unit,
    onSetJobNumber: (String) -> Unit,
    onSetNotes: (String) -> Unit,
    onSetOverallLengthRaw: (String) -> Unit,
    onSetOverallLengthMm: (Float) -> Unit,
    onSetOverallIsManual: (Boolean) -> Unit,

    // Adds (all mm)
    onAddBody: (Float, Float, Float) -> Unit,
    onAddTaper: (Float, Float, Float, Float) -> Unit,
    onAddThread: (Float, Float, Float, Float) -> Unit, // order: start, length, pitch, majorDia
    onAddLiner: (Float, Float, Float) -> Unit,

    // Updates (all mm)
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateTaper: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,

    // Removes by stable id
    onRemoveBody: (String) -> Unit,
    onRemoveTaper: (String) -> Unit,
    onRemoveThread: (String) -> Unit,
    onRemoveLiner: (String) -> Unit,

    // Other
    renderShaft: @Composable (ShaftSpec, UnitSystem) -> Unit,
    snackbarHostState: SnackbarHostState,
    onClickSave: () -> Unit,
    onExportPdf: () -> Unit,
    onOpenSettings: () -> Unit,

    /**
     * Accessibility: [fabEnabled]
     * When true, shows a floating “Add component” button as an alternative to the
     * carousel’s add cards. Off by default; toggle from Settings for users who prefer
     * a large, persistent affordance that sits above the IME.
     */
    fabEnabled: Boolean = false, // ← NEW: default off
) {
    // UI options for preview highlight (renderer should consume these—see comment in PreviewCard)
    var highlightEnabled by rememberSaveable { mutableStateOf(true) }
    var highlightId by rememberSaveable { mutableStateOf<String?>(null) }

    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }

    var chooserOpen by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()

    // Auto-sync overall when not manual
    LaunchedEffect(overallIsManual, spec.bodies, spec.tapers, spec.threads, spec.liners) {
        if (!overallIsManual) {
            val end = lastOccupiedEndMm(spec)
            if (end != spec.overallLengthMm) onSetOverallLengthMm(end)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        floatingActionButton = {
            if (fabEnabled) {
                AddComponentFab(
                    onClick = { chooserOpen = true }
                )
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp)
        ) {
            // Preview
            PreviewCard(
                showGrid = showGrid,
                spec = spec,
                unit = unit,
                highlightEnabled = highlightEnabled,
                highlightId = focusedId,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 200.dp)
                    .aspectRatio(3.0f)
            )
            // NOTE: Your renderer should read highlightEnabled + highlightId from VM/global state.
            // If you want me to thread them directly into renderShaft, I can add a tiny bridge later.

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Scrollable editor content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    )
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Overall Length (auto vs manual — always show a value)
                var hasLenFocus by remember { mutableStateOf(false) }

                val displayMm =
                    if (overallIsManual) spec.overallLengthMm else lastOccupiedEndMm(spec)
                var lengthText by remember(unit, displayMm, overallIsManual) {
                    mutableStateOf(formatDisplay(displayMm, unit))
                }

                val freeSignedMm = spec.overallLengthMm - lastOccupiedEndMm(spec)
                val isOversized = freeSignedMm < 0f

                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { input ->
                        lengthText = input
                        if (overallIsManual) {
                            toMmOrNull(input, unit)?.let { mm ->
                                onSetOverallLengthMm(mm)
                            }
                        }
                    },
                    label = { Text("Overall Length (${abbr(unit)})") },
                    singleLine = true,
                    enabled = overallIsManual, // auto mode is read-only
                    isError = isOversized,
                    supportingText = {
                        val mode = if (overallIsManual) "Manual" else "Auto"
                        val hint = if (isOversized)
                            "Oversized by ${formatDisplay(-freeSignedMm, unit)} ${abbr(unit)}"
                        else "$mode • ${formatDisplay(displayMm, unit)} ${abbr(unit)}"
                        Text(hint)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        val t = lengthText.trim()
                        if (t.isEmpty()) {
                            onSetOverallIsManual(false)
                            onSetOverallLengthMm(lastOccupiedEndMm(spec))
                        } else {
                            toMmOrNull(t, unit)?.let { mm ->
                                onSetOverallLengthMm(mm)
                                onSetOverallIsManual(true)
                                onSetOverallLengthRaw(t) // keep user’s display text
                            }
                        }
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { f ->
                            hasLenFocus = f.isFocused
                            if (!f.isFocused) {
                                val t = lengthText.trim()
                                if (t.isEmpty()) {
                                    onSetOverallIsManual(false)
                                    onSetOverallLengthMm(lastOccupiedEndMm(spec))
                                } else {
                                    toMmOrNull(t, unit)?.let { mm ->
                                        onSetOverallLengthMm(mm)
                                        onSetOverallIsManual(true)
                                        onSetOverallLengthRaw(t)
                                    }
                                }
                            }
                        }
                )

                // Project info (optional)
                ExpandableSection("Project Information (optional)", initiallyExpanded = false) {
                    CommitTextField("Job Number", jobNumber, onSetJobNumber, Modifier.fillMaxWidth())
                    CommitTextField("Customer", customer, onSetCustomer, Modifier.fillMaxWidth())
                    CommitTextField("Vessel", vessel, onSetVessel, Modifier.fillMaxWidth())
                    CommitTextField(
                        label = "Notes",
                        initial = notes,
                        onCommit = onSetNotes,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minHeight = 88.dp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Highlight selection in preview", Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = highlightEnabled,
                        onCheckedChange = { highlightEnabled = it }
                    )
                }

                Text(
                    "Components",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )

                ComponentCarouselPager(
                    spec = spec,
                    unit = unit,
                    componentOrder = componentOrder,
                    onUpdateBody = onUpdateBody,
                    onUpdateTaper = onUpdateTaper,
                    onUpdateThread = onUpdateThread,
                    onUpdateLiner = onUpdateLiner,
                    onRemoveBody = onRemoveBody,
                    onRemoveTaper = onRemoveTaper,
                    onRemoveThread = onRemoveThread,
                    onRemoveLiner = onRemoveLiner,
                    onTapAdd = { chooserOpen = true },
                    onAddAtAft = {
                        // Use your defaults but anchored AFT
                        val d = computeAddDefaults(spec).copy(startMm = 0f) // AFT = 0
                        chooserOpen = true
                        // In the chooser handlers, call onAdd… with d.startMm; after add, jump to page 1:
                        // scope.launch { pagerState.scrollToPage(1) }
                    },
                    onAddAtFwd = {
                        val d = computeAddDefaults(spec) // your current behavior likely uses last end
                        chooserOpen = true
                        // After adding at FWD, jump to the newest page:
                        // scope.launch { pagerState.scrollToPage(rowsSorted.size + 1) }
                    },
                    // Focus reporting
                    onFocusedChanged = { idOrNull ->
                        focusedId = idOrNull
                    }
                )

                if (chooserOpen) {
                    val d = computeAddDefaults(spec)

                    // Centralized defaults (mm)
                    val bodyLenMm     = defaultBodyLenMm(unit)
                    val linerLenMm    = defaultLinerLenMm(unit)
                    val taperLenMm    = defaultTaperLenMm(unit)
                    val threadLenMm   = defaultThreadLenMm(unit)
                    val threadMajMm   = defaultThreadMajorDiaMm(unit)
                    val threadPitchMm = defaultThreadPitchMm()
                    val taperRatio    = AddDefaultsConfig.TAPER_RATIO

                    InlineAddChooserDialog(
                        onDismiss = { chooserOpen = false },
                        onAddBody = { chooserOpen = false; onAddBody(d.startMm, bodyLenMm, d.lastDiaMm) },
                        onAddLiner = { chooserOpen = false; onAddLiner(d.startMm, linerLenMm, d.lastDiaMm) },
                        onAddThread = {
                            chooserOpen = false
                            // IMPORTANT: order = start, length, pitch, majorDia
                            onAddThread(d.startMm, threadLenMm, threadPitchMm, threadMajMm)
                        },
                        onAddTaper = {
                            chooserOpen = false
                            val len = taperLenMm
                            val setDiaMm = d.lastDiaMm
                            val letDiaMm = setDiaMm + (len * taperRatio) // ~1:12
                            onAddTaper(d.startMm, len, setDiaMm, letDiaMm)
                        }
                    )
                }
            }
        }
    }
}

/* ───────────────── Preview ───────────────── */

@Composable
private fun PreviewCard(
    showGrid: Boolean,
    spec: ShaftSpec,
    unit: UnitSystem,
    // NEW: explicit preview controls
    highlightEnabled: Boolean,
    highlightId: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Transparent)) {
            // Direct render: pass grid + highlight to the renderer
            ShaftDrawing(
                spec = spec,
                unit = unit,
                showGrid = showGrid,
                highlightEnabled = highlightEnabled && (highlightId != null),
                highlightId = highlightId
            )

            FreeToEndBadge(
                spec = spec,
                unit = unit,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            )
        }
    }
}


/* ───────────────── Carousel implementation ───────────────── */

private data class RowRef(
    val kind: ComponentKind,
    val index: Int,
    val start: Float,
    val id: String
)

/**
 * ComponentCarouselPager
 *
 * Purpose: Horizontal pager with sentinel add-cards at both ends and boxed arrow nav.
 * Contract:
 *  • Honors componentOrder when provided; else stable assembly order by start, tie-broken by type.
 *  • Calls onFocusedChanged(idOrNull) whenever the current page changes (add-pages send null).
 *  • Arrow buttons animate elevation/alpha on press/hover; pager gestures remain intact.
 */
@Composable
private fun ComponentCarouselPager(
    spec: ShaftSpec,
    unit: UnitSystem,
    componentOrder: List<ComponentKey>,
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateTaper: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,
    onRemoveBody: (String) -> Unit,
    onRemoveTaper: (String) -> Unit,
    onRemoveThread: (String) -> Unit,
    onRemoveLiner: (String) -> Unit,
    onTapAdd: () -> Unit,
    onAddAtAft: () -> Unit,
    onAddAtFwd: () -> Unit,
    onFocusedChanged: (String?) -> Unit
) {
    val rows = remember(spec, componentOrder) { buildOrderedRows(spec, componentOrder) }

    // Build rows and force left→right (AFT→FWD) by actual start position in mm.
    val rowsSorted = remember(spec, componentOrder) {
        val base = buildOrderedRows(spec, componentOrder)
        fun startMm(row: RowRef): Float = when (row.kind) {
            ComponentKind.BODY   -> spec.bodies.getOrNull(row.index)?.startFromAftMm ?: Float.MAX_VALUE
            ComponentKind.TAPER  -> spec.tapers.getOrNull(row.index)?.startFromAftMm ?: Float.MAX_VALUE
            ComponentKind.THREAD -> spec.threads.getOrNull(row.index)?.startFromAftMm ?: Float.MAX_VALUE
            ComponentKind.LINER  -> spec.liners.getOrNull(row.index)?.startFromAftMm ?: Float.MAX_VALUE
        }
        base.sortedBy { startMm(it) }
    }

// Pager pages: [Add @ AFT] + rowsSorted + [Add @ FWD]
    val pageCount = rowsSorted.size + 2
    val pagerState = rememberPagerState(
        initialPage = if (rowsSorted.isEmpty()) 0 else 1, // land on leftmost component when present
        pageCount = { pageCount }
    )

    val scope = rememberCoroutineScope()

    // Auto-jump to the newest card after insertion
    LaunchedEffect(rowsSorted.size) {
        if (rowsSorted.isNotEmpty()) {
            val newPage = rowsSorted.size // page 1 = rowsSorted[0], so newest = rowsSorted.size
            pagerState.scrollToPage(newPage)
        }
    }

    // Report focused id
    LaunchedEffect(pagerState.currentPage, rowsSorted) {
        val p = pagerState.currentPage
        val idOrNull = when (p) {
            0, pageCount - 1 -> null
            else -> rowsSorted[p - 1].id
        }
        onFocusedChanged(idOrNull)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(CAROUSEL_HEIGHT) // your existing constant (e.g., 360.dp)
    ) {
        // Pager fills the box
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .matchParentSize()
        ) { page ->
            when (page) {
                0 -> AddComponentCard(label = "Add at AFT", onAdd = onAddAtAft)   // see section 2
                pageCount - 1 -> AddComponentCard(label = "Add at FWD", onAdd = onAddAtFwd)
                else -> {
                    val row = rowsSorted[page - 1]
                    ComponentPagerCard(
                        spec = spec, unit = unit, row = row,
                        onUpdateBody = onUpdateBody, onUpdateTaper = onUpdateTaper,
                        onUpdateThread = onUpdateThread, onUpdateLiner = onUpdateLiner,
                        onRemoveBody = onRemoveBody, onRemoveTaper = onRemoveTaper,
                        onRemoveThread = onRemoveThread, onRemoveLiner = onRemoveLiner
                    )
                }
            }
        }

        // Left paddle
        EdgeNavButton(
            left = true,
            onClick = {
                scope.launch {
                    pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                }
            },
            modifier = Modifier
                .align(Alignment.CenterStart) // vertically centered next to the card
                .fillMaxHeight()              // same height as the card
                .width(44.dp)                 // slim nub
                .padding(start = 4.dp)        // tiny gutter
        )

        // Right paddle
        EdgeNavButton(
            left = false,
            onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(
                        (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(44.dp)
                .padding(end = 4.dp)
        )
    }
}

/* ───────────────── Arrow Button (reusable) ───────────────── */

/**
 * Pager direction is controlled by `carouselDirectionLtr`.
 * When true, the first component page (index 1) corresponds to the leftmost shaft segment (AFT).
 * When false, the last component page (index pageCount-2) is the leftmost segment.
 */

@Composable
private fun ArrowNavButton(
    left: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Lightweight hover for mouse/trackpad; harmless on touch
    var hovered by remember { mutableStateOf(false) }

    val targetElevation = when {
        pressed -> 6.dp
        hovered -> 4.dp
        else -> 2.dp
    }
    val elevation by animateDpAsState(targetElevation, label = "arrowElevation")

    val contentAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.85f else if (hovered) 0.92f else 1f,
        label = "arrowAlpha"
    )

    Surface(
        tonalElevation = elevation,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        hovered = event.type == PointerEventType.Move || event.type == PointerEventType.Enter
                    }
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                Text(
                    text = if (left) "◀" else "▶",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.graphicsLayer { alpha = contentAlpha }
                )
            }
        }
    }
}

@Composable
private fun AddComponentCard(
    label: String,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(18.dp)
                ) { Text("Add component") }
            }
        }
    }
}


@Composable
private fun ArrowHint(left: Boolean) {
    Text(
        text = if (left) "◀" else "▶",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ComponentPagerCard(
    spec: ShaftSpec,
    unit: UnitSystem,
    row: RowRef,
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateTaper: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,
    onRemoveBody: (String) -> Unit,
    onRemoveTaper: (String) -> Unit,
    onRemoveThread: (String) -> Unit,
    onRemoveLiner: (String) -> Unit
) {
    when (row.kind) {
        ComponentKind.BODY -> {
            val b = spec.bodies[row.index]
            ComponentCard("Body #${row.index + 1}", onRemove = { onRemoveBody(b.id) }) {
                CommitNum("Start (${abbr(unit)})", disp(b.startFromAftMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateBody(row.index, it, b.lengthMm, b.diaMm)
                    }
                }
                CommitNum("Length (${abbr(unit)})", disp(b.lengthMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateBody(row.index, b.startFromAftMm, it, b.diaMm)
                    }
                }
                CommitNum("Ø (${abbr(unit)})", disp(b.diaMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateBody(row.index, b.startFromAftMm, b.lengthMm, it)
                    }
                }
            }
        }

        ComponentKind.TAPER -> {
            val t = spec.tapers[row.index]
            ComponentCard("Taper #${row.index + 1}", onRemove = { onRemoveTaper(t.id) }) {
                CommitNum("Start (${abbr(unit)})", disp(t.startFromAftMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateTaper(row.index, it, t.lengthMm, t.startDiaMm, t.endDiaMm)
                    }
                }
                CommitNum("Length (${abbr(unit)})", disp(t.lengthMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateTaper(row.index, t.startFromAftMm, it, t.startDiaMm, t.endDiaMm)
                    }
                }
                CommitNum("S.E.T. Ø (${abbr(unit)})", disp(t.startDiaMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateTaper(row.index, t.startFromAftMm, t.lengthMm, it, t.endDiaMm)
                    }
                }
                CommitNum("L.E.T. Ø (${abbr(unit)})", disp(t.endDiaMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateTaper(row.index, t.startFromAftMm, t.lengthMm, t.startDiaMm, it)
                    }
                }
            }
        }

        ComponentKind.THREAD -> {
            val th = spec.threads[row.index]
            val tpiDisplay = pitchMmToTpi(th.pitchMm).fmtTrim(3)
            ComponentCard("Thread #${row.index + 1}", onRemove = { onRemoveThread(th.id) }) {
                CommitNum("Start (${abbr(unit)})", disp(th.startFromAftMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateThread(row.index, it, th.lengthMm, th.majorDiaMm, th.pitchMm)
                    }
                }
                CommitNum("Length (${abbr(unit)})", disp(th.lengthMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateThread(row.index, th.startFromAftMm, it, th.majorDiaMm, th.pitchMm)
                    }
                }
                CommitNum("Major Ø (${abbr(unit)})", disp(th.majorDiaMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateThread(row.index, th.startFromAftMm, th.lengthMm, it, th.pitchMm)
                    }
                }
                CommitNum("TPI", tpiDisplay) { s ->
                    parseFractionOrDecimal(s)?.takeIf { it > 0f }?.let { tpi ->
                        onUpdateThread(
                            row.index,
                            th.startFromAftMm,
                            th.lengthMm,
                            th.majorDiaMm,
                            tpiToPitchMm(tpi)
                        )
                    }
                }
            }
        }

        ComponentKind.LINER -> {
            val ln = spec.liners[row.index]
            ComponentCard("Liner #${row.index + 1}", onRemove = { onRemoveLiner(ln.id) }) {
                CommitNum("Start (${abbr(unit)})", disp(ln.startFromAftMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateLiner(row.index, it, ln.lengthMm, ln.odMm)
                    }
                }
                CommitNum("Length (${abbr(unit)})", disp(ln.lengthMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateLiner(row.index, ln.startFromAftMm, it, ln.odMm)
                    }
                }
                CommitNum("Outer Ø (${abbr(unit)})", disp(ln.odMm, unit)) { s ->
                    toMmOrNull(s, unit)?.let {
                        onUpdateLiner(row.index, ln.startFromAftMm, ln.lengthMm, it)
                    }
                }
            }
        }
    }
}


/* ───────────────── Shared builders ───────────────── */
/* ───────────────── Ordering tiers (for stable tie-break) ───────────────── */

private const val TIE_BODY   = 0
private const val TIE_TAPER  = 1_000_000
private const val TIE_THREAD = 2_000_000
private const val TIE_LINER  = 3_000_000

// Assembly order: honor componentOrder if provided; else merge by physical start.
private fun buildOrderedRows(
    spec: ShaftSpec,
    componentOrder: List<ComponentKey>
): List<RowRef> {
    if (componentOrder.isNotEmpty()) {
        // Respect VM-provided assembly order verbatim.
        val bodyIdx   = spec.bodies.withIndex().associate { it.value.id to it.index }
        val taperIdx  = spec.tapers.withIndex().associate { it.value.id to it.index }
        val threadIdx = spec.threads.withIndex().associate { it.value.id to it.index }
        val linerIdx  = spec.liners.withIndex().associate { it.value.id to it.index }


        return buildList {
            componentOrder.forEach { key ->
                when (key.kind) {
                    ComponentKind.BODY   -> bodyIdx[key.id]?.let   { i -> add(RowRef(ComponentKind.BODY,   i, spec.bodies[i].startFromAftMm,  key.id)) }
                    ComponentKind.TAPER  -> taperIdx[key.id]?.let  { i -> add(RowRef(ComponentKind.TAPER,  i, spec.tapers[i].startFromAftMm,  key.id)) }
                    ComponentKind.THREAD -> threadIdx[key.id]?.let { i -> add(RowRef(ComponentKind.THREAD, i, spec.threads[i].startFromAftMm, key.id)) }
                    ComponentKind.LINER  -> linerIdx[key.id]?.let  { i -> add(RowRef(ComponentKind.LINER,  i, spec.liners[i].startFromAftMm,  key.id)) }
                }
            }
        }
    }


    // Fallback: true assembly order by start position, stable within equal starts.
    data class AnyRow(val kind: ComponentKind, val index: Int, val start: Float, val id: String, val tie: Int)

    val merged = buildList {
        spec.bodies.forEachIndexed  { i, b  -> add(AnyRow(ComponentKind.BODY,   i, b.startFromAftMm,  b.id, TIE_BODY   + i)) }
        spec.tapers.forEachIndexed  { i, t  -> add(AnyRow(ComponentKind.TAPER,  i, t.startFromAftMm,  t.id, TIE_TAPER  + i)) }
        spec.threads.forEachIndexed { i, th -> add(AnyRow(ComponentKind.THREAD, i, th.startFromAftMm, th.id, TIE_THREAD + i)) }
        spec.liners.forEachIndexed  { i, ln -> add(AnyRow(ComponentKind.LINER,  i, ln.startFromAftMm,  ln.id, TIE_LINER  + i)) }
    }
        .sortedWith(compareBy<AnyRow>({ it.start }, { it.tie }))

    return merged.map { RowRef(it.kind, it.index, it.start, it.id) }
}

/* ───────────────── Cards & fields ───────────────── */

@Composable
private fun ComponentCard(
    title: String,
    onRemove: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp), // consistent outer spacing
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    // Medium weight reads cleaner in cards than Small + default weight
                )
                content()
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}


@Composable
private fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableWithoutRipple { expanded = !expanded }
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun CommitTextField(
    label: String,
    initial: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    minHeight: Dp = Dp.Unspecified
) {
    var text by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = modifier
            .let { if (minHeight != Dp.Unspecified) it.heightIn(min = minHeight) else it }
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}

@Composable
private fun CommitNum(
    label: String,
    initialDisplay: String,
    onCommit: (String) -> Unit
) {
    var text by remember(initialDisplay) { mutableStateOf(initialDisplay) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
    )
}

/* ───────────────── Helpers: units, parsing, badge math, defaults ───────────────── */

private val CAROUSEL_HEIGHT = 360.dp // visually nice; tweak if you like
private fun abbr(unit: UnitSystem) = if (unit == UnitSystem.MILLIMETERS) "mm" else "in"

private fun formatDisplay(valueMm: Float, unit: UnitSystem, d: Int = 3): String {
    // Decimals:
    // • Millimeters: use the requested precision (default 3).
    // • Inches: force at least 4 decimals so common fractions (1/16, 1/32) are exact
    //   when entered as decimals (0.4375, 0.3125, etc.).
    val decimals = when (unit) {
        UnitSystem.INCHES -> maxOf(d, 4)
        UnitSystem.MILLIMETERS -> d
    }

    val v = if (unit == UnitSystem.MILLIMETERS) {
        valueMm
    } else {
        valueMm / 25.4f
    }

    return "%.${decimals}f"
        .format(v)
        .trimEnd('0')
        .trimEnd('.')
        .ifEmpty { "0" }
}

/** Convenience wrapper for "mm → display" in the component cards. */
private fun disp(mm: Float, unit: UnitSystem, d: Int = 3): String =
    formatDisplay(mm, unit, d)

private fun toMmOrNull(text: String, unit: UnitSystem): Float? {
    val t = text.trim(); if (t.isEmpty()) return null
    val num = parseFractionOrDecimal(t) ?: return null
    return if (unit == UnitSystem.MILLIMETERS) num else num * 25.4f
}

private fun Float.fmtTrim(d: Int) = "%.${d}f".format(this).trimEnd('0').trimEnd('.')

/** Accepts "12", "3/4", "1.5", or "1:12". */
private fun parseFractionOrDecimal(input: String): Float? {
    val t = input.trim(); if (t.isEmpty()) return null
    val colon = t.indexOf(':')
    if (colon >= 0) {
        val a = t.substring(0, colon).trim().toFloatOrNull() ?: return null
        val b = t.substring(colon + 1).trim().toFloatOrNull() ?: return null
        if (b == 0f) return null
        return a / b
    }
    val slash = t.indexOf('/')
    if (slash >= 0) {
        val a = t.substring(0, slash).trim().toFloatOrNull() ?: return null
        val b = t.substring(slash + 1).trim().toFloatOrNull() ?: return null
        if (b == 0f) return null
        return a / b
    }
    return t.toFloatOrNull()
}

@Composable
private fun EdgeNavButton(
    left: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Soft vertical gradient to suggest “edge”
    val scrim = androidx.compose.ui.graphics.Brush.verticalGradient(
        0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.0f),
        0.5f to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.0f)
    )
    Box(
        modifier
            .background(scrim, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (left) "◀" else "▶",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddComponentFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            // keep excellent keyboard & nav safety when enabled
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Add component")
    }
}


/** Latest occupied end position along the shaft (mm) from all components. */
private fun lastOccupiedEndMm(spec: ShaftSpec): Float {
    var end = 0f
    spec.bodies.forEach  { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.tapers.forEach  { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.threads.forEach { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach  { if (it.lengthMm  > 0f) end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    return end
}

/* ───────────────── Free-to-End badge ───────────────── */

@Composable
private fun FreeToEndBadge(
    spec: ShaftSpec,
    unit: UnitSystem,
    modifier: Modifier = Modifier
) {
    val endMm = lastOccupiedEndMm(spec)
    val freeSignedMm = spec.overallLengthMm - endMm
    val isOversized = freeSignedMm < 0f

    val bg = if (isOversized) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val fg = if (isOversized) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSurface

    Surface(
        tonalElevation = if (isOversized) 3.dp else 2.dp,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = "Free to end: ${formatDisplay(freeSignedMm, unit)} ${abbr(unit)}",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun tpiToPitchMm(tpi: Float): Float = if (tpi > 0f) 25.4f / tpi else 0f
@Suppress("unused")
private fun pitchMmToTpi(pitchMm: Float): Float = if (pitchMm > 0f) 25.4f / pitchMm else 0f

/** Defaults for new components (mm). */
private data class AddDefaults(val startMm: Float, val lastDiaMm: Float)
private fun computeAddDefaults(spec: ShaftSpec): AddDefaults {
    var end = 0f
    spec.bodies.forEach  { end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.tapers.forEach  { end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.threads.forEach { end = maxOf(end, it.startFromAftMm + it.lengthMm) }
    spec.liners.forEach  { end = maxOf(end, it.startFromAftMm + it.lengthMm) }

    var dia = 50f
    spec.bodies.firstOrNull  { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.diaMm }
    spec.liners.firstOrNull  { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.odMm }
    spec.threads.firstOrNull { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.majorDiaMm }
    spec.tapers.firstOrNull  { it.startFromAftMm + it.lengthMm == end }?.let { dia = it.endDiaMm }
    if (dia == 50f && spec.bodies.isNotEmpty()) dia = spec.bodies.first().diaMm

    return AddDefaults(startMm = end, lastDiaMm = dia)
}

/* ───────────────── Click helper ───────────────── */

// Compose-safe no-ripple click helper
private fun Modifier.clickableWithoutRipple(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    clickable(
        enabled = enabled,
        indication = null,
        interactionSource = interaction,
        onClick = onClick
    )
}
