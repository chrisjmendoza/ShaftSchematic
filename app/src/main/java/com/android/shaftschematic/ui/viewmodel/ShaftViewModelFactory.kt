package com.android.shaftschematic.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ShaftViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShaftViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShaftViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
