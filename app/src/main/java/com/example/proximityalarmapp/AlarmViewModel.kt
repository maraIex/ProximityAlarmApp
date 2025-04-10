package com.example.proximityalarmapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import java.util.UUID

class AlarmViewModel(private val alarmRepository: AlarmRepository) : ViewModel() {
    val alarms: LiveData<List<Alarm>> = alarmRepository.getAllAlarms()

    val title = MutableLiveData<String>()
    val radius = MutableLiveData<Float>()
    val location = MutableLiveData<LatLong>()
    val isEnabled = MutableLiveData<Boolean>()
    val schedule = MutableLiveData<List<DayOfWeek>>()
    val oneTime = MutableLiveData<Boolean>()
    val weekdaysOnly = MutableLiveData<Boolean>()
    val weekendsOnly = MutableLiveData<Boolean>()
    val sound = MutableLiveData<String>()
    val vibration = MutableLiveData<Boolean>()
    val soundEnabled = MutableLiveData<Boolean>()
    val notification = MutableLiveData<Boolean>()
    val volume = MutableLiveData<Int>()

    fun updateTitle(newTitle: String) {
        title.value = newTitle
    }

    fun updateRadius(newRadius: Float) {
        radius.value = newRadius
    }

    fun updateLocation(latLong: LatLong) {
        location.value = latLong
    }

    fun updateisEnabled(enabled: Boolean) {
        isEnabled.value = enabled
    }

    fun updateSchedule(lst: List<DayOfWeek>) {
        schedule.value = lst
    }

    fun updateoneTime(onetime: Boolean) {
        oneTime.value = onetime
    }

    fun updateweekdaysOnly(weekdays: Boolean) {
        weekdaysOnly.value = weekdays
    }

    fun updateweekendsOnly(weekends: Boolean) {
        weekendsOnly.value = weekends
    }

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

    fun saveAlarm() {
        val alarm = Alarm(
            id = UUID.randomUUID().toString(),
            title = title.value ?: "Без названия",
            radius = radius.value ?: 100f,
            location = location.value ?: LatLong(0.0, 0.0),
            isEnabled = TODO(),
            schedule = TODO(),
            oneTime = TODO(),
            weekdaysOnly = TODO(),
            weekendsOnly = TODO(),
            sound = TODO(),
            vibration = TODO(),
            soundEnabled = TODO(),
            notification = TODO(),
            volume = TODO()
        )

        viewModelScope.launch {
            alarmRepository.addAlarm(alarm)
        }
    }
}
