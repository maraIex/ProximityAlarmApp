package com.example.proximityalarmapp

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.mapsforge.core.model.LatLong

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val id: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val title: String,
    val isEnabled: Boolean,
    val schedule: String,
    val oneTime: Boolean,
    val weekdaysOnly: Boolean,
    val weekendsOnly: Boolean,
    val vibration: Boolean,
    val soundEnabled: Boolean,
    val notification: Boolean,
    val volume: Int
) {
    fun toAlarm(): Alarm {
        return Alarm(
            id = id,
            location = LatLong(latitude, longitude),
            radius = radius,
            title = title,
            isEnabled = isEnabled,
            schedule = parseSchedule(schedule),
            oneTime = oneTime,
            weekdaysOnly = weekdaysOnly,
            weekendsOnly = weekendsOnly,
            vibration = vibration,
            soundEnabled = soundEnabled,
            notification = notification,
            volume = volume
        )
    }

    companion object {
        fun fromAlarm(alarm: Alarm): AlarmEntity {
            return AlarmEntity(
                id = alarm.id,
                latitude = alarm.location.latitude,
                longitude = alarm.location.longitude,
                radius = alarm.radius,
                title = alarm.title,
                isEnabled = alarm.isEnabled,
                schedule = alarm.schedule.joinToString(",") { it.name },
                oneTime = alarm.oneTime,
                weekdaysOnly = alarm.weekdaysOnly,
                weekendsOnly = alarm.weekendsOnly,
                vibration = alarm.vibration,
                soundEnabled = alarm.soundEnabled,
                notification = alarm.notification,
                volume = alarm.volume
            )
        }

        private fun parseSchedule(scheduleString: String): List<DayOfWeek> {
            return scheduleString.split(",").mapNotNull { name ->
                try {
                    DayOfWeek.valueOf(name)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }
    }
}