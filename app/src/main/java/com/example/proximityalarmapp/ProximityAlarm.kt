package com.example.proximityalarmapp

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

class ProximityAlarm : Application() {
    // Используем lazy для отложенной инициализации
    val appContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val alarmRepository: AlarmRepository = AlarmRepository
    val alarmViewModel: AlarmViewModel by lazy {
        AlarmViewModel(application, alarmRepository)
    }
}