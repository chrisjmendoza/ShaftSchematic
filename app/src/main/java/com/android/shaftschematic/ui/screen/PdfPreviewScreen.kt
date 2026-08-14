// file: app/src/main/java/com/android/shaftschematic/ui/screen/PdfPreviewScreen.kt
package com.android.shaftschematic.ui.screen

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import com.android.shaftschematic.settings.PdfTieringMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.defaultVisualScale
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.maxOuterDiaMm
import com.android.shaftschematic.pdf.PdfExportOptions
import com.android.shaftschematic.pdf.composeShaftPdf
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.*
import com.android.shaftschematic.util.DocumentNaming
import com.android.shaftschematic.util.InkBand
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.inkBand
import com.android.shaftschematic.util.printShaftPdfPage
import com.android.shaftschematic.util.renderPdfPageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.pm.PackageManager
import kotlin.math.roundToInt

/**
 * PdfPreviewScreen
 *
 * Purpose
 * Shows a full-resolution preview of the PDF that will be exported, rasterized through the
 * shared `util/PdfRaster.renderPdfPageBitmap` (the real composed page, never a separate
 * draw path). Supports pinch-to-zoom (and double-tap to reset) so users can inspect
 * dimension labels before committing to an export.
 *
 * Contract
 * - Rendering runs on Dispatchers.IO; a loading indicator is shown meanwhile.
 * - While the options sheet is open the page redraws as a fit-width strip pinned under the
 *   app bar, and the sheet is capped to what is left below it — the sliders tune the page
 *   live, so the page has to stay in sight. Opening the sheet resets zoom/pan.
 * - If rendering fails, a plain error message is shown (no crash).
 * - The "Export PDF" action in the top bar calls [onExport] to proceed to the SAF picker.
 * - No model state is mutated here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    vm: ShaftViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    val ctx = LocalContext.current

    // Unlock rotation for this screen only; restore portrait when leaving.
    val activity = ctx as? Activity
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val spec by vm.spec.collectAsState()
    val unit by vm.unit.collectAsState()
    // The per-job "Shaft height" multiplier lives in RunoutConfig — ONE value behind the
    // schematic, the runout sheet, and the consolidated output.
    val runoutConfig by vm.runoutConfig.collectAsState()
    val pdfExportMode by vm.pdfExportMode.collectAsState()
    val pdfBlankDraft by vm.pdfBlankDraft.collectAsState()
    val pdfBlankDiaCallouts by vm.pdfBlankDiaCallouts.collectAsState()
    val lineThicknessScale by vm.lineThicknessScale.collectAsState()
    val customer by vm.customer.collectAsState()
    val vessel by vm.vessel.collectAsState()
    val jobNumber by vm.jobNumber.collectAsState()
    val shaftPosition by vm.shaftPosition.collectAsState()
    val resolvedComponents by vm.resolvedComponents.collectAsState()
    val pdfShowComponentTitles by vm.pdfShowComponentTitles.collectAsState()
    val pdfTieringMode by vm.pdfTieringMode.collectAsState()
    val pdfShadedBodies by vm.pdfShadedBodies.collectAsState()
    val pdfShadedTapers by vm.pdfShadedTapers.collectAsState()
    val pdfShadedLiners by vm.pdfShadedLiners.collectAsState()
    val pdfSBreakThresholdFrac by vm.pdfSBreakThresholdFrac.collectAsState()
    // Arrowhead size: a chip tap commits straight to PdfPrefs, so the render loop needs it as
    // an input key or the page would keep the old heads.
    val pdfArrowSizePt by vm.pdfArrowSizePt.collectAsState()
    // Sizing-curve anchors: the composer sizes the drawn shaft off them, so a Settings
    // change to "Default drawing size" has to re-render an open preview. Collected here,
    // not only inside the Tune sheet, or the page would keep its old height until some
    // other input moved.
    val curveLoHeightIn by vm.pdfCurveLoHeightIn.collectAsState()
    val curveHiHeightIn by vm.pdfCurveHiHeightIn.collectAsState()

    val project = remember(customer, vessel, shaftPosition, jobNumber) {
        ProjectInfo(customer = customer, vessel = vessel, side = shaftPosition, jobNumber = jobNumber)
    }
    val options = remember(pdfExportMode, pdfBlankDraft, pdfBlankDiaCallouts) {
        PdfExportOptions(
            mode = pdfExportMode,
            blankValues = pdfBlankDraft,
            blankDiaCallouts = pdfBlankDiaCallouts,
        )
    }

    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    // Where the composed page actually carries ink — the tuning strip crops to it so blank
    // paper never takes room from the drawing. Measured on sharp passes only (see below).
    var inkBand by remember { mutableStateOf<InkBand?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Live slider drags — visual-only overrides, never a write. See [PreviewTuning].
    val tuning = rememberPreviewTuning()

    // The render loop. One RenderInputs value carries EVERYTHING the composed page reads
    // (an omission here is a stale-preview bug); `snapshotFlow { … }.conflate()` renders
    // the newest inputs and drops the intermediate frames a slider drag produces while a
    // render is in flight. When a drag ends the overrides go null, the inputs change once
    // more, and that final pass rasterizes at full resolution.
    LaunchedEffect(Unit) {
        var previousWasDraft = false
        snapshotFlow {
            val config = tunedRunoutConfig(runoutConfig, tuning.heightScale, tuning.linerCompression)
            SchematicRenderInputs(
                spec = spec,
                unit = unit,
                project = ProjectInfo(
                    customer = customer, vessel = vessel,
                    side = shaftPosition, jobNumber = jobNumber,
                ),
                options = PdfExportOptions(
                    mode = pdfExportMode,
                    blankValues = pdfBlankDraft,
                    blankDiaCallouts = pdfBlankDiaCallouts,
                ),
                resolved = resolvedComponents,
                lineThicknessScale = tuning.lineThickness ?: lineThicknessScale,
                showComponentTitles = pdfShowComponentTitles,
                tieringMode = pdfTieringMode,
                shadedBodies = pdfShadedBodies,
                shadedTapers = pdfShadedTapers,
                shadedLiners = pdfShadedLiners,
                sBreakThresholdFrac = tuning.sBreakFrac ?: pdfSBreakThresholdFrac,
                arrowSizePt = pdfArrowSizePt,
                curveLoHeightIn = curveLoHeightIn,
                curveHiHeightIn = curveHiHeightIn,
                heightScale = config.heightScale,
                linerMinFracOfTrue = config.linerMinFracOfTrue,
                draft = tuning.active,
            )
        }.conflate().collect { inputs ->
            // A drag frame — and the sharp pass right after one — keeps the current page on
            // screen: swapping in the spinner between frames would strobe the preview.
            val quiet = inputs.draft || previousWasDraft
            previousWasDraft = inputs.draft
            if (!quiet) isLoading = true
            errorMessage = null
            // Snapshot on the main thread before switching to IO.
            val pdfPrefsSnapshot = tunedPdfPrefs(vm.currentPdfPrefs, inputs.sBreakThresholdFrac)
            val versionSnapshot = appVersionName(ctx)
            // The ink band is measured on the raw raster, and only on a sharp (non-draft)
            // pass: a drag frame that resized the strip — and with it the sheet cap — would
            // shuffle the layout under a moving finger.
            val (bmp, band) = withContext(Dispatchers.IO) {
                val raster = renderPdfPageBitmap(
                    ctx,
                    renderScale = previewRenderScale(inputs.draft),
                ) { page ->
                    composeShaftPdf(
                        page = page,
                        spec = inputs.spec,
                        unit = inputs.unit,
                        project = inputs.project,
                        appVersion = versionSnapshot,
                        filename = "preview",
                        pdfPrefs = pdfPrefsSnapshot,
                        options = inputs.options,
                        resolvedComponents = inputs.resolved.takeIf { it.isNotEmpty() },
                        lineThicknessScale = inputs.lineThicknessScale,
                        heightScale = inputs.heightScale,
                        linerMinFracOfTrue = inputs.linerMinFracOfTrue,
                    )
                }
                raster to raster?.takeIf { !inputs.draft }?.inkBand()
            }
            if (bmp != null) {
                previewBitmap = bmp.asImageBitmap()
                if (!inputs.draft) inkBand = band
            } else {
                errorMessage = "Could not render preview."
            }
            isLoading = false
        }
    }

    // ── Pan / Zoom state ──────────────────────────────────────────────────────
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // ── Tuning layout ─────────────────────────────────────────────────────────
    // While the options sheet is open the page moves into a strip pinned under the app bar
    // and the sheet is capped just below it, so a slider's effect is visible as it is
    // dragged ("I can see the PDF Preview area lighten up on moving a slider but I can't
    // see anything" — on-device report). The strip carries the page's INK BAND, so the
    // drawing gets the room the blank top margin used to hold. The sheet's drag handle and
    // its navigation-bar inset stack OUTSIDE the content cap, so they are budgeted here;
    // strip and cap come from one set of inputs and cannot disagree. Opening the sheet
    // RESETS zoom/pan: predictable beats preserved, since a zoom would hide the strip.
    val configuration = LocalConfiguration.current
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
    val sheetChromeDp = TUNING_SHEET_CHROME_DP + navBottomDp
    val stripHeightDp = tuningPageStripHeightDp(
        configuration.screenWidthDp.toFloat(),
        configuration.screenHeightDp.toFloat(),
        sheetChromeDp,
        inkBand?.frac ?: 1f,
    )
    val maxSheetHeight = tuningSheetMaxHeightDp(
        configuration.screenHeightDp.toFloat(),
        stripHeightDp,
        sheetChromeDp,
    ).dp
    LaunchedEffect(showOptions) {
        if (showOptions) {
            scale.snapTo(1f)
            offset.snapTo(Offset.Zero)
        }
    }

    val gestures = Modifier
        .pointerInput(Unit) {
            detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                val old = scale.value
                val new = (old * zoom).coerceIn(0.5f, 8.0f)
                val z = if (old != 0f) new / old else 1f
                val newOffset = offset.value * z + centroid * (1f - z) + pan
                scope.launch {
                    if (new != old) scale.snapTo(new)
                    offset.snapTo(newOffset)
                }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = {
                    scope.launch {
                        scale.animateTo(1f, tween(140))
                        offset.animateTo(Offset.Zero, tween(140))
                    }
                }
            )
        }

    // ── UI ───────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Preview") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showOptions = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = "PDF options")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                scale.animateTo(1f, tween(140))
                                offset.animateTo(Offset.Zero, tween(140))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset zoom"
                        )
                    }
                    IconButton(onClick = {
                        val baseName = DocumentNaming.suggestedBaseName(
                            jobNumber = jobNumber,
                            customer = customer,
                            vessel = vessel,
                            suffix = shaftPosition.printableLabelOrNull(),
                        ) ?: "Shaft Schematic"
                        val jobName = if (pdfBlankDraft) "$baseName (blank draft)" else baseName
                        // Snapshot state on the UI thread; onWrite runs on a binder thread.
                        val specSnapshot = spec
                        val unitSnapshot = unit
                        val projectSnapshot = project
                        val optionsSnapshot = options
                        val resolvedSnapshot = resolvedComponents.takeIf { it.isNotEmpty() }
                        val prefsSnapshot = vm.currentPdfPrefs
                        val thicknessSnapshot = lineThicknessScale
                        val heightSnapshot = runoutConfig.heightScale
                        val linerFracSnapshot = runoutConfig.linerMinFracOfTrue
                        val versionSnapshot = appVersionName(ctx)
                        printShaftPdfPage(ctx, jobName) { page ->
                            composeShaftPdf(
                                page = page,
                                spec = specSnapshot,
                                unit = unitSnapshot,
                                project = projectSnapshot,
                                appVersion = versionSnapshot,
                                filename = "$jobName.pdf",
                                pdfPrefs = prefsSnapshot,
                                options = optionsSnapshot,
                                resolvedComponents = resolvedSnapshot,
                                lineThicknessScale = thicknessSnapshot,
                                heightScale = heightSnapshot,
                                linerMinFracOfTrue = linerFracSnapshot,
                            )
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Print,
                            contentDescription = "Print"
                        )
                    }
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Outlined.PictureAsPdf,
                            contentDescription = "Export PDF"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val currentError = errorMessage
        val currentBitmap = previewBitmap
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }

                currentError != null -> {
                    Text(
                        text = currentError,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                currentBitmap != null -> {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(gestures)
                            .testTag("pdf_preview_canvas")
                    ) {
                        withTransform({
                            translate(offset.value.x, offset.value.y)
                            scale(scale.value, scale.value, Offset.Zero)
                        }) {
                            if (showOptions) {
                                // Tuning layout: the drawing, cropped to its ink band,
                                // fitted into the strip the sheet was sized to leave free
                                // and top-aligned under the app bar.
                                drawPageBand(currentBitmap, inkBand, stripHeightDp.dp.toPx())
                            } else {
                                // Normal preview: the whole page — real paper, blank
                                // margins included — fitted to the canvas and centered.
                                val imgW = currentBitmap.width.toFloat()
                                val imgH = currentBitmap.height.toFloat()
                                val fitScale = minOf(size.width / imgW, size.height / imgH)
                                val fittedW = imgW * fitScale
                                val fittedH = imgH * fitScale
                                drawImage(
                                    image = currentBitmap,
                                    dstOffset = androidx.compose.ui.unit.IntOffset(
                                        ((size.width - fittedW) / 2f).toInt(),
                                        ((size.height - fittedH) / 2f).toInt()
                                    ),
                                    dstSize = androidx.compose.ui.unit.IntSize(
                                        fittedW.toInt(),
                                        fittedH.toInt()
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // The gap between the page strip and the sheet, dimmed — the modal affordance
            // the bottom sheet's own scrim would give, minus the part that matters: the
            // strip stays at full brightness. `ModalBottomSheet`'s scrim is all-or-nothing
            // over the whole window, so it is passed transparent and this stands in for it.
            // A drag takes even this off — the page is the thing being judged.
            if (showOptions && !tuning.active) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = stripHeightDp.dp)
                        .background(BottomSheetDefaults.ScrimColor)
                )
            }

            // Always-visible blank-draft toggle, overlaid on the preview — the SAME
            // session-only state as the options sheet's switch, so the two can never
            // disagree. Surfaced here because the sheet buried it (on-device report:
            // hard to find, even when demoing the app). Toggling re-renders the
            // preview live, so what's shown is always what will print. Hidden while the
            // options sheet is open: it would sit on the page strip, and the sheet's own
            // first row is this same switch.
            if (!showOptions) {
                FilterChip(
                    selected = pdfBlankDraft,
                    onClick = { vm.setPdfBlankDraft(!pdfBlankDraft) },
                    label = { Text("Blank draft (write-in)") },
                    leadingIcon = if (pdfBlankDraft) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .testTag("pdf_blank_toggle"),
                )
            }
        }
    }

    if (showOptions) {
        ModalBottomSheet(
            onDismissRequest = { showOptions = false },
            sheetState = sheetState,
            // `ModalBottomSheet`'s scrim is a full-window rect — it cannot be restricted to
            // the area below the page strip, and dimming the strip is exactly what this
            // layout exists to prevent. It stays transparent; the preview Box paints the
            // strip-to-sheet gap itself (and stops even that during a drag).
            scrimColor = Color.Transparent,
        ) {
            PdfOptionsSheet(
                vm = vm,
                spec = spec,
                pdfShowComponentTitles = pdfShowComponentTitles,
                pdfTieringMode = pdfTieringMode,
                lineThicknessScale = lineThicknessScale,
                sBreakThresholdFrac = pdfSBreakThresholdFrac,
                arrowSizePt = pdfArrowSizePt,
                heightScale = runoutConfig.heightScale,
                linersProportional = runoutConfig.linersProportional,
                linerCompression = runoutConfig.linerCompression,
                curveLoHeightIn = curveLoHeightIn,
                curveHiHeightIn = curveHiHeightIn,
                pdfShadedBodies = pdfShadedBodies,
                pdfShadedTapers = pdfShadedTapers,
                pdfShadedLiners = pdfShadedLiners,
                pdfBlankDraft = pdfBlankDraft,
                pdfBlankDiaCallouts = pdfBlankDiaCallouts,
                tuning = tuning,
                maxHeightDp = maxSheetHeight,
            )
        }
    }
}

/**
 * Everything one composed schematic preview page depends on, in one structural-equality
 * value — the render loop's unit of work.
 *
 * Some fields never reach [composeShaftPdf] directly: the shade flags, component titles,
 * tiering mode, S-break threshold, arrowhead size and sizing-curve anchors travel inside the
 * `PdfPrefs` snapshot taken at render time. They are held here because the loop must RE-RENDER when
 * they change, and a `PdfPrefs` read is not snapshot state.
 */
