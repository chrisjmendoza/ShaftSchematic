// app/src/main/java/com/android/shaftschematic/ui/nav/PdfExportRoute.kt
package com.android.shaftschematic.ui.nav

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.pdf.composeShaftPdf
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import android.graphics.Canvas
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.domain.geom.computeOalWindow
import com.android.shaftschematic.domain.geom.computeSetPositionsInMeasureSpace
import com.android.shaftschematic.domain.model.LinerDim
import com.android.shaftschematic.domain.model.LinerAnchor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Route that exports the current shaft as a single-page PDF via SAF (“Create Document”).
 *
 * Units: **mm** (millimeters) in the underlying model. The composer handles unit labels.
 *
 * Behavior:
 * - Launches the system save dialog once.
 * - If a URI is chosen, writes a Letter-landscape page via [composeShaftPdf].
 * - Calls [onFinished] after success or cancel so the caller can navigate away.
 *
 * See also: `/docs/PdfExportRoute.md` for the longer contract and future options.
 */
@Composable
fun PdfExportRoute(
    nav: NavController,
    vm: ShaftViewModel,
    onFinished: () -> Unit
) {
    val ctx = LocalContext.current
    var launched by rememberSaveable { mutableStateOf(false) }
    var finished by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        runCatching {
            if (uri != null) {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    val doc = PdfDocument()
                    try {
                        // US Letter landscape: 792×612 pt (1 pt = 1/72")
                        val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
                        val page = doc.startPage(pageInfo)

                        val filename = uri.lastPathSegment ?: defaultFilename()
                        val project = ProjectInfo(customer = "", vessel = "", jobNumber = "")

                        composeShaftPdf(
                            page = page,
                            spec = vm.spec.value,
                            unit = vm.unit.value,
                            project = project,
                            appVersion = appVersionFromContext(ctx),
                            filename = filename
                        )

                        doc.finishPage(page)
                        doc.writeTo(out)
                    } finally {
                        try { out.flush() } catch (_: Throwable) {}
                        doc.close()
                    }
                }
            }
        }.onFailure {
            // Optional: surface a snackbar or log.
        }
        finished = true
    }

    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            launcher.launch(defaultFilename())
        }
    }

    if (finished) {
        onFinished()
    } else {
        Text("") // keep composition alive while the picker is open
    }
}

/** Default export filename suggestion. */
private fun defaultFilename(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    return "Shaft_$stamp.pdf"
}

/** Obtain app versionName without BuildConfig coupling. */
private fun appVersionFromContext(context: Context): String {
    return try {
        val pm = context.packageManager
        val pkg = context.packageName
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName ?: "0"
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0).versionName ?: "0"
        }
    } catch (_: Throwable) {
        "0"
    }
}
private data class LinerProjection(
    val id: String,
    val startMm: Double,
    val endMm: Double
)

/**
 * Projects model liners into measurement-space using the OAL window.
 */
private fun projectLinersToMeasure(spec: ShaftSpec): List<LinerProjection> {
    val win = computeOalWindow(spec)
    return spec.liners.map { ln ->
        val start = win.toMeasureX(ln.startFromAftMm.toDouble())
        val end = start + ln.lengthMm.toDouble()
        LinerProjection(id = ln.id, startMm = start, endMm = end)
    }
}

/**
 * Adapts projected liners to the export-only LinerDim shape.
 * Anchor is inferred by proximity to SETs; if your liners already encode anchor, wire it here directly.
 */
private fun mapToLinerDims(spec: ShaftSpec): List<LinerDim> {
    val win = computeOalWindow(spec)
    val sets = computeSetPositionsInMeasureSpace(win)

    return projectLinersToMeasure(spec).map { p ->
        val length = (p.endMm - p.startMm).coerceAtLeast(0.0)
        val aftGapToStart = p.startMm - sets.aftSETxMm
        val fwdGapToStart = sets.fwdSETxMm - p.startMm
        val anchor = if (fwdGapToStart < aftGapToStart) LinerAnchor.FWD_SET else LinerAnchor.AFT_SET

        when (anchor) {
            LinerAnchor.AFT_SET -> LinerDim(
                id = p.id,
                anchor = anchor,
                offsetFromSetMm = aftGapToStart.coerceAtLeast(0.0),
                lengthMm = length
            )
            LinerAnchor.FWD_SET -> LinerDim(
                id = p.id,
                anchor = anchor,
                // Offset is FWD SET → FWD edge (toward AFT)
                offsetFromSetMm = (sets.fwdSETxMm - p.endMm).coerceAtLeast(0.0),
                // Length runs AFT from FWD edge
                lengthMm = length
            )
        }
    }
}

/**
 * Call this from your existing PDF export flow after the shaft graphics are drawn.
 */
fun drawDimensionsSection(canvas: Canvas, spec: ShaftSpec, style: DimStyle) {
    val linerDims = mapToLinerDims(spec)
    drawLinerDimensionsPdf(canvas, spec, linerDims, style)
}
