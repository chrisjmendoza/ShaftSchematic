package com.android.shaftschematic.ui.nav

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.android.shaftschematic.io.InternalStorage
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.FeedbackIntentFactory
import com.android.shaftschematic.util.Achievements
import com.android.shaftschematic.util.DocumentNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.collectAsState
import java.io.File

/**
# InternalDocRoutes – open/save JSON *inside app storage*
 **Purpose**: UI for listing and saving JSON drawings stored **internally** (app sandbox).
 **Contract**
 No SAF here. Uses [InternalStorage] (app-private files dir).
- Emits navigation completion via [onFinished].
- ViewModel owns JSON shape/version (importJson/exportJson).
- Names are unique to avoid conflicts with SAF routes.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenLocalDocumentRoute(               // ← renamed (no clash with SAF)
    nav: NavController,
    vm: ShaftViewModel,
    onFinished: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(listOf<String>()) }
    val unit by vm.unit.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    // Load list once on enter (keeps behavior the same as your original)
    LaunchedEffect(Unit) { files = InternalStorage.list(ctx) }

    if (pendingDelete != null) {
        val name = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete saved shaft?") },
            text = { Text("Delete saved shaft ‘${name.removeSuffix(".json")}’? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                InternalStorage.delete(ctx, name)
                            }
                            if (ok) {
                                files = InternalStorage.list(ctx)
                            } else {
                                snackbarHostState.showSnackbar(
                                    message = "Could not delete ‘${name.removeSuffix(".json")}’."
                                )
                            }
                            pendingDelete = null
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open drawing") },
                actions = {
                    TextButton(
                        onClick = {
                            val intent = FeedbackIntentFactory.create(
                                context = ctx,
                                screen = "Open/Saved",
                                unit = unit,
                                selectedSaveName = null,
                                attachments = emptyList()
                            )
                            try {
                                ctx.startActivity(Intent.createChooser(intent, "Send Feedback"))
                            } catch (_: ActivityNotFoundException) {
                                scope.launch { snackbarHostState.showSnackbar("No email app found.") }
                            }
                        }
                    ) { Text("Send Feedback") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
        ) {
            if (files.isEmpty()) {
                item { Text("No saved drawings yet.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(files) { name ->
                    var menuOpen by remember(name) { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    val text = withContext(Dispatchers.IO) {
                                        InternalStorage.load(ctx, name)
                                    }
                                    vm.importJson(text)
                                    onFinished()
                                }
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = name.removeSuffix(".json"),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Open", color = MaterialTheme.colorScheme.primary)
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Send Feedback") },
                                        onClick = {
                                            menuOpen = false
                                            scope.launch {
                                                val attachments = withContext(Dispatchers.IO) {
                                                    val dir = InternalStorage.dir(ctx.filesDir)
                                                    val file = File(dir, name)
                                                    val out = mutableListOf<android.net.Uri>()
                                                    if (file.exists()) {
                                                        out += FeedbackIntentFactory.uriForFile(ctx, file)
                                                    }
                                                    out
                                                }

                                                val intent = FeedbackIntentFactory.create(
                                                    context = ctx,
                                                    screen = "Open/Saved",
                                                    unit = unit,
                                                    selectedSaveName = name.removeSuffix(".json"),
                                                    attachments = attachments
                                                )

                                                try {
                                                    ctx.startActivity(Intent.createChooser(intent, "Send Feedback"))
                                                } catch (_: ActivityNotFoundException) {
                                                    snackbarHostState.showSnackbar("No email app found.")
                                                }
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            menuOpen = false
                                            pendingDelete = name
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Delete, contentDescription = null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/* ───────────────────── SAVE (to app storage) ───────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveLocalDocumentRoute(               // ← renamed (no clash with SAF)
    nav: NavController,
    vm: ShaftViewModel,
    onFinished: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val jobNumber by vm.jobNumber.collectAsState()
    val customer by vm.customer.collectAsState()
    val vessel by vm.vessel.collectAsState()
    val shaftPosition by vm.shaftPosition.collectAsState()

    var existingFiles by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) { existingFiles = InternalStorage.list(ctx) }

    var name by remember {
        val default = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val positionSuffix = shaftPosition.printableLabelOrNull()
        val suggested = DocumentNaming.suggestedBaseName(
            jobNumber = jobNumber,
            customer = customer,
            vessel = vessel,
            suffix = positionSuffix
        )
        mutableStateOf(TextFieldValue(suggested ?: "Shaft_$default"))
    }
    var error by remember { mutableStateOf<String?>(null) }

    val normalizedName = remember(name.text) { InternalStorage.normalizeJsonName(name.text) }
    val willOverwrite = remember(normalizedName, existingFiles) {
        normalizedName != null && existingFiles.any { it.equals(normalizedName, ignoreCase = true) }
    }
    var pendingOverwrite by remember { mutableStateOf<String?>(null) }

    if (pendingOverwrite != null) {
        val file = pendingOverwrite!!
        AlertDialog(
            onDismissRequest = { pendingOverwrite = null },
            title = { Text("Overwrite existing save?") },
            text = { Text("A saved shaft named ‘${file.removeSuffix(".json")}’ already exists. Overwrite it?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                InternalStorage.save(ctx, file, vm.exportJson())
                            }
                            vm.unlockAchievement(Achievements.Id.FIRST_SAVE)
                            pendingOverwrite = null
                            onFinished()
                        }
                    }
                ) { Text("Overwrite") }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverwrite = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Save Drawing") }) }) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("File name") },
                isError = error != null,
                supportingText = {
                    when {
                        error != null -> Text(error!!)
                        willOverwrite -> Text("Matches an existing save. You’ll be asked to overwrite.")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Existing saves list: shown under the input and filtered as the user types.
            val query = name.text.trim()
            val existingBaseNames = remember(existingFiles) { existingFiles.map { it.removeSuffix(".json") } }
            val filtered = remember(existingBaseNames, query) {
                if (query.isBlank()) {
                    existingBaseNames
                } else {
                    existingBaseNames.filter { it.contains(query, ignoreCase = true) }
                }
            }
            if (existingBaseNames.isNotEmpty()) {
                Text("Existing saves", style = MaterialTheme.typography.titleSmall)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                ) {
                    items(filtered, key = { it }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    name = TextFieldValue(
                                        text = item,
                                        selection = TextRange(item.length)
                                    )
                                    error = null
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item)
                            Text("Use", color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider()
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val normalized = InternalStorage.normalizeJsonName(name.text)
                    if (normalized == null) {
                        error = "Enter a file name."
                    } else {
                        if (InternalStorage.exists(ctx, normalized)) {
                            pendingOverwrite = normalized
                        } else {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    InternalStorage.save(ctx, normalized, vm.exportJson())
                                }
                                vm.unlockAchievement(Achievements.Id.FIRST_SAVE)
                                onFinished()
                            }
                        }
                    }
                }) { Text("Save") }
                OutlinedButton(onClick = onFinished) { Text("Cancel") }
            }
        }
    }
}
