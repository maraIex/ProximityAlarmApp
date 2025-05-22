package com.example.proximityalarmapp

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmRepository private constructor(context: Context) {
    private val alarmDao = AppDatabase.getDatabase(context).alarmDao()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    val alarms: LiveData<List<Alarm>> = alarmDao.getAllAlarms().map { entities ->
        entities.map { it.toAlarm() }
    }

    companion object {
        @Volatile
        private var INSTANCE: AlarmRepository? = null

        fun getInstance(context: Context): AlarmRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlarmRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun addAlarm(alarm: Alarm) {
        coroutineScope.launch {
            alarmDao.insert(AlarmEntity.fromAlarm(alarm))
        }
    }

    fun updateAlarm(alarm: Alarm) {
        coroutineScope.launch {
            alarmDao.update(AlarmEntity.fromAlarm(alarm))
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        coroutineScope.launch {
            alarmDao.delete(alarm.id)
        }
    }

    suspend fun getAlarmById(alarmId: String): AlarmEntity? {
        return alarmDao.getAlarmById(alarmId)
    }

    suspend fun getActiveAlarmsSync(): List<Alarm> {
        return withContext(Dispatchers.IO) {
            alarmDao.getActiveAlarms().map { it.toAlarm() }
        }
    }
}

