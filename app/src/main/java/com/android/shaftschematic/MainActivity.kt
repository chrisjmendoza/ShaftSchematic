package com.android.shaftschematic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.android.shaftschematic.ui.screen.ShaftScreen
import com.android.shaftschematic.ui.screen.ShaftViewModel  // <-- fixed package

// If you’ve set up your own theme file:
// import com.android.shaftschematic.ui.theme.ShaftschematicTheme

class MainActivity : ComponentActivity() {

    // Activity-scoped ViewModel (requires activity-ktx)
    private val vm by viewModels<ShaftViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Replace with ShaftschematicTheme if you have one
            MaterialTheme {
                ShaftScreen(viewModel = vm)
            }
        }
    }
}
