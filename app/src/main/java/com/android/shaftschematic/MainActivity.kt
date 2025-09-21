package com.android.shaftschematic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.android.shaftschematic.ui.shaft.ShaftRoute

/**
 * Minimal host Activity.
 * All state and logic live in ShaftViewModel via ShaftRoute().
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    ShaftRoute()
                }
            }
        }
    }
}
