package com.android.shaftschematic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.android.shaftschematic.ui.screen.ShaftScreen
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.ShaftViewModelFactory

class MainActivity : ComponentActivity() {

    // Activity-scoped VM using your factory
    private val vm: ShaftViewModel by viewModels { ShaftViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    ShaftScreen(viewModel = vm)   // <-- pass the VM
                }
            }
        }
    }
}
