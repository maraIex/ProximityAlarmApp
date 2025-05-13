package com.example.proximityalarmapp

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

class ProximityAlarm : Application() {
    // Используем lazy для отложенной инициализации
    val appContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
    }
}

class AppContainer(application: Application) {
    // Инициализируем Repository через lazy
    val alarmRepository: AlarmRepository by lazy {
        AlarmRepository.getInstance(application)
    }

    val alarmViewModel: AlarmViewModel by lazy {
        AlarmViewModel(application, alarmRepository)
    }
}