package com.example.proximityalarmapp

import org.mapsforge.core.model.LatLong

data class Alarm(
    val id: String,
    val location: LatLong,
    val radius: Float = 100f,
    val title: String,
    val isEnabled: Boolean,
    val schedule: List<DayOfWeek>,

    val oneTime: Boolean = false,
    val weekdaysOnly: Boolean = false,
    val weekendsOnly: Boolean = false,
    val sound: String = "default",
    val vibration: Boolean = false,
    val soundEnabled: Boolean = true,
    val notification: Boolean = true,
    val volume: Int = 100
)

