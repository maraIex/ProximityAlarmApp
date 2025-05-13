package com.example.proximityalarmapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback
import org.mapsforge.map.rendertheme.XmlThemeResourceProvider
import org.mapsforge.map.android.graphics.AndroidBitmap
import org.mapsforge.map.layer.overlay.Marker
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.proximityalarmapp.LocationTrackingService.LocalBinder
import org.mapsforge.core.model.Point
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import androidx.cardview.widget.CardView
import kotlin.Boolean
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {
  
    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 100
        private const val TAG = "MainActivity" 
    }

    // Инициализация AlarmViewModel
    private val viewModel by lazy {
        (application as ProximityAlarm).appContainer.alarmViewModel
    }

    private lateinit var drawerLayout: DrawerLayout
    // mapView вынесена сюда, потому что нужен доступ к ней вне onCreate
    private lateinit var mapView: MapView
    //Подписка на обновление позиции
    private var trackingService: LocationTrackingService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("Binding", "Service connected")
            trackingService = (service as LocalBinder).getService().apply {
                addLocationListener(::handleLocationUpdate)
                Log.d("Binding", "Service connected with ${locationUpdateListener.size} listeners")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.e("Binding", "Service disconnected")
            trackingService?.removeLocationListener(::handleLocationUpdate)
            trackingService = null
            Log.w("Binding", "Service unexpectedly disconnected")
        }
    }
    // Флаг окончания загрузки карты
    private var isMapReady = false
    //Флаг об первой инициализации маркера

    // Обработчик долгого касания
    private val handler = Handler(Looper.getMainLooper())
    // Флаг сработало ли долгое касание
    private var isLongPressTriggered = false
    // Флаг нажатия на существующий маркер
    private var isMarkerTouched = false
    // Флаг режима установки метки
    private var isSelectionMode = false

    // Кнопка переключения режимов
    private lateinit var btnToggleMode: ImageButton
    // Текст об установке метки
    private lateinit var textPlacementContainer: CardView
    private lateinit var textPlacement: TextView

    // Кнопка шторки
    private lateinit var btn_drawer: ImageButton

    // Переменные для анимаций
    val animation_duration = 300L
    val text_offset = -100f
    val button_offset = -100f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //
        MapMarkerManager.initUserMarker(this, R.drawable.iamhere)

        // Инициализация кнопки переключения и текста установки
        btnToggleMode = findViewById(R.id.btn_toggle_mode)
        textPlacementContainer = findViewById(R.id.text_placement_container)
        textPlacement = findViewById(R.id.text_placement)

        drawerLayout = findViewById(R.id.drawer_layout)
        btn_drawer = findViewById(R.id.btn_drawer)

        // Инициализация touchSlop
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop * 1.5f

        //Подключение рендерера
        AndroidGraphicFactory.createInstance(applicationContext)

        //Инициализация карты
        mapView = findViewById<MapView>(R.id.map)
        mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        //Добавление возможности кликать на карту и кнопок увеличения и уменьшения размерности
        mapView.isClickable = true
        mapView.mapScaleBar.isVisible = true

        //Уставновление пределов Зума
        mapView.setBuiltInZoomControls(true)
        mapView.setZoomLevelMin(10.toByte())
        mapView.setZoomLevelMax(20.toByte())

        //Создание из карты кешфайла для офлайн просмотра
        val tileCache = AndroidUtil.createTileCache(this, "mapcache",
            mapView.model.displayModel.tileSize, 1f,
            mapView.model.frameBufferModel.overdrawFactor)

        //Открытие файла карт из папки assets с помощью корутины, поскольку кеширование карты - длительный процесс
        lifecycleScope.launch {
            val file = File(cacheDir, "SaratovZone.map")

            // Проверяем, существует ли файл и его размер больше 0
            if (!file.exists() || file.length() == 0L) {
                withContext(Dispatchers.IO) {
                    file.outputStream().use { output ->
                        assets.open("SaratovZone.map").copyTo(output)
                    }
                }
                isMapReady = true
                Log.d("MainActivity", "Карта успешно скопирована в кеш")
            } else {
                Log.d("MainActivity", "Карта уже существует в кеше, пропускаем копирование"
            }

            // Инициализация MapDataStore
            val mapDataStore: MapDataStore = MapFile(file)

            //Рендеринг слоя в карте
            val tileRendererLayer = TileRendererLayer(
                tileCache,
                mapDataStore,
                mapView.model.mapViewPosition,
                false, // isTransparent
                true,  // renderLabels
                false, // cacheLabels
                AndroidGraphicFactory.INSTANCE
            )

            //Реализация 5 обязательных интерфейсов, из которых реально используется только getRenderThemeAsStream
            val renderTheme = object : XmlRenderTheme {
                private var menuCallback: XmlRenderThemeMenuCallback? = null
                private var resourceProvider: XmlThemeResourceProvider? = null

                override fun getRelativePathPrefix(): String {
                    return ""
                }

                override fun getRenderThemeAsStream(): InputStream {
                    // загрузка темы из файла
                    return this@MainActivity.assets.open("vtm/default.xml")
                }

                override fun getMenuCallback(): XmlRenderThemeMenuCallback? {
                    return menuCallback
                }

                override fun getResourceProvider(): XmlThemeResourceProvider? {
                    return resourceProvider
                }

                override fun setMenuCallback(menuCallback: XmlRenderThemeMenuCallback?) {
                    this.menuCallback = menuCallback
                }

                override fun setResourceProvider(resourceProvider: XmlThemeResourceProvider?) {
                    this.resourceProvider = resourceProvider
                }
            }
            //Центрирование карты и установка стратового положения( пока в центре карты, но потом будет в зависимости от гео)
            val boundingBox = mapDataStore.boundingBox()
            mapView.model.mapViewPosition.setCenter(LatLong(51.52964, 45.98008))
            mapView.model.mapViewPosition.zoomLevel = 15.toByte()

            // Применение темы к карте
            tileRendererLayer.setXmlRenderTheme(renderTheme)

            // Добавление слоя в MapView
            mapView.layerManager.layers.add(tileRendererLayer)
            //Запуск в фоновом режим
            if (checkPermissions()) {
                //Запускаем трекинг с помощью FusedLocationApi
                startLocationTracking()
            } else {
                requestPermissions()
            }

            setupAlarmMarkers()
        }
        // Поиск элементов в интерфейсе по id
        drawerLayout = findViewById(R.id.drawer_layout)

        Log.d("MainActivity", "SELECT_LOCATION: ${intent?.getBooleanExtra("SELECT_LOCATION", false)}")
        // Проверка, запустили мы карту из меню созадния будильника или нет
        if (intent?.getBooleanExtra("SELECT_LOCATION", false) == true) {
            showLocationSelectionMode()
        } else {
            BasicMode()
        }

        btnToggleMode.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(this, R.anim.btn_scale))
            handler.postDelayed({
                toggleSelectionMode()
            }, 100)
        }
    }

    private fun BasicMode() {
        isSelectionMode = false
        textPlacementContainer.visibility = View.GONE
        updateToggleButtonState()
        animateMenuButton(visible = true)
        animateText(visible = false)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        val btnMenu: ImageButton = findViewById(R.id.btn_drawer)

        // Если кнопка шторки нажата
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START) // Открываем меню
        }

        // Обработка нажатий на меню
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_alarms -> {
                    startActivity(Intent(this, AlarmsActivity::class.java)) //Смена Activity на AlarmsActivity
                    drawerLayout.closeDrawers() // Закрываем меню после выбора
                    true
                }
                R.id.nav_settings -> {
                    //startActivity(Intent(this, SettingsActivity::class.java)) // Смена Activity на SettingsActivity
                    drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }

        setupMapListeners()
    }

    private fun showLocationSelectionMode() {
        isSelectionMode = true
        updateToggleButtonState()
        animateMenuButton(visible = false)
        animateText(visible = true)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

        setupMapListeners()
    }

    // Переменные для управления обработчиком касаний
    private val MAX_CLICK_DURATION = 200L
    private var touchSlop = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartTime = 0L;
    private var isScrolling = false

    private fun setupMapListeners() {
        @SuppressLint("ClickableViewAccessibility")
            mapView.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPressTriggered = false
                        isMarkerTouched = mapView.layerManager.layers.any { layer ->
                            layer is Marker && layer.contains(
                                mapView.mapViewProjection.toPixels(layer.latLong),
                                Point(event.x.toDouble(), event.y.toDouble()),
                                mapView
                            )
                        }
                        if (!isMarkerTouched) {
                            longPressHandler.postDelayed({
                                isLongPressTriggered = true
                                val tappedLatLong = mapView.mapViewProjection.fromPixels(
                                    event.x.toDouble(), event.y.toDouble()
                                )
                                showAddMarkerDialog(tappedLatLong)
                            }, 600)
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        longPressHandler.removeCallbacksAndMessages(null)
                        if (!isLongPressTriggered && !isMarkerTouched) {
                            view.performClick() // Вызов при обычном клике
                        }
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        longPressHandler.removeCallbacksAndMessages(null)
                    }
                }
                false
            }
    }

    private fun handleLongPress(event: MotionEvent) {
        if (!isSelectionMode) return

        isLongPressTriggered = true
        val tappedLatLong = mapView.mapViewProjection.fromPixels(
            event.x.toDouble(), event.y.toDouble()
        )
        showAddMarkerDialog(tappedLatLong)
    }

    private fun handleClick(event: MotionEvent) {
        // Обработка обычного клика (если нужно)
    }

    private fun cleanupTouch() {
        handler.removeCallbacksAndMessages(null)
        isLongPressTriggered = false
        isMarkerTouched = false
        isScrolling = false
    }

    private fun toggleSelectionMode() {
        if (isSelectionMode) {
            BasicMode()
        } else {
            showLocationSelectionMode()
        }
    }

    private fun checkSelectionMode() {
        if (intent?.getBooleanExtra("SELECT_LOCATION", false) == true) {
            showLocationSelectionMode()
        } else {
            BasicMode()
        }
    }

    private fun animateMenuButton(visible: Boolean) {
        btn_drawer.animate()
            .translationX(if (visible) 0f else button_offset)
            .alpha(if (visible) 1f else 0f)
            .setDuration(animation_duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withStartAction {
                btn_drawer.isClickable = visible
            }
            .start()
    }

    private fun animateText(visible: Boolean) {
        textPlacementContainer.animate()
            .translationY(if (visible) 0f else text_offset)
            .setDuration(animation_duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withStartAction {
                if (visible) {
                    textPlacementContainer.visibility = View.VISIBLE
                }
            }
            .withEndAction {
                if (!visible) {
                    textPlacementContainer.visibility = View.GONE
                }
            }
            .start()
    }

    private fun updateToggleButtonState() {
        btnToggleMode.isSelected = isSelectionMode
        btnToggleMode.setImageResource(
            if (isSelectionMode) R.drawable.marker_close else R.drawable.marker
        )
    }

    override fun onDestroy() {
        unbindService(serviceConnection) // Важно!
        trackingService?.removeLocationListener(::handleLocationUpdate)
        super.onDestroy()
    }
    //ОГРОМНЫЙ БЛОК ФУНКЦИЙ ГЕОКОДИНГА

    private fun checkPermissions(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Для Android 10+ нужно фоновое разрешение
        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // Для Android 12+ нужно разрешение на точные геозоны
        val hasPreciseGeofence = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return (hasFineLocation || hasCoarseLocation) && hasBackgroundLocation && hasPreciseGeofence
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startLocationTracking()
            } else {
                // Обработка отказа от разрешений
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    // Пользователь отклонил, но можно показать объяснение
                    showPermissionExplanation()
                } else {
                    // Пользователь отклонил и поставил "Не спрашивать снова"
                    Toast.makeText(this, "Для работы приложения необходимы разрешения на местоположение", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showPermissionExplanation() {
        AlertDialog.Builder(this)
            .setTitle("Необходимы разрешения")
            .setMessage("Приложению нужны разрешения на местоположение для работы геозон")
            .setPositiveButton("OK") { _, _ -> requestPermissions() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startLocationTracking() {
        if (!checkPermissions()) {
            Log.w(TAG, "Попытка запуска без необходимых разрешений")
            return
        }
        // Запускаем сервис
        startLocationService()
        // Запускаем периодическую проверку
        schedulePeriodicWork()
    }

    private fun startLocationService() {
        try {
            val intent = Intent(this, LocationTrackingService::class.java)
            startService(intent)
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при запуске сервиса: ${e.message}")
        }
    }

    private fun schedulePeriodicWork() {
        val workRequest = PeriodicWorkRequestBuilder<LocationCheckWorker>(
            1, // Интервал в минутах
            TimeUnit.MINUTES
        )
            .setInitialDelay(10, TimeUnit.MINUTES)
            .addTag(LocationCheckWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "location_check",
            ExistingPeriodicWorkPolicy.UPDATE, // Обновляем существующую работу
            workRequest
        )
    }

    private fun handleLocationUpdate(location: Location) {
        Log.d("LocationFlow", "HandleLocationUpdate triggered! Location: $location")
        // Добавьте проверку на область видимости карты
        val saratovBounds = LatLong(48.0, 44.0) to LatLong(53.0, 48.0)
        val currentLatLong = LatLong(location.latitude, location.longitude)

        if (!currentLatLong.isWithin(saratovBounds)) {
            Log.w("Location", "Position outside Saratov region: $currentLatLong")
            return
        }

        runOnUiThread {
            val latLong = LatLong(location.latitude, location.longitude)
            Log.d("Location", "Updating marker: $latLong")
            MapMarkerManager.updateUserLocation(mapView, latLong)
        }
    }

    // Добавьте расширение для проверки координат
    fun LatLong.isWithin(bounds: Pair<LatLong, LatLong>): Boolean {
        return latitude in bounds.first.latitude..bounds.second.latitude &&
                longitude in bounds.first.longitude..bounds.second.longitude
    }

    private fun showAddMarkerDialog(latLong: LatLong) {
        AlertDialog.Builder(this)
            .setTitle("Добавить будильник")
            .setMessage("Координаты: ${latLong.latitude}, ${latLong.longitude}")
            .setPositiveButton("Добавить") { _, _ ->
                // 1. Сохраняем координаты в ViewModel
                viewModel.apply {
                    updateLocation(latLong)
                    updateHasLocation(true)
                }

                BasicMode()
                // 2. Возвращаемся в NewAlarmActivity (на самом деле создаём новую)
                startActivity(Intent(this, NewAlarmActivity::class.java))
            }
            .setNegativeButton("Отмена") { _, _ ->
                BasicMode()
            }
            .show()
    }


    fun createInteractiveMarker(alarm: Alarm): Marker {
        // Создаем Bitmap для маркера
        val bitmap = AndroidBitmap(BitmapFactory.decodeResource(resources, R.drawable.marker))

        // Создаем маркер с обработчиками
        return object : Marker(alarm.location, bitmap, 0, -bitmap.height / 2) {
            override fun onTap(tapLatLong: LatLong, layerXY: Point, tapXY: Point): Boolean {
                if (contains(layerXY, tapXY, mapView)) {
                    showAlarmInfo(alarm)
                    return true
                }
                return false
            }

            override fun onLongPress(tapLatLong: LatLong, layerXY: Point, tapXY: Point): Boolean {
                if (contains(layerXY, tapXY, mapView)) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Удалить будильник?")
                        .setMessage("Вы точно хотите удалить этот будильник?")
                        .setPositiveButton("Да") { _, _ ->
                            mapView.layerManager.layers.remove(this)
                            bitmap.decrementRefCount() // Удаление
                        }
                        .setNegativeButton("Нет", null)
                        .show()
                    return true
                }
                return false
            }
        }
    }

    // Нажимаем ли мы на существующий маркер
    private fun isTouchOnMarker(event: MotionEvent, tolerance: Double = 20.0): Boolean {
        val touchPoint = Point(event.x.toDouble(), event.y.toDouble())

        return mapView.layerManager.layers.any { layer ->
            if (layer is Marker) {
                val markerPoint = mapView.mapViewProjection.toPixels(layer.latLong)
                layer.contains(markerPoint, touchPoint, mapView) ||
                        distance(markerPoint, touchPoint) < tolerance
            } else false
        }
    }

    private fun distance(p1: Point, p2: Point): Double {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }

    private fun setupAlarmMarkers() {
        viewModel.alarms.observe(this) { alarms ->
            // Создаем список маркеров для удаления
            val markersToRemove = mapView.layerManager.layers
                .filterIsInstance<Marker>()
                .toList() // Создаем копию списка

            // Удаляем каждый маркер отдельно
            markersToRemove.forEach { marker ->
                mapView.layerManager.layers.remove(marker)
                (marker.bitmap as? AndroidBitmap)?.decrementRefCount()
            }

            // Добавляем новые маркеры для каждого будильника
            alarms.forEach { alarm ->
                val marker = createInteractiveMarker(alarm)
                mapView.layerManager.layers.add(marker)
            }

            mapView.invalidate() // Обновляем карту
        }
    }

    private fun showAlarmInfo(alarm: Alarm) {
        AlertDialog.Builder(this@MainActivity) // исправлено здесь
            .setTitle(alarm.title)
            .setMessage("""
            Радиус: ${alarm.radius} м
            Координаты: ${alarm.location.latitude}, ${alarm.location.longitude}
            Расписание: ${formatSchedule(alarm.schedule)}
        """.trimIndent())
            .setPositiveButton("OK", null)
            .setNegativeButton("Удалить") { _, _ ->
                viewModel.deleteAlarm(alarm)
            }
            .show()
    }

    private fun formatSchedule(schedule: List<DayOfWeek>): String {
        return when {
            schedule.size == 7 -> "Ежедневно"
            schedule.isEmpty() -> "Одноразовый"
            else -> schedule.joinToString(" ") {
                when(it) {
                    DayOfWeek.MONDAY -> "Пн"
                    DayOfWeek.TUESDAY -> "Вт"
                    DayOfWeek.WEDNESDAY -> "Ср"
                    DayOfWeek.THURSDAY -> "Чт"
                    DayOfWeek.FRIDAY -> "Пт"
                    DayOfWeek.SATURDAY -> "Сб"
                    DayOfWeek.SUNDAY -> "Вс"
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkSelectionMode()
    }
}
