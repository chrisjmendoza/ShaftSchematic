package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

/**
 * ShaftViewModelFactory
 *
 * Purpose
 * Provide the Application instance to ShaftViewModel(AndroidViewModel).
 * Keeps a single construction path and is easy to mock in tests.
 */
object ShaftViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(ShaftViewModel::class.java)) {
            "Unsupported ViewModel type: $modelClass"
        }
        val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
            ?: error("Application was not provided in CreationExtras")
        return ShaftViewModel(app as Application) as T
    }
}
