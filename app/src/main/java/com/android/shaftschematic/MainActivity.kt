package com.android.shaftschematic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.android.shaftschematic.ui.shaft.ShaftRoute
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel

/**
 * Minimal host Activity.
 * All state and logic live in ShaftViewModel via ShaftRoute().
 */
class MainActivity : ComponentActivity() {
    private val myShaftViewModel: ShaftViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    ShaftRoute(vm = myShaftViewModel)
                }
            }
        }
    }
}
