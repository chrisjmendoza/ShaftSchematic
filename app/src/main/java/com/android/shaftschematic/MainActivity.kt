// file: app/src/main/java/com/android/shaftschematic/MainActivity.kt
package com.android.shaftschematic

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.shaftschematic.ui.adaptive.baseActivityOrientation
import com.android.shaftschematic.ui.nav.AppNav
import com.android.shaftschematic.ui.theme.ShaftSchematicTheme
import com.android.shaftschematic.ui.theme.isAppDarkTheme
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.ShaftViewModelFactory

/**
 * MainActivity
 *
 * Purpose
 * Single-activity host for the Compose UI. Creates the app-scoped [ShaftViewModel]
 * using our factory (required because the ViewModel extends AndroidViewModel)
 * and passes it to the navigation graph, wrapped in the user-selected theme
 * (Settings → Appearance).
 *
 * Contract
 * - No business logic here.
 * - Do not perform file I/O or SAF work here.
 * - ViewModel is created once per Activity and handed to AppNav.
 */
/**
 * The three-button navigation bar's scrims — `enableEdgeToEdge`'s own defaults, restated because
 * the library keeps them private. Gesture navigation draws no scrim.
 */
private val NAV_BAR_LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NAV_BAR_DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The manifest declares portrait, the phone lock. A manifest resource cannot vary by
        // configuration (lint ManifestResource: a values-sw600dp override there is simply never
        // read), so the per-device base orientation is applied here from the ONE resource the
        // rotation-unlocking screens restore. On a tablet this lifts the lock before the first
        // frame; on a phone it is the value already in force.
        requestedOrientation = baseActivityOrientation()
        enableEdgeToEdge()

        setContent {
            // IMPORTANT: use the factory so AndroidViewModel receives Application
            val vm: ShaftViewModel = viewModel(factory = ShaftViewModelFactory)
            val themeMode by vm.themeMode.collectAsState()
            val highContrast by vm.highContrast.collectAsState()
            // The bar icons follow the APP's theme, not the system's: the bare `enableEdgeToEdge()`
            // above follows the system night mode, which left dark clock and status icons on the
            // Dark and High-contrast Dark app bars (on-device report) and would do the reverse
            // under a Light app theme on a dark-mode device. Re-applied whenever the choice flips.
            val darkTheme = isAppDarkTheme(themeMode)
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(NAV_BAR_DARK_SCRIM)
                    } else {
                        SystemBarStyle.light(NAV_BAR_LIGHT_SCRIM, NAV_BAR_DARK_SCRIM)
                    },
                )
            }
            ShaftSchematicTheme(themeMode = themeMode, highContrast = highContrast) {
                Surface {
                    AppNav(vm = vm)
                }
            }
        }
    }
}
