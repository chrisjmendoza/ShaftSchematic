package com.android.shaftschematic.ui.screen

import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.filterDecimalPermissive
import com.android.shaftschematic.model.MM_PER_IN
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftScreen(viewModel: ShaftViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val spec by viewModel.spec.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var showGrid by rememberSaveable { mutableStateOf(false) }

    // Keep the last-saved PDF URI locally (avoid unresolved ViewModel API).
    var lastPdfUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // SAF “Save As…” for PDF
    val exportLauncher = rememberLauncherForActivityResult(CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext<Unit>(Dispatchers.IO) {
                    val os: OutputStream = ctx.contentResolver.openOutputStream(uri)
                        ?: error("Could not open output stream.")

                    val pdf = PdfDocument()
                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create()
                        val page = pdf.startPage(pageInfo)
                        val canvas = page.canvas
                        val paint = android.graphics.Paint().apply { textSize = 16f }
                        canvas.drawText("ShaftSchematic PDF (preview export)", 48f, 48f, paint)
                        pdf.finishPage(page)
                        pdf.writeTo(os)
                    } finally {
                        try { pdf.close() } catch (_: Exception) {}
                        try { os.close() } catch (_: Exception) {}
                    }
                }
                lastPdfUri = uri
                snackbar.showSnackbar("PDF saved.")
            } catch (e: Exception) {
                snackbar.showSnackbar("Export failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shaft Schematic") },
                actions = {
                    IconButton(onClick = { showGrid = !showGrid }) {
                        Icon(Icons.Filled.GridOn, contentDescription = if (showGrid) "Hide grid" else "Show grid")
                    }
                    // Units dropdown
                    var unitsOpen by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { unitsOpen = true }) {
                            Text("Units: ${unit.displayName}")
                        }
                        DropdownMenu(expanded = unitsOpen, onDismissRequest = { unitsOpen = false }) {
                            UnitSystem.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.displayName) },
                                    onClick = { viewModel.setUnit(opt); unitsOpen = false }
                                )
                            }
                        }
                    }
                    // Export PDF
                    IconButton(onClick = { exportLauncher.launch("shaft_drawing.pdf") }) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    // View last PDF (uses local remembered URI)
                    IconButton(onClick = {
                        val uri = lastPdfUri
                        if (uri == null) {
                            scope.launch { snackbar.showSnackbar("No PDF exported this session yet.") }
                        } else {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                ctx.startActivity(intent)
                            } catch (_: Exception) {
                                scope.launch { snackbar.showSnackbar("No app found to view PDFs.") }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Visibility, contentDescription = "View last PDF")
                    }
                    // Overflow: Clear all
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, null) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Clear all") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.clearAll()
                                    scope.launch { snackbar.showSnackbar("Cleared shaft spec") }
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = { AddFab(viewModel) },
        floatingActionButtonPosition = FabPosition.End,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Preview on top
            item {
                ShaftDrawing(
                    spec = spec,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }

            // Basics – overall length first
            item { SectionTitle("Basics") }
            item {
                NumberField(
                    fieldKey = "overallLength",
                    label = "Overall length (${unit.displayName})",
                    unit = unit,
                    mmValue = spec.overallLengthMm.toDouble(),
                    format = { mm -> viewModel.formatInCurrentUnit(mm.toFloat(), 3) },
                    onUserChange = { s -> viewModel.setOverallLength(s) }
                )
            }

            item {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            // Bodies
            if (spec.bodies.isNotEmpty()) {
                item { SectionTitle("Bodies") }
                itemsIndexed(spec.bodies, key = { _, it -> it.id }) { idx, b ->
                    ComponentCard(title = "Body", onRemove = { viewModel.removeBodyById(b.id) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(
                                fieldKey = "body_${b.id}_start",
                                label = "Start from AFT (${unit.displayName})",
                                unit = unit,
                                mmValue = b.startFromAftMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setBody(idx, start = s) },
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                fieldKey = "body_${b.id}_len",
                                label = "Length (${unit.displayName})",
                                unit = unit,
                                mmValue = b.lengthMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setBody(idx, length = s) },
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                fieldKey = "body_${b.id}_dia",
                                label = "Diameter (${unit.displayName})",
                                unit = unit,
                                mmValue = b.diaMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setBody(idx, dia = s) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Tapers
            if (spec.tapers.isNotEmpty()) {
                item { SectionTitle("Tapers") }
                itemsIndexed(spec.tapers, key = { _, it -> it.id }) { idx, t ->
                    ComponentCard(title = "Taper", onRemove = { viewModel.removeTaperById(t.id) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(
                                fieldKey = "taper_${t.id}_start",
                                label = "Start from AFT (${unit.displayName})",
                                unit = unit,
                                mmValue = t.startFromAftMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setTaper(idx, start = s) },
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                fieldKey = "taper_${t.id}_len",
                                label = "Length (${unit.displayName})",
                                unit = unit,
                                mmValue = t.lengthMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setTaper(idx, length = s) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(
                                fieldKey = "taper_${t.id}_startDia",
                                label = "Start Ø (${unit.displayName})",
                                unit = unit,
                                mmValue = t.startDiaMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setTaper(idx, startDia = s) },
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                fieldKey = "taper_${t.id}_endDia",
                                label = "End Ø (${unit.displayName})",
                                unit = unit,
                                mmValue = t.endDiaMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setTaper(idx, endDia = s) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Threads
            if (spec.threads.isNotEmpty()) {
                item { SectionTitle("Threads") }
                itemsIndexed(spec.threads, key = { _, it -> it.id }) { idx, th ->
                    ComponentCard(title = "Thread", onRemove = { viewModel.removeThreadById(th.id) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(
                                fieldKey = "thread_${th.id}_start",
                                label = "Start from AFT (${unit.displayName})",
                                unit = unit,
                                mmValue = th.startFromAftMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setThread(idx, start = s) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(
                                fieldKey = "thread_${th.id}_major",
                                label = "Major Ø (${unit.displayName})",
                                unit = unit,
                                mmValue = th.majorDiaMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setThread(idx, majorDia = s) },
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                fieldKey = "thread_${th.id}_pitch",
                                label = "Pitch (${unit.displayName})",
                                unit = unit,
                                mmValue = th.pitchMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setThread(idx, pitch = s) },
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                fieldKey = "thread_${th.id}_len",
                                label = "Length (${unit.displayName})",
                                unit = unit,
                                mmValue = th.lengthMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setThread(idx, length = s) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Liners
            if (spec.liners.isNotEmpty()) {
                item { SectionTitle("Liners") }
                itemsIndexed(spec.liners, key = { _, it -> it.id }) { idx, ln ->
                    ComponentCard(title = "Liner", onRemove = { viewModel.removeLinerById(ln.id) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(
                                fieldKey = "liner_${ln.id}_start",
                                label = "Start from AFT (${unit.displayName})",
                                unit = unit,
                                mmValue = ln.startFromAftMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setLiner(idx, start = s) },
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                fieldKey = "liner_${ln.id}_len",
                                label = "Length (${unit.displayName})",
                                unit = unit,
                                mmValue = ln.lengthMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setLiner(idx, length = s) },
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                fieldKey = "liner_${ln.id}_od",
                                label = "Outer Ø (${unit.displayName})",
                                unit = unit,
                                mmValue = ln.odMm.toDouble(),
                                format = { viewModel.formatInCurrentUnit(it.toFloat(), 3) },
                                onUserChange = { s -> viewModel.setLiner(idx, od = s) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ==============================  FAB (add menu)  ============================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFab(viewModel: ShaftViewModel) {
    var open by remember { mutableStateOf(false) }
    FloatingActionButton(onClick = { open = true }) {
        Icon(Icons.Filled.Add, contentDescription = "Add")
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Add component", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = { open = false; viewModel.addBodySegment() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Body") }
                Button(
                    onClick = { open = false; viewModel.addTaper() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Taper") }
                Button(
                    onClick = { open = false; viewModel.addThread() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Thread") }
                Button(
                    onClick = { open = false; viewModel.addLiner() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Liner") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/* ==============================  UI helpers  ============================== */

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun ComponentCard(
    title: String,
    onRemove: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
            }
            content()
        }
    }
}

/**
 * Cursor-safe numeric text field.
 * - **No mid-typing rewrites**: we only reformat when focus is lost, or when the value/unit changes
 *   from elsewhere *and* the user is not actively editing.
 * - Raw text is forwarded to the ViewModel for parsing (in current unit).
 */
@Composable
private fun NumberField(
    fieldKey: String = "",
    label: String,
    unit: UnitSystem,
    mmValue: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    onUserChange: (String) -> Unit
) {
    // Stable key for this field's saved state
    val saveKey = remember(fieldKey, label) { if (fieldKey.isNotBlank()) fieldKey else label }

    var isEditing by rememberSaveable(saveKey + "_editing") { mutableStateOf(false) }
    var tf by rememberSaveable(saveKey, unit, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(format(mmValue)))
    }

    // Keep UI in sync with external updates ONLY when not editing
    LaunchedEffect(mmValue, unit, isEditing) {
        if (!isEditing) {
            val safe = if (mmValue.isFinite()) mmValue else 0.0
            val t = format(safe)
            if (tf.text != t) {
                tf = tf.copy(text = t, selection = TextRange(t.length))
            }
        }
    }

    OutlinedTextField(
        value = tf,
        onValueChange = { v ->
            // Permissive filtering to avoid cursor jumps; no formatting while typing
            val sanitized = filterDecimalPermissive(v.text)
            // keep caret at end (simple + stable); if you want smarter caret, track deltas
            tf = v.copy(text = sanitized, selection = TextRange(sanitized.length))
            onUserChange(sanitized)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { f ->
                isEditing = f.isFocused
                if (!f.isFocused) {
                    // On blur, snap to the formatted canonical value
                    val t = format(mmValue.takeIf { it.isFinite() } ?: 0.0)
                    if (tf.text != t) {
                        tf = tf.copy(text = t, selection = TextRange(t.length))
                    }
                }
            }
    )
}