private data class SchematicRenderInputs(
    val spec: ShaftSpec,
    val unit: UnitSystem,
    val project: ProjectInfo,
    val options: PdfExportOptions,
    val resolved: List<ResolvedComponent>,
    val lineThicknessScale: Float,
    val showComponentTitles: Boolean,
    val tieringMode: PdfTieringMode,
    val shadedBodies: Boolean,
    val shadedTapers: Boolean,
    val shadedLiners: Boolean,
    val sBreakThresholdFrac: Float,
    val arrowSizePt: Float,
    val curveLoHeightIn: Float,
    val curveHiHeightIn: Float,
    val heightScale: Float,
    val linerMinFracOfTrue: Float,
    /** A tuning slider is mid-drag: raster at draft resolution and hold the spinner back. */
    val draft: Boolean,
)

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun appVersionName(context: Context): String = runCatching {
    val pm = context.packageManager
    val pkg = context.packageName
    if (Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName ?: "0"
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(pkg, 0).versionName ?: "0"
    }
}.getOrDefault("0")

@Composable
private fun PdfOptionsSheet(
    vm: ShaftViewModel,
    spec: ShaftSpec,
    pdfShowComponentTitles: Boolean,
    pdfTieringMode: PdfTieringMode,
    lineThicknessScale: Float,
    sBreakThresholdFrac: Float,
    arrowSizePt: Float,
    heightScale: Float,
    linersProportional: Boolean,
    linerCompression: Float,
    curveLoHeightIn: Float,
    curveHiHeightIn: Float,
    pdfShadedBodies: Boolean,
    pdfShadedTapers: Boolean,
    pdfShadedLiners: Boolean,
    pdfBlankDraft: Boolean,
    pdfBlankDiaCallouts: Boolean,
    /**
     * Live-tuning sink: every slider here reports its in-progress value so the preview
     * behind the sheet reshapes under the finger. Visual only — the commit path is
     * unchanged and nothing persists on a drag frame.
     */
    tuning: PreviewTuning,
    /**
     * Cap for this content column, computed by the screen from the SAME inputs as the page
     * strip ([tuningSheetMaxHeightDp]) so sheet and strip can never disagree. This sheet
     * tunes the page live, so it must not cover it.
     */
    maxHeightDp: Dp,
) {
    // Scrollable + inset-padded: the sheet's content is taller than a phone screen, so
    // without its own scroll the bottom rows clip mid-checkbox behind the navigation bar.
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeightDp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text("PDF Options", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        // ── Blank draft (write-in) ───────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = pdfBlankDraft,
                onCheckedChange = { vm.setPdfBlankDraft(it) },
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Blank draft (write-in)", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Prints the drawing with all values blanked for handwriting. Not saved — resets each session.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Sub-option of the switch above: a blank sheet either carries Ø leaders ready to
        // fill in, or prints clear so the diameters can be written in freehand wherever they
        // belong. Disabled (but visible) when blank mode is off, where it has no effect.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 24.dp, top = 4.dp),
        ) {
            Switch(
                checked = pdfBlankDiaCallouts,
                enabled = pdfBlankDraft,
                onCheckedChange = { vm.setPdfBlankDiaCallouts(it) },
                modifier = Modifier.testTag("pdf_blank_dia_callouts_toggle"),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Ø callouts",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (pdfBlankDraft) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Off prints the shaft clear of Ø leaders so they can be hand-written.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Labels ───────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = pdfShowComponentTitles,
                onCheckedChange = { vm.setPdfShowComponentTitles(it) },
            )
            Spacer(Modifier.width(12.dp))
            Text("Component labels", style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Line thickness ───────────────────────────────────────────────────
        LineThicknessSlider(
            scale = lineThicknessScale,
            onCommit = { vm.setLineThicknessScale(it) },
            onDrag = { tuning.lineThickness = it },
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Body S-break ─────────────────────────────────────────────────────
        // The same app-wide `PdfPrefs.sBreakThresholdFrac` Settings → Drawing sets —
        // here so the threshold can be judged against the drawing it changes.
        SBreakThresholdSlider(
            frac = sBreakThresholdFrac,
            onCommit = { vm.setPdfSBreakThresholdFrac(it) },
            onDrag = { tuning.sBreakFrac = it },
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Dimension arrows ─────────────────────────────────────────────────
        DimensionArrowSizeChips(
            arrowSizePt = arrowSizePt,
            onCommit = { vm.setPdfArrowSizePt(it) },
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Shaft height ─────────────────────────────────────────────────────
        // The same per-job multiplier the runout / consolidated sheets carry
        // (`RunoutConfig.heightScale`) — one slider value behind every drawing
        // (on-device request: the schematic was meant to follow it too). Selected by
        // drawn-height VALUE in paper inches; the schematic's base is the default
        // sizing curve at the configured anchor heights, no width-fit term.
        val sliderDiaMm = remember(spec) { spec.maxOuterDiaMm().coerceAtLeast(10f) }
        val sliderBase = defaultVisualScale(sliderDiaMm, curveLoHeightIn * 72f, curveHiHeightIn * 72f)
        ShaftHeightSlider(
            heightScale = heightScale,
            baseScale = sliderBase,
            maxDiaMm = sliderDiaMm,
            onCommit = { vm.setRunoutHeightScale(it) },
            onDrag = { tuning.heightScale = it },
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Liner compression ────────────────────────────────────────────────
        // Same per-job pair as the Consolidated Output tab (`RunoutConfig`).
        LinerCompressionControl(
            linersProportional = linersProportional,
            linerCompression = linerCompression,
            estimateKeptFrac = { frac ->
                estimatedLinerKeptFracOfTrue(spec, sliderBase, heightScale, frac)
            },
            onSetProportional = { vm.setLinersProportional(it) },
            onSetCompression = { vm.setLinerCompression(it) },
            onDrag = { tuning.linerCompression = it },
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Measurement reference ────────────────────────────────────────────
        Text("Measurement reference", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        listOf(
            PdfTieringMode.AUTO to "Auto (closest end)",
            PdfTieringMode.AFT  to "AFT",
            PdfTieringMode.FWD  to "FWD",
        ).forEach { (mode, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = pdfTieringMode == mode,
                    onClick = { vm.setPdfTieringMode(mode) },
                )
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Shade in PDF ─────────────────────────────────────────────────────
        ShadeInPdfChecks(
            pdfShadedBodies = pdfShadedBodies,
            pdfShadedTapers = pdfShadedTapers,
            pdfShadedLiners = pdfShadedLiners,
            onSetShadedBodies = { vm.setPdfShadedBodies(it) },
            onSetShadedTapers = { vm.setPdfShadedTapers(it) },
            onSetShadedLiners = { vm.setPdfShadedLiners(it) },
        )

        Spacer(Modifier.height(24.dp))
    }
}
