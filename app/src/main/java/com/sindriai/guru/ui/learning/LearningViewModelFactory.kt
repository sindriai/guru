package com.sindriai.guru.ui.learning

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class LearningViewModelFactory(
    val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LearningViewModel::class.java)) {
            return LearningViewModel(context = context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}