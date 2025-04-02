package com.example.proximityalarmapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong

class AlarmViewModel(private val alarmRepository: AlarmRepository) : ViewModel() {
    val alarms: LiveData<List<Alarm>> = alarmRepository.getAllAlarms()

    // Добавление будильника
    fun addAlarm(alarm: Alarm) {
        viewModelScope.launch {
            alarmRepository.addAlarm(alarm)
        }
    }

    fun getAllAlarms(): LiveData<List<Alarm>> {
        return AlarmRepository.getAllAlarms()
    }

    // Поиск будильника по координатам
    fun findAlarmAtLocation(location: LatLong): Alarm? {
        return alarmRepository.getAllAlarms().value?.firstOrNull { alarm ->
            alarm.location.sphericalDistance(location) < (alarm.radius / 111_320.0)
            // Конвертируем метры в градусы (примерно)
        }
    }
}
