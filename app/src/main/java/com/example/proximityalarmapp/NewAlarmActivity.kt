package com.example.proximityalarmapp

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

class NewAlarmActivity : AppCompatActivity() {

    private lateinit var viewModel: AlarmViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.new_alarm)

        // Инициализация AlarmViewModel
        viewModel = ViewModelProvider(this, AlarmViewModelFactory())[AlarmViewModel::class.java]
        // Получение координат из интента
        val latitude = intent.getDoubleExtra("LATITUDE", 0.0)
        val longitude = intent.getDoubleExtra("LONGITUDE", 0.0)

        // Вывод координат на экран для отладки (можно будет заменить на поля ввода)
        val textCoords = findViewById<TextView>(R.id.text_coords)
        textCoords.text = "Создание будильника:\n$latitude, $longitude"

        // Подключение кнопки отмены
        val text_cancel : TextView = findViewById<TextView>(R.id.text_cancel)
        text_cancel.setOnClickListener { finish() }

        // Получаем возможные значения радиуса
        val radius_values = resources.getStringArray(R.array.radius_values)

        // Подключение списка выбора радиуса
        val radius_spinner : Spinner = findViewById<Spinner>(R.id.radius_spinner)
        val adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, radius_values)

        radius_spinner.adapter = adapter

        radius_spinner.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View, position: Int, id: Long
            ) {
                Toast.makeText(this@NewAlarmActivity, radius_values[position], Toast.LENGTH_SHORT)
                    .show()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // без этого метода мы не можем объявить object
            }
        }


    }
}
