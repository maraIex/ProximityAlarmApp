package com.example.proximityalarmapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AlarmViewModelFactory : ViewModelProvider.Factory {
    override fun <T: ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmViewModel::class.java)) {
            @Suppress("UNCHECKEd_CAST")
            return AlarmViewModel(AlarmRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}