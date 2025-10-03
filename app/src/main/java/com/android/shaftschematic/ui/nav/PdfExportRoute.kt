// app/src/main/java/com/android/shaftschematic/ui/nav/PdfExportRoute.kt
package com.android.shaftschematic.ui.nav

import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.android.shaftschematic.pdf.composeShaftPdf // <-- adjust if your function lives elsewhere
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * PdfExportRoute
 *
 * Purpose
 * Uses SAF CreateDocument to export a PDF. Actual drawing is delegated to composeShaftPdf.
 */
@Composable
fun PdfExportRoute(
    nav: NavController,
    vm: ShaftViewModel,
    onFinished: () -> Unit
) {
    val ctx = LocalContext.current
    var done by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        runCatching {
            if (uri != null) {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    val doc = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create() // Letter @ 72dpi
                    val page = doc.startPage(pageInfo)

                    val filename = uri.lastPathSegment ?: "Shaft.pdf"
                    // Minimal project info stub; replace with your real data hook
                    val project = com.android.shaftschematic.pdf.ProjectInfo()

                    composeShaftPdf(
                        page = page,
                        spec = vm.spec.value,
                        unit = vm.unit.value,
                        project = project,
                        appVersion = "0.1.0",
                        filename = filename
                    )
                    doc.finishPage(page)
                    doc.writeTo(out)
                    doc.close()
                }
            }
        }
        done = true
    }

    LaunchedEffect(Unit) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        launcher.launch("Shaft_$stamp.pdf")
    }

    if (done) onFinished() else Text("")
}
