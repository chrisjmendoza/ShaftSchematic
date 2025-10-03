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

/**
 * MainActivity
 *
 * Purpose
 * Single-activity host for Compose. Entrypoint renders AppNav.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    val vm: ShaftViewModel = viewModel()
                    AppNav(vm = vm)
                }
            }
        }
    }
}
