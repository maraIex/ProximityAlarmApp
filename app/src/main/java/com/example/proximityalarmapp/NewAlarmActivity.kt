package com.example.proximityalarmapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class NewAlarmActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.new_alarm)

        // Получение координат из интента
        val latitude = intent.getDoubleExtra("LATITUDE", 0.0)
        val longitude = intent.getDoubleExtra("LONGITUDE", 0.0)

        // Вывод координат на экран для отладки (можно будет заменить на поля ввода)
        val textCoords = findViewById<TextView>(R.id.text_coords)
        textCoords.text = "Создание будильника:\n$latitude, $longitude"

        // Здесь можно реализовать остальную логику создания будильника (ввод названия, расстояния, сохранение в базу и т.д.)
    }
}
