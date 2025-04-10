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

    // Флаг чтобы понять ставили мы метку или нет
    val hasLocation = MutableLiveData<Boolean>(false)

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

    fun updateSound(new_sound: String) {
        sound.value = new_sound
    }

    fun updateVibration(new_vibration: Boolean) {
        vibration.value = new_vibration
    }

    fun updateSoundEnabled(sound_enabled: Boolean) {
        soundEnabled.value = sound_enabled
    }

    fun updateNotification(new_notification: Boolean) {
        notification.value = new_notification
    }

    fun updateVolume(new_volume: Int) {
        volume.value = new_volume
    }

    fun updateHasLocation(hasLoc: Boolean) {
        hasLocation.value = hasLoc
    }

    // Добавление будильника
    fun addAlarm() {
        val alarm = Alarm(
            id = UUID.randomUUID().toString(),
            location = location.value ?: LatLong(0.0, 0.0),
            radius = radius.value ?: 100f,
            title = title.value ?: "Без названия",
            isEnabled = isEnabled.value ?: true,
            schedule = (schedule.value ?: listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            )) as List<DayOfWeek>,
            oneTime = oneTime.value ?: true,
            weekdaysOnly = weekdaysOnly.value ?: true,
            weekendsOnly = weekendsOnly.value ?: false,
            sound = sound.value ?: "Standard",
            vibration = vibration.value ?: true,
            soundEnabled = soundEnabled.value ?: true,
            notification = notification.value ?: true,
            volume = volume.value ?: 50,
        )

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

    fun saveAlarms() {
        viewModelScope.launch {
            alarmRepository.saveAlarms()
        }
    }
}
