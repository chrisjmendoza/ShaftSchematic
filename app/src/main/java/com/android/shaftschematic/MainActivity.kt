package com.android.shaftschematic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.android.shaftschematic.ui.screen.ShaftScreen
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.ShaftViewModelFactory

class MainActivity : ComponentActivity() {

    private val vm: ShaftViewModel by viewModels {
        ShaftViewModelFactory()   // now a no-arg factory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ShaftScreen(viewModel = vm)
            }
        }
    }
}
