package com.example.proximityalarmapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
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

    private lateinit var weekendsSwitch : Switch
    private lateinit var weekdaysSwitch : Switch
    private lateinit var oneTimeSwitch : Switch

    private lateinit var title_edit : EditText

    // launcher для отслеживания переходов
    private lateinit var locationSelectionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.new_alarm)

        // Инициализация AlarmViewModel
        viewModel = ViewModelProvider(this, AlarmViewModelFactory())[AlarmViewModel::class.java]

        textCoords = findViewById<TextView>(R.id.text_coords)
        textSelectLocation = findViewById<TextView>(R.id.text_select_location)

        title_edit = findViewById<EditText>(R.id.title_edit)

        weekdaysSwitch = findViewById<Switch>(R.id.weekdays_switch)
        weekendsSwitch = findViewById<Switch>(R.id.weekends_switch)

        oneTimeSwitch = findViewById<Switch>(R.id.one_time_switch)

        // Обработка координат (если они передавались)
        val latitude = intent?.getDoubleExtra("LATITUDE", 0.0) ?: 0.0
        val longitude = intent?.getDoubleExtra("LONGITUDE", 0.0) ?: 0.0
        if (latitude != 0.0 || longitude != 0.0) {
            viewModel.updateLocation(LatLong(latitude, longitude))
            updateLocationText(LatLong(latitude, longitude))
        }

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

        val radius_edit : EditText = findViewById<EditText>(R.id.radius_edit)

        radius_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) {
                    radius_spinner.setSelection(0)
                    viewModel.updateRadius(s.toString().toFloat())
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        radius_spinner.adapter = adapter

        radius_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val selectedRadius = parent.getItemAtPosition(position).toString()
                if (selectedRadius.isNotEmpty()) {
                    radius_edit.text.clear()
                    viewModel.updateRadius(selectedRadius.toFloat())
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // без этого метода мы не можем объявить object
            }
        }

        title_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateTitle(s?.toString() ?: "")
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Подключение переключателей
        oneTimeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Временно отключаем слушатели дней недели
                getDayCheckboxes().forEach { it.setOnCheckedChangeListener(null) }

                // Очищаем все дни
                getDayCheckboxes().forEach { it.isChecked = false }
                updateSelectedDays(getDayCheckboxes())

                // Выключаем переключатели "По будням/выходным"
                weekdaysSwitch.isChecked = false
                weekendsSwitch.isChecked = false

                // Восстанавливаем слушатели
                getDayCheckboxes().forEach { checkbox ->
                    checkbox.setOnCheckedChangeListener { _, isChecked ->
                        updateSelectedDays(getDayCheckboxes())
                        if (isChecked) {
                            oneTimeSwitch.isChecked = false
                            viewModel.updateoneTime(false)
                        }
                    }
                }
            }
            viewModel.updateoneTime(isChecked)
        }

        weekdaysSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                weekendsSwitch.isChecked = false
                setWeekdaysCheckboxes(true)
                viewModel.updateweekdaysOnly(true)
                viewModel.updateweekendsOnly(false)
            } else {
                viewModel.updateweekdaysOnly(false)
            }
        }

        weekendsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                weekdaysSwitch.isChecked = false
                setWeekendsCheckboxes(true)
                viewModel.updateweekendsOnly(true)
                viewModel.updateweekdaysOnly(false)
            } else {
                viewModel.updateweekendsOnly(false)
            }
        }

        // Подключение CheckBox для дней недели
        val checkBoxMonday = findViewById<CheckBox>(R.id.checkbox_monday)
        val checkBoxTuesday = findViewById<CheckBox>(R.id.checkbox_tuesday)
        val checkBoxWednesday = findViewById<CheckBox>(R.id.checkbox_wednesday)
        val checkBoxThursday = findViewById<CheckBox>(R.id.checkbox_thursday)
        val checkBoxFriday = findViewById<CheckBox>(R.id.checkbox_friday)
        val checkBoxSaturday = findViewById<CheckBox>(R.id.checkbox_saturday)
        val checkBoxSunday = findViewById<CheckBox>(R.id.checkbox_sunday)

        val dayCheckboxes = listOf(
            checkBoxMonday, checkBoxTuesday, checkBoxWednesday,
            checkBoxThursday, checkBoxFriday, checkBoxSaturday, checkBoxSunday
        )

        dayCheckboxes.forEach { checkbox ->
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                updateSelectedDays(dayCheckboxes)
                if (isChecked) {
                    oneTimeSwitch.isChecked = false
                    viewModel.updateoneTime(false)
                }
                checkDaysConsistency()
            }
        }

        // Подключение CheckBox для вариантов оповещения
        val vibrationCheckBox = findViewById<CheckBox>(R.id.vibration_checkbox)
        vibrationCheckBox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateVibration(isChecked)
        }

        val soundCheckBox = findViewById<CheckBox>(R.id.sound_checkbox)
        soundCheckBox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateSoundEnabled(isChecked)
        }

        val notificationCheckBox = findViewById<CheckBox>(R.id.notification_checkbox)
        notificationCheckBox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotification(isChecked)
        }

        // Подключение SeekBar для громкости
        val volumeSeekBar = findViewById<SeekBar>(R.id.volume_seekbar)
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                viewModel.updateVolume(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Подключение кнопки сохранения
        val textSave = findViewById<TextView>(R.id.text_save)
        textSave.setOnClickListener {
            if (viewModel.hasLocation.value == true) {
                viewModel.addAlarm()
                finish()
            } else {
                Toast.makeText(this, "Пожалуйста, укажите местоположение будильника", Toast.LENGTH_SHORT).show()
            }
        }


    }

    private fun updateLocationText(location: LatLong) {
        textSelectLocation.text = "Метка установлена"
        textCoords.text = "Координаты будильника:\n${location.latitude}, ${location.longitude}"
    }

    private fun openMapForLocationSelection() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("SELECT_LOCATION", true)

            // Сохраняем текущие настройки в Bundle
            val alarmData = Bundle().apply {
                putString("title", title_edit.text.toString())

                val radiusFromEditText = findViewById<EditText>(R.id.radius_edit).text.toString().toFloatOrNull()
                val radiusFromViewModel = viewModel.radius.value

                val finalRadius = when {
                    radiusFromEditText != null -> radiusFromEditText
                    radiusFromViewModel != null && radiusFromViewModel != 0f -> radiusFromViewModel
                    else -> 100f
                }

                putFloat("radius", finalRadius)

                putBoolean("oneTime", oneTimeSwitch.isChecked)
                putBoolean("weekdays", weekdaysSwitch.isChecked)
                putBoolean("weekends", weekendsSwitch.isChecked)

                putBoolean("monday", findViewById<CheckBox>(R.id.checkbox_monday).isChecked)
                putBoolean("tuesday", findViewById<CheckBox>(R.id.checkbox_tuesday).isChecked)
                putBoolean("wednesday", findViewById<CheckBox>(R.id.checkbox_wednesday).isChecked)
                putBoolean("thursday", findViewById<CheckBox>(R.id.checkbox_thursday).isChecked)
                putBoolean("friday", findViewById<CheckBox>(R.id.checkbox_friday).isChecked)
                putBoolean("saturday", findViewById<CheckBox>(R.id.checkbox_saturday).isChecked)
                putBoolean("sunday", findViewById<CheckBox>(R.id.checkbox_sunday).isChecked)

                putBoolean("vibration", findViewById<CheckBox>(R.id.vibration_checkbox).isChecked)
                putBoolean("sound", findViewById<CheckBox>(R.id.sound_checkbox).isChecked)
                putBoolean("notification", findViewById<CheckBox>(R.id.notification_checkbox).isChecked)

                putInt("volume", findViewById<SeekBar>(R.id.volume_seekbar).progress)
            }
            putExtra("ALARM_DATA", alarmData)
        }
        locationSelectionLauncher.launch(intent)
    }

    // Функция для проверки, соответствуют ли выбранные дни будням/выходным
    fun checkDaysConsistency() {

        val checkBoxMonday = findViewById<CheckBox>(R.id.checkbox_monday)
        val checkBoxTuesday = findViewById<CheckBox>(R.id.checkbox_tuesday)
        val checkBoxWednesday = findViewById<CheckBox>(R.id.checkbox_wednesday)
        val checkBoxThursday = findViewById<CheckBox>(R.id.checkbox_thursday)
        val checkBoxFriday = findViewById<CheckBox>(R.id.checkbox_friday)
        val checkBoxSaturday = findViewById<CheckBox>(R.id.checkbox_saturday)
        val checkBoxSunday = findViewById<CheckBox>(R.id.checkbox_sunday)

        val isWeekdays = checkBoxMonday.isChecked && checkBoxTuesday.isChecked &&
                checkBoxWednesday.isChecked && checkBoxThursday.isChecked &&
                checkBoxFriday.isChecked && !checkBoxSaturday.isChecked &&
                !checkBoxSunday.isChecked

        val isWeekends = !checkBoxMonday.isChecked && !checkBoxTuesday.isChecked &&
                !checkBoxWednesday.isChecked && !checkBoxThursday.isChecked &&
                !checkBoxFriday.isChecked && checkBoxSaturday.isChecked &&
                checkBoxSunday.isChecked

        weekdaysSwitch.isChecked = isWeekdays
        weekendsSwitch.isChecked = isWeekends
    }

    // Функции для установки CheckBox
    private fun setWeekdaysCheckboxes(checked: Boolean) {
        findViewById<CheckBox>(R.id.checkbox_monday).isChecked = checked
        findViewById<CheckBox>(R.id.checkbox_tuesday).isChecked = checked
        findViewById<CheckBox>(R.id.checkbox_wednesday).isChecked = checked
        findViewById<CheckBox>(R.id.checkbox_thursday).isChecked = checked
        findViewById<CheckBox>(R.id.checkbox_friday).isChecked = checked
        findViewById<CheckBox>(R.id.checkbox_saturday).isChecked = !checked
        findViewById<CheckBox>(R.id.checkbox_sunday).isChecked = !checked
        updateSelectedDays(getDayCheckboxes())
    }

    private fun setWeekendsCheckboxes(checked: Boolean) {
        findViewById<CheckBox>(R.id.checkbox_monday).isChecked = !checked
        findViewById<CheckBox>(R.id.checkbox_tuesday).isChecked = !checked
        findViewById<CheckBox>(R.id.checkbox_wednesday).isChecked = !checked
        findViewById<CheckBox>(R.id.checkbox_thursday).isChecked = !checked
        findViewById<CheckBox>(R.id.checkbox_friday).isChecked = !checked
        findViewById<CheckBox>(R.id.checkbox_saturday).isChecked = checked
        findViewById<CheckBox>(R.id.checkbox_sunday).isChecked = checked
        updateSelectedDays(getDayCheckboxes())
    }

    private fun getDayCheckboxes(): List<CheckBox> {
        return listOf(
            findViewById(R.id.checkbox_monday),
            findViewById(R.id.checkbox_tuesday),
            findViewById(R.id.checkbox_wednesday),
            findViewById(R.id.checkbox_thursday),
            findViewById(R.id.checkbox_friday),
            findViewById(R.id.checkbox_saturday),
            findViewById(R.id.checkbox_sunday)
        )
    }

    private fun updateSelectedDays(checkboxes: List<CheckBox>) {
        val selectedDays = mutableListOf<DayOfWeek>()

        if (checkboxes[0].isChecked) selectedDays.add(DayOfWeek.MONDAY)
        if (checkboxes[1].isChecked) selectedDays.add(DayOfWeek.TUESDAY)
        if (checkboxes[2].isChecked) selectedDays.add(DayOfWeek.WEDNESDAY)
        if (checkboxes[3].isChecked) selectedDays.add(DayOfWeek.THURSDAY)
        if (checkboxes[4].isChecked) selectedDays.add(DayOfWeek.FRIDAY)
        if (checkboxes[5].isChecked) selectedDays.add(DayOfWeek.SATURDAY)
        if (checkboxes[6].isChecked) selectedDays.add(DayOfWeek.SUNDAY)

        viewModel.updateSchedule(selectedDays)
    }
}
