package com.android.shaftschematic.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.ui.screen.PdfPreviewScreen
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.defaultPdfFilename
import com.android.shaftschematic.util.appVersionFromContext

@Composable
fun PdfPreviewRoute(
    vm: ShaftViewModel,
    onClose: () -> Unit,
    onExportPdf: () -> Unit,
) {
    val ctx = LocalContext.current

    val spec by vm.spec.collectAsState()
    val unit by vm.unit.collectAsState()
    val resolvedComponents by vm.resolvedComponents.collectAsState()

    val customer by vm.customer.collectAsState()
    val vessel by vm.vessel.collectAsState()
    val jobNumber by vm.jobNumber.collectAsState()
    val shaftPosition by vm.shaftPosition.collectAsState()

    val pdfExportMode by vm.pdfExportMode.collectAsState()

    val project = ProjectInfo(
        customer = customer,
        vessel = vessel,
        side = shaftPosition,
        jobNumber = jobNumber
    )

    PdfPreviewScreen(
        spec = spec,
        unit = unit,
        project = project,
        appVersion = appVersionFromContext(ctx),
        filename = defaultPdfFilename(
            jobNumber = jobNumber,
            customer = customer,
            vessel = vessel,
            shaftPosition = shaftPosition
        ),
        pdfPrefs = vm.currentPdfPrefs,
        options = com.android.shaftschematic.pdf.PdfExportOptions(mode = pdfExportMode),
        resolvedComponents = resolvedComponents,
        onClose = onClose,
        onExportPdf = onExportPdf
    )
}
