// file: app/src/main/java/com/android/shaftschematic/MainActivity.kt
package com.android.shaftschematic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.shaftschematic.ui.nav.AppNav
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.ShaftViewModelFactory

/**
 * MainActivity
 *
 * Purpose
 * Single-activity host for the Compose UI. Creates the app-scoped [ShaftViewModel]
 * using our factory (required because the ViewModel extends AndroidViewModel)
 * and passes it to the navigation graph.
 *
 * Contract
 * - No business logic here.
 * - Do not perform file I/O or SAF work here.
 * - ViewModel is created once per Activity and handed to AppNav.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface {
                    // IMPORTANT: use the factory so AndroidViewModel receives Application
                    val vm: ShaftViewModel = viewModel(factory = ShaftViewModelFactory)
                    AppNav(vm = vm)
                }
            }
        }
    }
}
