// file: app/src/main/java/com/android/shaftschematic/ui/screen/ShaftEditorRoute.kt
package com.android.shaftschematic.ui.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.ui.screen.ShaftRoute
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.FeedbackIntentFactory
import kotlinx.coroutines.launch

/**
 * ShaftEditorRoute
 *
 * Purpose
 * Wraps the editor body with a top app bar for navigation and actions.
 *
 * Contract
 * - This route owns the app bar only.
 * - Save/Open/PDF actions delegate to navigation; no I/O here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftEditorRoute(
    vm: ShaftViewModel,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSettings: () -> Unit,
    onExportPdf: () -> Unit
) {
    val canUndoDeletes by vm.canUndoDeletes.collectAsState()
    val canRedoDeletes by vm.canRedoDeletes.collectAsState()

    val unit by vm.unit.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Surface {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 0.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Shaft Editor", style = MaterialTheme.typography.titleLarge)
                    }
                }

                TopAppBar(
                    title = { },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    actions = {
                        IconButton(
                            onClick = {
                                val intent = FeedbackIntentFactory.create(
                                    context = ctx,
                                    screen = "Editor",
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
                        ) {
                            Icon(Icons.Filled.Email, contentDescription = "Send Feedback")
                        }

                        // Undo / Redo for delete history (multi-step)
                        IconButton(
                            onClick = { vm.undoLastDelete() },
                            enabled = canUndoDeletes
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo delete")
                        }
                        IconButton(
                            onClick = { vm.redoLastDelete() },
                            enabled = canRedoDeletes
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo delete")
                        }

                        // Existing actions
                        IconButton(onClick = onSave) {
                            Icon(Icons.Filled.Save, contentDescription = "Save JSON")
                        }
                        IconButton(onClick = onExportPdf) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Export PDF")
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            ShaftRoute(vm = vm)
        }
    }
}
