// file: app/src/main/java/com/android/shaftschematic/ui/screen/UndercutRoute.kt
package com.android.shaftschematic.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.SurfaceSeg
import com.android.shaftschematic.geom.UNDERCUT_EXAGGERATION_MAX_FRAC
import com.android.shaftschematic.geom.UndercutLinerSpan
import com.android.shaftschematic.geom.UndercutSpanMm
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.geom.UndercutWindow
import com.android.shaftschematic.geom.assignUndercutLiner
import com.android.shaftschematic.geom.buildUndercutStrips
import com.android.shaftschematic.geom.clampUndercutSpan
import com.android.shaftschematic.geom.isUndercutStaleOverrun
import com.android.shaftschematic.geom.linerStripFor
import com.android.shaftschematic.geom.maxOuterDiaOver
import com.android.shaftschematic.geom.pickUndercutStripAt
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.pdf.composeUndercutPdf
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.surfaceSegsFrom
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.buildLinerTitleById
import com.android.shaftschematic.util.buildOpenPdfIntent
import com.android.shaftschematic.util.printShaftPdfPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * UndercutRoute
 *
 * Screen for the Undercut Drawing tab — the shop's record of machined-below-surface sections
 * (weld-repair undercuts, cleanup cuts). See `docs/UndercutDrawing_PLAN.md`.
 *
 * ## Layout
 * - **Overview canvas** — `ShaftLayout.compute` + `ShaftRenderer.draw` over
 *   `resolvedComponents` (never raw spec), then an affordance pass: every undercut's notch cut
 *   into the profile, plus a faint tint + count badge over **every liner** and over each
 *   bare-shaft **cluster window**. Liners are tap targets whether or not they hold cuts (the
 *   wear document's idiom) — on-device report: tapping a liner that had no cut yet did nothing,
 *   leaving no way in. A tap hit-tests in mm space via [ShaftLayout.Result.xMmFromPx]:
 *   `pickUndercutStripAt` first, then liner containment, and opens
 *   [UndercutWindowDetailOverlay] on that strip (an empty liner yields an empty
 *   `linerStripFor` strip — the authoring entry point). No pinch-zoom here; the overlay owns zoom.
 * - **Add undercut** — records a 1 in section just FWD of the AFT S.E.T. and opens its strip.
 *   Precision comes from the overlay's numeric fields, never from the tap (wear posture).
 * - **Cut depth exaggeration** — a slider (0 – `UNDERCUT_EXAGGERATION_MAX_FRAC`) storing the
 *   sheet's drawn-depth setting in the record. Display-only styling for every notch draw site;
 *   it never touches a stored or printed Ø.
 * - **Undercut list** — one compact row per recorded cut, aft → fwd: its distance under its own
 *   authored reference, length, Ø, and a stale-overrun warning. Tapping a row opens that cut's
 *   strip; the trailing icon deletes it (confirm-free, the wear-spot posture).
 * - Blank-draft toggle, Preview, Print, Export — straight ports of the wear document's flows,
 *   all calling `composeUndercutPdf`.
 *
 * Undercuts are reference-only data: nothing on this screen touches geometry, OAL, or
 * collision.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun UndercutRoute(
    vm: ShaftViewModel,
    onOpenSidebar: () -> Unit = {},
) {
    val spec               by vm.spec.collectAsState()
    val resolvedComponents by vm.resolvedComponents.collectAsState()
    val unit               by vm.unit.collectAsState()
    val customer           by vm.customer.collectAsState()
    val vessel             by vm.vessel.collectAsState()
    val jobNumber          by vm.jobNumber.collectAsState()
    val shaftPosition      by vm.shaftPosition.collectAsState()
    val openAfterExport    by vm.openPdfAfterExport.collectAsState()
    val lineThicknessScale by vm.lineThicknessScale.collectAsState()
    val pdfShadedBodies    by vm.pdfShadedBodies.collectAsState()
    val pdfShadedTapers    by vm.pdfShadedTapers.collectAsState()
    val pdfShadedLiners    by vm.pdfShadedLiners.collectAsState()
    val undercutRecord     by vm.undercutRecord.collectAsState()

    val ctx = LocalContext.current
    var showPreview by rememberSaveable { mutableStateOf(false) }
    // Blank-draft (write-in) copy: outline + form layout, all values blanked for handwriting.
    var blankDraft by rememberSaveable { mutableStateOf(false) }
    // Plain remember: an ImageBitmap is not saveable (crashes onSaveInstanceState),
    // and the LaunchedEffect below regenerates it anyway.
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    // What anchors the open detail overlay. Strips are derived state (they re-cluster on every
    // record edit), so the anchor is an id rather than a strip index and the strip is looked up
    // fresh below. Two anchors, one live at a time: a **liner** id keeps a liner strip open even
    // while it holds no cuts (so an emptied liner strip doesn't slam shut mid-authoring), an
    // **undercut** id anchors a bare-shaft cluster. The liner anchor wins when both are set.
    var anchorLinerId by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorUndercutId by rememberSaveable { mutableStateOf<String?>(null) }

    val oalMm = spec.overallLengthMm.coerceAtLeast(0f)
    val segs = remember(resolvedComponents) { surfaceSegsFrom(resolvedComponents) }
    val notches = remember(undercutRecord, segs, oalMm) {
        buildUndercutNotches(
            undercutRecord.undercuts, segs, oalMm,
            exaggerationFrac = undercutRecord.exaggerationFrac,
        )
    }
    val linerSpans = remember(resolvedComponents) { linerSpansOf(resolvedComponents) }
    val spans = remember(undercutRecord, oalMm) {
        undercutRecord.undercuts.map { u ->
            val c = clampUndercutSpan(u.startFromAftMm, u.lengthMm, oalMm)
            UndercutSpanMm(u.id, c.startMm, c.endMm)
        }.filter { it.endMm > it.startMm }
    }
    val strips = remember(spans, linerSpans, oalMm) {
        buildUndercutStrips(spans, linerSpans, oalMm)
    }
    val freeWindows = remember(strips) {
        strips.filterIsInstance<UndercutStrip.FreeStrip>().map { it.window }
    }
    val cutCountByLiner = remember(spans, linerSpans) {
        spans.mapNotNull { assignUndercutLiner(it, linerSpans) }.groupingBy { it }.eachCount()
    }

    // One liner's strip, cuts included — the same construction `buildUndercutStrips` uses, so a
    // liner tapped with zero cuts and the same liner tapped later with three read identically.
    fun stripForLiner(liner: UndercutLinerSpan): UndercutStrip.LinerStrip = linerStripFor(
        liner, spans.filter { assignUndercutLiner(it, linerSpans) == liner.id }, oalMm,
    )

    // An anchor that no longer resolves (the undercut was deleted, the liner removed, or the
    // shaft shrank past the cut) simply yields no overlay — an anchor is never auto-cleared,
    // because the record and the anchor can update in either order within a frame and clearing
    // on the stale pass would close an overlay that was just opened.
    val anchoredUndercutId = anchorUndercutId
    val activeStrip: UndercutStrip? = when {
        anchorLinerId != null ->
            linerSpans.firstOrNull { it.id == anchorLinerId }?.let { ln -> stripForLiner(ln) }
        anchoredUndercutId != null ->
            strips.firstOrNull { anchoredUndercutId in it.undercutIds }
        else -> null
    }

    // Open the strip an undercut belongs to, from the list below the canvas.
    fun anchorToUndercut(id: String) {
        when (val s = strips.firstOrNull { id in it.undercutIds }) {
            is UndercutStrip.LinerStrip -> { anchorLinerId = s.linerId; anchorUndercutId = null }
            else -> { anchorLinerId = null; anchorUndercutId = id }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    val doc = PdfDocument()
                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
                        val page = doc.startPage(pageInfo)
                        composeUndercutPdf(
                            page = page, spec = spec,
                            project = ProjectInfo(customer = customer, vessel = vessel,
                                jobNumber = jobNumber, side = shaftPosition),
                            unit = unit,
                            pdfPrefs = vm.currentPdfPrefs,
                            resolvedComponents = resolvedComponents,
                            undercutRecord = undercutRecord,
                            lineThicknessScale = lineThicknessScale,
                            blankValues = blankDraft,
                        )
                        doc.finishPage(page)
                        doc.writeTo(out)
                    } finally {
                        try { out.flush() } catch (_: Throwable) {}
                        doc.close()
                    }
                }
                if (openAfterExport) openUndercutPdf(ctx, uri)
            }
        }
    }

    LaunchedEffect(showPreview, spec, unit, resolvedComponents,
                   lineThicknessScale, pdfShadedBodies, pdfShadedTapers, pdfShadedLiners,
                   undercutRecord, blankDraft) {
        if (!showPreview) { previewBitmap = null; return@LaunchedEffect }
        previewLoading = true
        val prefsSnapshot     = vm.currentPdfPrefs
        val thicknessSnapshot = lineThicknessScale
        val bmp = withContext(Dispatchers.IO) {
            renderUndercutBitmap(
                context = ctx, spec = spec,
                project = ProjectInfo(customer = customer, vessel = vessel,
                    jobNumber = jobNumber, side = shaftPosition),
                unit = unit,
                pdfPrefs = prefsSnapshot,
                resolvedComponents = resolvedComponents,
                lineThicknessScale = thicknessSnapshot,
                undercutRecord = undercutRecord,
                blankValues = blankDraft,
            )
        }
        previewBitmap = bmp?.asImageBitmap()
        previewLoading = false
    }

    // Capture theme colors before the Canvas block (DrawScope is not composable) — same
    // technique as WearRoute's overview canvas.
    val outlineArgb    = MaterialTheme.colorScheme.onSurface.toArgb()
    // Component fills are fixed black-alpha, not theme colors: the overview canvas is a
    // paper-white sheet in both themes, so a dark-theme tint (near-white onSurface) would
    // vanish into the paper and the pure-white notch voids would lose the grey they read
    // against. The liner takes the PDF shade fill's weight (argb 40) — grey liner, white cut
    // sections (on-device request); bodies/tapers stay lighter so the liner still reads as the
    // outer surface.
    val bodyFillArgb   = Color.Black.copy(alpha = 0.08f).toArgb()
    val linerFillArgb  = Color.Black.copy(alpha = 0.16f).toArgb()
    val hatchArgb      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f).toArgb()
    val outlineColor   = MaterialTheme.colorScheme.onSurface
    val tapTintColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val tapBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    val badgeColor     = MaterialTheme.colorScheme.primary
    val badgeTextArgb  = MaterialTheme.colorScheme.onPrimary.toArgb()
    val previewShape   = MaterialTheme.shapes.medium

    val previewOpts = remember(outlineArgb, bodyFillArgb, linerFillArgb, hatchArgb) {
        RenderOptions(
            outlineColor        = outlineArgb,
            outlineWidthPx      = 1.5f,
            bodyFillColor       = bodyFillArgb,
            taperFillColor      = bodyFillArgb,
            linerFillColor      = linerFillArgb,
            threadFillColor     = 0x00000000,
            threadHatchColor    = hatchArgb,
        )
    }

    // ── Main UI ─────────────────────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

        // ── Toolbar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenSidebar) {
                Icon(Icons.Filled.Menu, contentDescription = "Open navigation")
            }
            Text(
                text = "Undercut Drawing",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        HorizontalDivider()

        // ── Scrollable content ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Records undercut sections — distance from a S.E.T., length, and measured " +
                    "diameter — and prints them as zoomed detail views with chained dimensions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── Overview canvas — tap a cluster window to open its detail view ────
            if (oalMm > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(previewShape)
                        .background(Color.White)
                        .pointerInput(spec, resolvedComponents, strips, linerSpans) {
                            detectTapGestures { tapOffset ->
                                val layout = ShaftLayout.compute(
                                    spec               = spec,
                                    leftPx             = 0f,
                                    topPx              = 0f,
                                    rightPx            = size.width.toFloat(),
                                    bottomPx           = size.height.toFloat(),
                                    marginPx           = 12.dp.toPx(),
                                    resolvedComponents = resolvedComponents,
                                )
                                val tapMm = layout.xMmFromPx(tapOffset.x)
                                // A strip claims the tap first (it covers its cuts plus context);
                                // failing that, any liner opens as an empty strip to author in.
                                val hit = pickUndercutStripAt(tapMm, strips)
                                    ?: pickLinerIdAtMm(
                                        tapMm,
                                        linerSpans.map { LinerSpanMm(it.id, it.startMm, it.endMm) },
                                    )?.let { id -> linerSpans.firstOrNull { it.id == id } }
                                        ?.let { ln -> stripForLiner(ln) }
                                when (hit) {
                                    is UndercutStrip.LinerStrip -> {
                                        anchorLinerId = hit.linerId
                                        anchorUndercutId = null
                                    }
                                    is UndercutStrip.FreeStrip -> {
                                        anchorLinerId = null
                                        anchorUndercutId = hit.window.undercutIds.firstOrNull()
                                    }
                                    null -> Unit
                                }
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val marginPx = 12.dp.toPx()
                        val layout = ShaftLayout.compute(
                            spec               = spec,
                            leftPx             = 0f,
                            topPx              = 0f,
                            rightPx            = size.width,
                            bottomPx           = size.height,
                            marginPx           = marginPx,
                            resolvedComponents = resolvedComponents,
                        )
                        with(ShaftRenderer) {
                            draw(spec, layout, previewOpts, resolvedComponents)
                        }
                        // Notches first (they erase profile strokes inside each cut), then the
                        // window tint + badge on top as the tap affordance.
                        drawUndercutNotches(
                            notches = notches,
                            xPx = { mm -> layout.xPx(mm) },
                            rPx = { dia -> layout.rPx(dia) },
                            cy = layout.centerlineYPx,
                            voidColor = Color.White,
                            outlineColor = outlineColor,
                            strokeWidthPx = 1.5f,
                        )
                        drawUndercutStripAffordances(
                            layout = layout,
                            liners = linerSpans,
                            cutCountByLiner = cutCountByLiner,
                            windows = freeWindows,
                            segs = segs,
                            tapTintColor = tapTintColor,
                            tapBorderColor = tapBorderColor,
                            badgeColor = badgeColor,
                            badgeTextArgb = badgeTextArgb,
                        )
                    }
                }
                Text(
                    text = when {
                        linerSpans.isNotEmpty() ->
                            "Tap a liner — or any highlighted section — to zoom in and record cuts there."
                        freeWindows.isEmpty() ->
                            "No undercuts recorded yet — add one to start the drawing."
                        else ->
                            "Tap a highlighted section to open its zoomed detail view."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = {
                    val (aftSetXMm, _) = undercutSetPositions(spec)
                    val start = aftSetXMm.coerceIn(0f, oalMm)
                    val length = DEFAULT_UNDERCUT_LENGTH_MM
                        .coerceAtMost((oalMm - start).coerceAtLeast(0.1f))
                    anchorLinerId = null
                    anchorUndercutId = vm.addUndercut(start, length)
                },
                enabled = oalMm > 0f,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add undercut")
            }

            // ── Drawn depth exaggeration + recorded undercuts ─────────────────
            // Visibility + removal without hunting for the right strip first: every cut on the
            // shaft is listed here, aft → fwd, whichever strip it belongs to.
            if (undercutRecord.undercuts.isNotEmpty()) {
                HorizontalDivider()

                // Sheet-wide drawn-depth styling, sitting directly under the overview canvas
                // it restyles so the change is visible while dragging. Display-only: the
                // deepest cut draws at this fraction of its local surface Ø and shallower
                // cuts scale relative to it, so sheets with very different absolute depths
                // read alike; stored and printed Ø values never move (golden rule). Commits
                // continuously — the same live-update posture as the OAL field and the
                // preview options sliders (not a NumericInputField, so the commit-on-blur
                // rule does not apply here).
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Cut depth exaggeration", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${(undercutRecord.exaggerationFrac * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = undercutRecord.exaggerationFrac,
                        onValueChange = { vm.setUndercutExaggeration(it) },
                        valueRange = 0f..UNDERCUT_EXAGGERATION_MAX_FRAC,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("undercut_exaggeration_slider"),
                    )
                    Text(
                        "Drawing only — 0% is true scale. The deepest cut draws at this depth " +
                            "and shallower cuts scale to it; printed Ø values never change.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Own spacing: the surrounding 16 dp rhythm would read as separate blocks
                // rather than one list.
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Recorded undercuts",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    val setPositions = undercutSetPositions(spec)
                    val linerTitles = buildLinerTitleById(spec)
                    undercutRecord.undercuts
                        .sortedBy { it.startFromAftMm }
                        .forEachIndexed { i, u ->
                            UndercutListRow(
                                index = i,
                                undercut = u,
                                unit = unit,
                                oalMm = oalMm,
                                linerSpans = linerSpans,
                                linerTitles = linerTitles,
                                aftSetXMm = setPositions.first,
                                fwdSetXMm = setPositions.second,
                                onOpen = { anchorToUndercut(u.id) },
                                onDelete = { vm.removeUndercut(u.id) },
                            )
                        }
                }
            }

            HorizontalDivider()

            // ── Blank draft toggle ────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = blankDraft, onCheckedChange = { blankDraft = it })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Blank draft (write-in)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Job info and OAL are blanked; recorded undercuts are omitted — a fresh form.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedButton(
                onClick = { showPreview = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Preview, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Preview Undercut Drawing")
            }

            // ── Print button ──────────────────────────────────────────────────
            OutlinedButton(
                onClick = {
                    val jobName = buildUndercutFilename(customer, vessel, jobNumber, blankDraft)
                        .removeSuffix(".pdf")
                    // Snapshot state on the UI thread; onWrite runs on a binder thread.
                    val specSnapshot = spec
                    val projectSnapshot = ProjectInfo(customer = customer, vessel = vessel,
                        jobNumber = jobNumber, side = shaftPosition)
                    val unitSnapshot = unit
                    val prefsSnapshot = vm.currentPdfPrefs
                    val resolvedSnapshot = resolvedComponents
                    val thicknessSnapshot = lineThicknessScale
                    val recordSnapshot = undercutRecord
                    val blankSnapshot = blankDraft
                    printShaftPdfPage(ctx, jobName) { page ->
                        composeUndercutPdf(
                            page = page, spec = specSnapshot,
                            project = projectSnapshot, unit = unitSnapshot,
                            pdfPrefs = prefsSnapshot,
                            resolvedComponents = resolvedSnapshot,
                            undercutRecord = recordSnapshot,
                            lineThicknessScale = thicknessSnapshot,
                            blankValues = blankSnapshot,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Print, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Print Undercut Drawing")
            }

            Button(
                onClick = { launcher.launch(buildUndercutFilename(customer, vessel, jobNumber, blankDraft)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export Undercut Drawing PDF")
            }
        }
    }

    BackHandler(enabled = showPreview) { showPreview = false }
    if (showPreview) {
        PdfPreviewOverlay(
            bitmap = previewBitmap,
            loading = previewLoading,
            title = "Undercut Drawing Preview",
            onClose = { showPreview = false },
            onExport = {
                showPreview = false
                launcher.launch(buildUndercutFilename(customer, vessel, jobNumber, blankDraft))
            },
            optionsSheet = {
                RunoutWearOptionsSheet(
                    lineThicknessScale = lineThicknessScale,
                    pdfShadedBodies = pdfShadedBodies,
                    pdfShadedTapers = pdfShadedTapers,
                    pdfShadedLiners = pdfShadedLiners,
                    vm = vm,
                )
            },
        )
    }

    // ── Undercut strip detail overlay ─────────────────────────────────────────
    // UndercutWindowDetailOverlay hosts its own BackHandler; only compose it while a strip
    // is active.
    activeStrip?.let { s ->
        UndercutWindowDetailOverlay(
            strip = s,
            spec = spec,
            resolvedComponents = resolvedComponents,
            unit = unit,
            undercutRecord = undercutRecord,
            onAddUndercut = { startMm, lengthMm, reference, referenceLinerId ->
                // Called when the overlay CONFIRMS a drafted new cut (a cancelled draft never
                // reaches here, so the record gains no ghosts). The returned id lets the overlay
                // land the draft's Ø/note and follow the new card; the open anchor already covers
                // this strip, so no re-anchoring is needed.
                vm.addUndercut(startMm, lengthMm, reference, referenceLinerId)
            },
            onUpdateUndercut = vm::updateUndercut,
            onUpdateReference = vm::updateUndercutReference,
            onRemoveUndercut = { id ->
                // A liner strip stays open on its liner regardless. On a free strip, keep the
                // overlay anchored when a sibling is left behind; deleting the last member
                // closes it via the anchor-resolution above.
                if (anchorLinerId == null && id == anchorUndercutId) {
                    anchorUndercutId = s.undercutIds.firstOrNull { it != id }
                }
                vm.removeUndercut(id)
            },
            onClose = { anchorLinerId = null; anchorUndercutId = null },
        )
    }
}

/** Default length of a newly added undercut (1 in), clamped to the remaining shaft extent. */
internal const val DEFAULT_UNDERCUT_LENGTH_MM = 25.4f

private fun buildUndercutFilename(
    customer: String,
    vessel: String,
    jobNumber: String,
    blankDraft: Boolean = false,
): String {
    val parts = listOf(customer, vessel, jobNumber).filter { it.isNotBlank() }
    val blankSuffix = if (blankDraft) "_BlankDraft" else ""
    return "${if (parts.isNotEmpty()) parts.joinToString("_") else "Undercuts"}_undercuts$blankSuffix.pdf"
}

private fun openUndercutPdf(context: Context, uri: Uri) {
    val intent = buildOpenPdfIntent(context, uri)
    context.packageManager.queryIntentActivities(intent, 0).forEach { ri ->
        runCatching {
            context.grantUriPermission(
                ri.activityInfo?.packageName ?: return@forEach, uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
    try { context.startActivity(Intent.createChooser(intent, "Open PDF")) }
    catch (_: ActivityNotFoundException) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Undercut list row
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One recorded undercut, summarized: its distance under the reference it was authored against
 * (with that reference's short tag), its length, and its Ø — "—" while unmeasured. A cut whose
 * span no longer fits the shaft (`isUndercutStaleOverrun`) leads with a warning icon, the same
 * non-blocking classifier the card shows in full.
 *
 * The whole row opens the cut's strip; the trailing icon deletes it outright — confirm-free, the
 * wear-spot posture. Neither path touches canonical values.
 */
@Composable
private fun UndercutListRow(
    index: Int,
    undercut: Undercut,
    unit: UnitSystem,
    oalMm: Float,
    linerSpans: List<UndercutLinerSpan>,
    linerTitles: Map<String, String>,
    aftSetXMm: Float,
    fwdSetXMm: Float,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val reference = effectiveUndercutReference(undercut, linerSpans)
    val refLiner = undercutReferenceLinerFor(undercut, linerSpans, stripLiner = null, oalMm = oalMm)
    val distanceMm = undercutDisplayedDistanceMm(undercut, reference, refLiner, aftSetXMm, fwdSetXMm)
    val stale = isUndercutStaleOverrun(undercut.startFromAftMm, undercut.lengthMm, oalMm)

    // A liner reference names its liner, so two cuts in different liners never read alike.
    val referenceTag = when (reference) {
        UndercutReference.LINER_AFT, UndercutReference.LINER_FWD -> {
            val title = refLiner?.id?.let { linerTitles[it] }
            val edge = if (reference == UndercutReference.LINER_AFT) "AFT" else "FWD"
            if (title != null) "$title $edge edge" else undercutReferenceLabel(reference)
        }
        else -> undercutReferenceLabel(reference)
    }
    val diaText = if (undercut.diaMm > 0f) disp(undercut.diaMm, unit) + abbr(unit) else "—"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onOpen)
            .testTag("undercut_row_${undercut.id}")
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stale) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "Extends past shaft end",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${index + 1}. ${disp(distanceMm, unit)}${abbr(unit)} from $referenceTag",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "L ${disp(undercut.lengthMm, unit)}${abbr(unit)}  ·  Ø $diaText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("undercut_delete_${undercut.id}"),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete undercut ${index + 1}")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Strip tap affordances — drawn after ShaftRenderer.draw() and the notches
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draw a faint tint + border over every tap target — **every liner** (whether or not it holds
 * cuts) and every bare-shaft cluster window — plus a small count badge carrying how many cuts
 * that target holds. Purely a rendering overlay: reads the spans but never mutates the record.
 *
 * Liners are unconditional targets so an empty one can be zoomed and authored in, matching the
 * wear document; the free window remains the target for cuts that belong to no liner, since
 * undercuts are not component-bound. A liner with no cuts gets no badge (nothing to count).
 */
private fun DrawScope.drawUndercutStripAffordances(
    layout: ShaftLayout.Result,
    liners: List<UndercutLinerSpan>,
    cutCountByLiner: Map<String, Int>,
    windows: List<UndercutWindow>,
    segs: List<SurfaceSeg>,
    tapTintColor: Color,
    tapBorderColor: Color,
    badgeColor: Color,
    badgeTextArgb: Int,
) {
    if (liners.isEmpty() && windows.isEmpty()) return

    val badgePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = badgeTextArgb
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 24f
    }
    val badgeRadiusPx = 11.dp.toPx()
    val badgeGapPx = 4.dp.toPx()

    fun target(startMm: Float, endMm: Float, count: Int) {
        val x0 = layout.xPx(startMm)
        val x1 = layout.xPx(endMm)
        val r = layout.rPx(maxOuterDiaOver(segs, startMm, endMm)).coerceAtLeast(4.dp.toPx())
        val top = layout.centerlineYPx - r
        val bot = layout.centerlineYPx + r

        drawRect(color = tapTintColor, topLeft = Offset(x0, top), size = Size(x1 - x0, bot - top))
        drawRect(
            color = tapBorderColor,
            topLeft = Offset(x0, top),
            size = Size(x1 - x0, bot - top),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        if (count <= 0) return
        val cx = (x0 + x1) / 2f
        val cy = (top - badgeGapPx - badgeRadiusPx).coerceAtLeast(badgeRadiusPx)
        drawCircle(color = badgeColor, radius = badgeRadiusPx, center = Offset(cx, cy))
        drawContext.canvas.nativeCanvas.drawText(
            "$count", cx, cy + badgeRadiusPx * 0.35f, badgePaint,
        )
    }

    liners.forEach { ln -> target(ln.startMm, ln.endMm, cutCountByLiner[ln.id] ?: 0) }
    windows.forEach { w -> target(w.startMm, w.endMm, w.undercutIds.size) }
}

private fun renderUndercutBitmap(
    context: Context,
    spec: ShaftSpec,
    project: ProjectInfo,
    unit: UnitSystem,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    resolvedComponents: List<ResolvedComponent>? = null,
    lineThicknessScale: Float = 1.0f,
    undercutRecord: UndercutRecord = UndercutRecord(),
    blankValues: Boolean = false,
): Bitmap? = runCatching {
    val tempFile = File.createTempFile("undercut_preview_", ".pdf", context.cacheDir)
    val doc = PdfDocument()
    try {
        val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
        val page = doc.startPage(pageInfo)
        composeUndercutPdf(page = page, spec = spec, project = project, unit = unit,
            pdfPrefs = pdfPrefs, resolvedComponents = resolvedComponents,
            undercutRecord = undercutRecord, lineThicknessScale = lineThicknessScale,
            blankValues = blankValues)
        doc.finishPage(page)
        tempFile.outputStream().buffered().use { doc.writeTo(it) }
    } finally {
        doc.close()
    }
    val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(pfd)
    try {
        val pdfPage = renderer.openPage(0)
        try {
            val bmp = Bitmap.createBitmap(pdfPage.width * 2, pdfPage.height * 2, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } finally { pdfPage.close() }
    } finally { renderer.close(); pfd.close(); tempFile.delete() }
}.getOrNull()
