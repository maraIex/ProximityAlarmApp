package com.example.proximityalarmapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import org.mapsforge.core.model.LatLong
import java.util.UUID
import kotlin.getValue

class NewAlarmActivity : AppCompatActivity() {

    private val viewModel by lazy {
        (application as ProximityAlarm).appContainer.alarmViewModel
    }

    private lateinit var textCoords: TextView
    private lateinit var textSelectLocation: TextView
    private lateinit var weekendsSwitch: Switch
    private lateinit var weekdaysSwitch: Switch
    private lateinit var oneTimeSwitch: Switch
    private lateinit var titleEdit: EditText
    private lateinit var radiusSpinner: Spinner
    private lateinit var radiusEdit: EditText
    private lateinit var vibrationCheckBox: CheckBox
    private lateinit var notificationCheckBox: CheckBox
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var soundCheckBox: CheckBox
    private lateinit var textSave: TextView
    private lateinit var textCancel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.new_alarm)

        initViews()
        setupListeners()
        setupViewModelObservers()
        //updateUIFromViewModel()
    }

    private fun initViews() {
        textCoords = findViewById(R.id.text_coords)
        textSelectLocation = findViewById(R.id.text_select_location)
        titleEdit = findViewById(R.id.title_edit)
        weekdaysSwitch = findViewById(R.id.weekdays_switch)
        weekendsSwitch = findViewById(R.id.weekends_switch)
        oneTimeSwitch = findViewById(R.id.one_time_switch)
        radiusSpinner = findViewById(R.id.radius_spinner)
        radiusEdit = findViewById(R.id.radius_edit)
        vibrationCheckBox = findViewById<CheckBox>(R.id.vibration_checkbox)
        notificationCheckBox = findViewById<CheckBox>(R.id.notification_checkbox)
        volumeSeekBar = findViewById<SeekBar>(R.id.volume_seekbar)
        textSave = findViewById<TextView>(R.id.text_save)
        textCancel = findViewById(R.id.text_cancel)
        soundCheckBox = findViewById<CheckBox>(R.id.sound_checkbox)
    }

    private fun setupViewModelObservers() {
        // 1. Координаты и метка
        viewModel.location.observe(this) { location ->
            location?.takeIf { it.isValid() }?.let {
                updateLocationText(it)
                textSelectLocation.text = "Метка установлена"
            } ?: run {
                textCoords.text = "Координаты не указаны"
                textSelectLocation.text = "Указать на карте"
            }
        }

        // 2. Радиус
        viewModel.radius.observe(this) { radius ->
            radius?.let {
                val radiusValues = getRadiusValues()
                if (radius in radiusValues) {
                    radiusSpinner.setSelection(radiusValues.indexOf(radius))
                } else {
                    radiusEdit.setText(radius.toString())
                }
            }
        }

        // 3. Переключатели
        viewModel.oneTime.observe(this) { isChecked ->
            oneTimeSwitch.isChecked = isChecked ?: true
        }
        viewModel.weekdaysOnly.observe(this) { isChecked ->
            weekdaysSwitch.isChecked = isChecked ?: false
        }
        viewModel.weekendsOnly.observe(this) { isChecked ->
            weekendsSwitch.isChecked = isChecked ?: false
        }

        // 4. Дни недели
        viewModel.schedule.observe(this) { schedule ->
            schedule?.let {
                findViewById<CheckBox>(R.id.checkbox_monday).isChecked = DayOfWeek.MONDAY in it
                findViewById<CheckBox>(R.id.checkbox_tuesday).isChecked = DayOfWeek.TUESDAY in it
                findViewById<CheckBox>(R.id.checkbox_wednesday).isChecked = DayOfWeek.WEDNESDAY in it
                findViewById<CheckBox>(R.id.checkbox_thursday).isChecked = DayOfWeek.THURSDAY in it
                findViewById<CheckBox>(R.id.checkbox_friday).isChecked = DayOfWeek.FRIDAY in it
                findViewById<CheckBox>(R.id.checkbox_saturday).isChecked = DayOfWeek.SATURDAY in it
                findViewById<CheckBox>(R.id.checkbox_sunday).isChecked = DayOfWeek.SUNDAY in it
            }
        }

        // 5. Название
        viewModel.title.observe(this) { title ->
            title?.takeIf { titleEdit.text.toString() != it }?.let {
                titleEdit.setText(it)
            }
        }

        // 6. Настройки оповещения
        viewModel.vibration.observe(this) { isChecked ->
            vibrationCheckBox.isChecked = isChecked ?: true
        }
        viewModel.soundEnabled.observe(this) { isChecked ->
            soundCheckBox.isChecked = isChecked ?: true
        }
        viewModel.notification.observe(this) { isChecked ->
            notificationCheckBox.isChecked = isChecked ?: true
        }

        // 7. Громкость
        viewModel.volume.observe(this) { volume ->
            volume?.takeIf { volumeSeekBar.progress != it }?.let {
                volumeSeekBar.progress = it
            }
        }
    }

    private fun setupListeners() {
        // Подключение кнопки отмены
        textCancel.setOnClickListener {
            viewModel.clearAlarmData()
            finish()
        }

        // Обработчик клика на "Указать на карте" или "Метка установлена"
        textSelectLocation.setOnClickListener {
            openMapForLocationSelection()
        }

        // Получаем возможные значения радиуса
        val radius_values = resources.getStringArray(R.array.radius_values)

        // Подключение списка выбора радиуса
        val adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, radius_values)

        radiusEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) {
                    radiusSpinner.setSelection(0)
                    viewModel.updateRadius(s.toString().toFloat())
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        radiusSpinner.adapter = adapter

        radiusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val selectedRadius = parent.getItemAtPosition(position).toString()
                if (selectedRadius.isNotEmpty()) {
                    radiusEdit.text.clear()
                    viewModel.updateRadius(selectedRadius.toFloat())
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // без этого метода мы не можем объявить object
            }
        }

        titleEdit.addTextChangedListener(object : TextWatcher {
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
        vibrationCheckBox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateVibration(isChecked)
        }

        soundCheckBox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateSoundEnabled(isChecked)
        }

        notificationCheckBox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateNotification(isChecked)
        }

        // Подключение SeekBar для громкости
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                viewModel.updateVolume(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Подключение кнопки сохранения
        textSave.setOnClickListener {
            if (viewModel.hasLocation.value == true) {
                // Создаем и сохраняем будильник
                val alarm = Alarm(
                    id = UUID.randomUUID().toString(),
                    location = viewModel.location.value!!,
                    radius = viewModel.radius.value ?: 100f,
                    title = viewModel.title.value ?: "Без названия",
                    isEnabled = true,
                    schedule = viewModel.schedule.value ?: emptyList(),
                    oneTime = viewModel.oneTime.value ?: true,
                    weekdaysOnly = viewModel.weekdaysOnly.value ?: false,
                    weekendsOnly = viewModel.weekendsOnly.value ?: false,
                    vibration = viewModel.vibration.value ?: true,
                    soundEnabled = viewModel.soundEnabled.value ?: true,
                    notification = viewModel.notification.value ?: true,
                    volume = viewModel.volume.value ?: 50
                )

                // Сохраняем через ViewModel
                viewModel.addAlarm(alarm)

                // Закрываем активность
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
//            // Передаем текущие координаты (если есть)
//            viewModel.location.value?.let {
//                putExtra("CURRENT_LAT", it.latitude)
//                putExtra("CURRENT_LON", it.longitude)
//            }
        }
        startActivity(intent)
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

    private fun getRadiusValues(): List<Float> {
        return resources.getStringArray(R.array.radius_values).map { it.toFloat() }
    }

    private fun Bundle.getLocation(): LatLong? {
        val lat = getDouble("LATITUDE", 0.0)
        val lon = getDouble("LONGITUDE", 0.0)
        return if (lat != 0.0 || lon != 0.0) LatLong(lat, lon) else null
    }

    private fun LatLong.isValid() = latitude != 0.0 || longitude != 0.0

    // Расширение для Intent
    fun Intent.getLocation(): LatLong? {
        val lat = getDoubleExtra("LATITUDE", 0.0)
        val lon = getDoubleExtra("LONGITUDE", 0.0)
        return if (lat != 0.0 || lon != 0.0) LatLong(lat, lon) else null
    }

    // В NewAlarmActivity
    override fun onDestroy() {
        super.onDestroy()
        Log.d("NewAlarm", "Activity destroyed") // Не должно быть перед получением результата!
    }
}
