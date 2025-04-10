package com.example.proximityalarmapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import org.mapsforge.core.model.LatLong

class NewAlarmActivity : AppCompatActivity() {

    private lateinit var viewModel: AlarmViewModel

    private lateinit var textCoords : TextView
    private lateinit var textSelectLocation: TextView

    // launcher для отслеживания переходов
    private lateinit var locationSelectionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.new_alarm)

        // Инициализация AlarmViewModel
        viewModel = ViewModelProvider(this, AlarmViewModelFactory())[AlarmViewModel::class.java]

        // Инициализируем launcher
        locationSelectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->
                    val lat = data.getDoubleExtra("LATITUDE", 0.0)
                    val lon = data.getDoubleExtra("LONGITUDE", 0.0)
                    if (lat != 0.0 || lon != 0.0) {
                        val location = LatLong(lat, lon)
                        viewModel.updateLocation(location)
                    }
                }
            }
        }

        // Получение координат из интента
        val latitude = intent.getDoubleExtra("LATITUDE", 0.0)
        val longitude = intent.getDoubleExtra("LONGITUDE", 0.0)

        textCoords = findViewById<TextView>(R.id.text_coords)
        textSelectLocation = findViewById<TextView>(R.id.text_select_location)

        // Наблюдаем за изменениями location в ViewModel
        viewModel.location.observe(this) { location ->
            if (location != null && (location.latitude != 0.0 || location.longitude != 0.0)) {
                viewModel.updateHasLocation(true)
                updateLocationText(location)
            }
        }

        // Подключение кнопки отмены
        val text_cancel : TextView = findViewById<TextView>(R.id.text_cancel)
        text_cancel.setOnClickListener { finish() }

        // Обработчик клика на "Указать на карте" или "Метка установлена"
        textSelectLocation.setOnClickListener {
            openMapForLocationSelection()
        }

        if (latitude != 0.0 || longitude != 0.0) {
            // Значит метка уже установлена
            val location = LatLong(latitude, longitude)
            viewModel.updateLocation(location)
            viewModel.updateHasLocation(true)
            updateLocationText(location)
        } else {
            // Метка еще не установлена
            viewModel.updateHasLocation(false)
        }

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
                viewModel.updateRadius(radius_values[position].toFloat())
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // без этого метода мы не можем объявить object
            }
        }

        val title_edit : EditText = findViewById<EditText>(R.id.title_edit)
        title_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateTitle(s?.toString() ?: "")
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })


    }

    private fun updateLocationText(location: LatLong) {
        textSelectLocation.text = "Метка установлена"
        textCoords.text = "Координаты будильника:\n${location.latitude}, ${location.longitude}"
    }

    private fun openMapForLocationSelection() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("SELECT_LOCATION", true)
            // Передаём текущие координаты, если они есть
            viewModel.location.value?.let {
                putExtra("CURRENT_LAT", it.latitude)
                putExtra("CURRENT_LON", it.longitude)
            }
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        locationSelectionLauncher.launch(intent)
    }
}
