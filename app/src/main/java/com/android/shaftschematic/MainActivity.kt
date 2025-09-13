package com.android.shaftschematic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.android.shaftschematic.ui.screens.ShaftScreen
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel

// If you’ve set up your own theme file:
// import com.android.shaftschematic.ui.theme.ShaftschematicTheme

class MainActivity : ComponentActivity() {
    // Create the ViewModel scoped to this Activity
    private val vm by viewModels<ShaftViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // If you generated a custom theme, wrap with that instead of plain MaterialTheme
            MaterialTheme {
                ShaftScreen(viewModel = vm)
            }
        }
    }
}