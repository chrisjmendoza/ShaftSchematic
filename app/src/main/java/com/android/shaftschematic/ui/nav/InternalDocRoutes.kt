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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.android.shaftschematic.io.InternalStorage
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.FeedbackIntentFactory
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
- No SAF here. Uses [InternalStorage] (app-private files dir).
- Emits navigation completion via [onFinished].
- ViewModel owns JSON shape/version (importJson/exportJson).
- Names are unique to avoid conflicts with SAF routes.
 */

/* ───────────────────── OPEN (from app storage) ───────────────────── */

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
                                ctx.startActivity(Intent.createChooser(intent, "Send feedback"))
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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name.removeSuffix(".json"))
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
                                                    ctx.startActivity(Intent.createChooser(intent, "Send feedback"))
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
                    Divider()
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

    var name by remember {
        val default = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        mutableStateOf(TextFieldValue("Shaft_$default.json"))
    }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Save drawing") }) }) { pad ->
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
                label = { Text("File name (.json)") },
                isError = error != null,
                supportingText = { if (error != null) Text(error!!) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val n = name.text.trim()
                    when {
                        n.isEmpty() -> error = "Enter a file name."
                        !n.endsWith(".json", ignoreCase = true) -> error = "Name must end with .json"
                        else -> scope.launch {
                            withContext(Dispatchers.IO) {
                                InternalStorage.save(ctx, n, vm.exportJson())
                            }
                            onFinished()
                        }
                    }
                }) { Text("Save") }
                OutlinedButton(onClick = onFinished) { Text("Cancel") }
            }
        }
    }
}
