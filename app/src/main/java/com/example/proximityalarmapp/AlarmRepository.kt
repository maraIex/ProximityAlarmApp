package com.example.proximityalarmapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object AlarmRepository {
    // Список будильников
    private val alarmList = mutableListOf<Alarm>()
    // LiveData - "обертка", которая дает доступ к данным и позволяет наблюдать за этими данными
    private val alarmsLiveData = MutableLiveData<List<Alarm>>()

    // При создании репозитория - заполнить LiveData актуальным списком будильников (аналогия - выставить на витрину)
    init {
        alarmsLiveData.value = alarmList
    }

    // С помощью этой функции можно получить список будильников
    fun getAllAlarms(): LiveData<List<Alarm>> = alarmsLiveData

    fun getAlarmById(id: String): Alarm? {
        return alarmList.firstOrNull { it.id == id }
    }

    //все функции self explanatory

    fun addAlarm(alarm: Alarm) {
        alarmList.add(alarm)
        alarmsLiveData.value = alarmList.toList()
    }

    fun updateAlarm(updatedAlarm: Alarm) {
        val index = alarmList.indexOfFirst { it.id == updatedAlarm.id }
        if (index != -1) {
            alarmList[index] = updatedAlarm
            alarmsLiveData.value = alarmList.toList()
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        alarmList.remove(alarm)
        alarmsLiveData.value = alarmList.toList()
    }

    fun saveAlarms() {
        TODO("Сохранить будильники")
    }
}

