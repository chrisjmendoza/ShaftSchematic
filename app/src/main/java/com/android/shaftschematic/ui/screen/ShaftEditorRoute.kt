// app/src/main/java/com/android/shaftschematic/ui/screen/ShaftEditorRoute.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.shaftschematic.ui.shaft.ShaftRoute
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel

/**
 * Wraps the legacy ShaftRoute with a top app bar.
 * Actions: Save (internal JSON) + Export PDF (SAF).
 */
@OptIn(ExperimentalMaterial3Api::class)
// app/src/main/java/com/android/shaftschematic/ui/screen/ShaftEditorRoute.kt
@Composable
fun ShaftEditorRoute(
    vm: ShaftViewModel,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSettings: () -> Unit,
    onExportPdf: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shaft Editor") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = onSave) { Icon(Icons.Filled.Save, "Save JSON") }
                    IconButton(onClick = onExportPdf) { Icon(Icons.Outlined.PictureAsPdf, "Export PDF") }
                }
            )
        }
    ) { pad ->                         // ✅ get outer scaffold padding
        Box(Modifier.padding(pad)) {   // ✅ apply it to your editor body
            ShaftRoute(vm = vm)        //    (ShaftRoute doesn’t take a modifier)
        }
    }
}

