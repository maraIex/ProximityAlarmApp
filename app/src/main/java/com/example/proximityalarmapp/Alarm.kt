package com.example.proximityalarmapp

import org.mapsforge.core.model.LatLong

data class Alarm(
    val id: String,
    val location: LatLong,
    val radius: Float = 100f,
    val name: String,
    val isEnabled: Boolean,
    val schedule: List<DayOfWeek>
)
