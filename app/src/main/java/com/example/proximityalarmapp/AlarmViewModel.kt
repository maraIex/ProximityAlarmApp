package com.example.proximityalarmapp

import android.app.Application
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import java.util.UUID

class AlarmViewModel(application: Application, private val alarmRepository: AlarmRepository) : AndroidViewModel(application) {
    val alarms: LiveData<List<Alarm>> = alarmRepository.alarms

    // Флаг чтобы понять ставили мы метку или нет
    val hasLocation = MutableLiveData<Boolean>(false)

    val title = MutableLiveData<String>("Без названия")
    val radius = MutableLiveData<Float>(100f)
    val location = MutableLiveData<LatLong>(LatLong(0.0, 0.0))
    val isEnabled = MutableLiveData<Boolean>(false)
    val schedule = MutableLiveData<List<DayOfWeek>>(emptyList())
    val oneTime = MutableLiveData<Boolean>(true)
    val weekdaysOnly = MutableLiveData<Boolean>(false)
    val weekendsOnly = MutableLiveData<Boolean>(false)
    val vibration = MutableLiveData<Boolean>(true)
    val soundEnabled = MutableLiveData<Boolean>(true)
    val notification = MutableLiveData<Boolean>(true)
    val volume = MutableLiveData<Int>(50)

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

    fun createStateBundle(): Bundle {
        return Bundle().apply {
            putString("title", title.value ?: "Без названия")
            putFloat("radius", radius.value ?: 100f)

            putBoolean("oneTime", oneTime.value ?: true)
            putBoolean("weekdays", weekdaysOnly.value ?: false)
            putBoolean("weekends", weekendsOnly.value ?: false)

            putStringArray("schedule", schedule.value?.map { it.name }?.toTypedArray() ?: emptyArray())

            putBoolean("vibration", vibration.value ?: true)
            putBoolean("notification", notification.value ?: true)
            putBoolean("soundEnabled", soundEnabled.value ?: true)
            putInt("volume", volume.value ?: 50)
        }
    }

    fun restoreState(bundle: Bundle) {
        title.value = bundle.getString("title", "Без названия")
        radius.value = bundle.getFloat("radius", 100f)

        oneTime.value = bundle.getBoolean("oneTime", true)
        weekdaysOnly.value = bundle.getBoolean("weekdays", false)
        weekendsOnly.value = bundle.getBoolean("weekends", false)

        schedule.value = bundle.getStringArray("schedule")
            ?.mapNotNull { name ->
                try {
                    DayOfWeek.valueOf(name)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } ?: emptyList()

        vibration.value = bundle.getBoolean("vibration", true)
        notification.value = bundle.getBoolean("notification", true)
        soundEnabled.value = bundle.getBoolean("soundEnabled", true)
        volume.value = bundle.getInt("volume", 50)
    }

    fun saveMarkerPosition(latLong: LatLong) {
        updateLocation(latLong)
        updateHasLocation(true)
    }

    fun clearMarkerPosition() {
        updateLocation(LatLong(0.0, 0.0))
        updateHasLocation(false)
    }

    fun clearAlarmData() {
        clearMarkerPosition()
        updateHasLocation(false)
        updateTitle("Без названия")
        updateRadius(100f)
        updateisEnabled(false)
        updateSchedule(emptyList())
        updateoneTime(true)
        updateweekdaysOnly(false)
        updateweekendsOnly(false)
        updateVibration(true)
        updateSoundEnabled(true)
        updateNotification(true)
        updateVolume(50)
    }

    fun addAlarm(alarm: Alarm) {
        viewModelScope.launch {
            alarmRepository.addAlarm(alarm)
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            alarmRepository.deleteAlarm(alarm)
        }
    }

    fun getAlarm(alarmId: String): LiveData<Alarm?> {
        val result = MutableLiveData<Alarm?>()
        viewModelScope.launch {
            val alarmEntity = alarmRepository.getAlarmById(alarmId)
            result.postValue(alarmEntity?.toAlarm())
        }
        return result
    }
}
