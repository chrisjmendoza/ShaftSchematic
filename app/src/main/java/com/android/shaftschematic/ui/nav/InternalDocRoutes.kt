package com.android.shaftschematic.ui.nav

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
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
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.doc.ShaftDocCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.collectAsState
import java.io.File
import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import com.android.shaftschematic.doc.stripShaftDocExtension

/**
# InternalDocRoutes – open/save shaft docs *inside app storage*
 **Purpose**: UI for listing and saving shaft documents stored **internally** (app sandbox).
 **Contract**
 No SAF here. Uses [InternalStorage] (app-private files dir).
- Emits navigation completion via [onFinished].
- ViewModel owns JSON shape/version (importJson/exportJson).
- Names are unique to avoid conflicts with SAF routes.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenLocalDocumentRoute(
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
    var pendingRename by remember { mutableStateOf<String?>(null) }

    fun sanitizeUserBaseName(raw: String): String {
        val collapsed = raw.trim().replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty()) return ""

        return collapsed
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("[\\u0000-\\u001F]"), "")
            .trim()
    }

    // Load list once on enter (keeps behavior the same as your original)
    LaunchedEffect(Unit) { files = InternalStorage.list(ctx) }

    if (pendingDelete != null) {
        val name = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete saved shaft?") },
            text = { Text("Delete saved shaft ‘${stripShaftDocExtension(name)}’? This cannot be undone.") },
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
                                    message = "Could not delete ‘${stripShaftDocExtension(name)}’."
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

    if (pendingRename != null) {
        val fromName = pendingRename!!
        var value by remember(fromName) {
            val base = stripShaftDocExtension(fromName)
            mutableStateOf(TextFieldValue(base, selection = TextRange(0, base.length)))
        }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename saved shaft") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a new name. The file will be saved as $SHAFT_DOT_EXT.")
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        label = { Text("Name") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val sanitizedBase = sanitizeUserBaseName(value.text)
                        val toName = InternalStorage.normalizeShaftDocName(sanitizedBase)
                        if (toName == null) {
                            scope.launch { snackbarHostState.showSnackbar("Name cannot be blank.") }
                            return@TextButton
                        }

                        if (toName.equals(fromName, ignoreCase = true)) {
                            pendingRename = null
                            return@TextButton
                        }

                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                // Avoid overwrites.
                                if (InternalStorage.exists(ctx, toName)) return@withContext false
                                val raw = InternalStorage.load(ctx, fromName)
                                val decoded = ShaftDocCodec.decode(raw)
                                val base = stripShaftDocExtension(toName)
                                val updatedSpec = decoded.spec.copy(displayName = base)
                                val updatedDoc = ShaftDocCodec.ShaftDocV1(
                                    preferredUnit = decoded.preferredUnit ?: UnitSystem.INCHES,
                                    unitLocked = decoded.unitLocked,
                                    jobNumber = decoded.jobNumber,
                                    customer = decoded.customer,
                                    vessel = decoded.vessel,
                                    shaftPosition = decoded.shaftPosition,
                                    notes = decoded.notes,
                                    spec = updatedSpec,
                                )
                                if (!InternalStorage.rename(ctx, fromName, toName)) return@withContext false
                                InternalStorage.save(ctx, toName, ShaftDocCodec.encodeV1(updatedDoc))
                                true
                            }
                            if (ok) {
                                files = InternalStorage.list(ctx)
                                pendingRename = null
                            } else {
                                snackbarHostState.showSnackbar(
                                    message = "Could not rename to ‘${stripShaftDocExtension(toName)}’."
                                )
                            }
                        }
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) { Text("Cancel") }
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
                                    vm.importJson(text, displayNameOverride = stripShaftDocExtension(name))
                                    onFinished()
                                }
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = stripShaftDocExtension(name),
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
                                        text = { Text("Rename") },
                                        onClick = {
                                            menuOpen = false
                                            pendingRename = name
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Edit, contentDescription = null)
                                        }
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
                                                    selectedSaveName = stripShaftDocExtension(name),
                                                    attachments = attachments
                                                )

                                                try {
                                                    ctx.startActivity(Intent.createChooser(intent, "Send Feedback"))
                                                } catch (_: ActivityNotFoundException) {
                                                    snackbarHostState.showSnackbar("No email app found.")
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Email, contentDescription = null)
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
    val spec by vm.spec.collectAsState()

    var existingFiles by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) { existingFiles = InternalStorage.list(ctx) }

    var name by remember(spec.displayName, jobNumber, customer, vessel, shaftPosition) {
        val persisted = spec.displayName?.trim().orEmpty().ifBlank { null }
        val default = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val positionSuffix = shaftPosition.printableLabelOrNull()
        val suggested = DocumentNaming.suggestedBaseName(
            jobNumber = jobNumber,
            customer = customer,
            vessel = vessel,
            suffix = positionSuffix
        )
        mutableStateOf(TextFieldValue(persisted ?: suggested ?: "Shaft_$default"))
    }
    var error by remember { mutableStateOf<String?>(null) }

    val normalizedName = remember(name.text) { InternalStorage.normalizeShaftDocName(name.text) }
    val willOverwrite = remember(normalizedName, existingFiles) {
        val base = normalizedName?.let(::stripShaftDocExtension)
        base != null && existingFiles.any { stripShaftDocExtension(it).equals(base, ignoreCase = true) }
    }
    var pendingOverwrite by remember { mutableStateOf<String?>(null) }

    if (pendingOverwrite != null) {
        val file = pendingOverwrite!!
        AlertDialog(
            onDismissRequest = { pendingOverwrite = null },
            title = { Text("Overwrite existing save?") },
            text = { Text("A saved shaft named ‘${stripShaftDocExtension(file)}’ already exists. Overwrite it?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val base = stripShaftDocExtension(file)
                            vm.setDisplayName(base)
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
            val existingBaseNames = remember(existingFiles) {
                existingFiles.map(::stripShaftDocExtension).distinctBy { it.lowercase() }
            }
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
                    val normalized = InternalStorage.normalizeShaftDocName(name.text)
                    if (normalized == null) {
                        error = "Enter a file name."
                    } else {
                        val base = stripShaftDocExtension(normalized)
                        val targetName = base + SHAFT_DOT_EXT
                        if (existingFiles.any { stripShaftDocExtension(it).equals(base, ignoreCase = true) }) {
                            pendingOverwrite = targetName
                        } else {
                            scope.launch {
                                vm.setDisplayName(base)
                                withContext(Dispatchers.IO) {
                                    InternalStorage.save(ctx, targetName, vm.exportJson())
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
