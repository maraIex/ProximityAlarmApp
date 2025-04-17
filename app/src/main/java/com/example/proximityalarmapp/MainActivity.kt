package com.example.proximityalarmapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
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
import org.mapsforge.core.model.Point
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.Boolean

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 100
        private const val TAG = "MainActivity"
    }

    private lateinit var drawerLayout: DrawerLayout
    // Создание объекта AlarmViewModel при загрузке MainActivity
    private val alarmViewModel: AlarmViewModel = AlarmViewModel(AlarmRepository)
    // mapView вынесена сюда, потому что нужен доступ к ней вне onCreate
    private lateinit var mapView: MapView
    // Флаг окончания загрузки карты
    private var isMapReady = false


    // Обработчик долгого касания
    private val longPressHandler = Handler(Looper.getMainLooper())
    // Флаг сработало ли долгое касание
    private var isLongPressTriggered = false
    // Флаг нажатия на существующий маркер
    private var isMarkerTouched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Запуск в фоновом режим
        if (checkPermissions()) {
            //Запускаем трекинг с помощью FusedLocationApi
            startLocationTracking()
        } else {
            requestPermissions()
        }

        setContentView(R.layout.activity_main)

        //Подключение рендерера
        AndroidGraphicFactory.createInstance(applicationContext)

        //Инициализация карты
        mapView = findViewById<MapView>(R.id.map)
        mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        //Добавление возможности кликать на карту и кнопок увеличения и уменьшения размерности
        mapView.isClickable = true
        mapView.mapScaleBar.isVisible = true

        //Уставновление предлелов Зума
        mapView.setBuiltInZoomControls(true)
        mapView.setZoomLevelMin(10.toByte())
        mapView.setZoomLevelMax(20.toByte())

        //Создание из карты кешфайла для офлайн просмотра
        val tileCache = AndroidUtil.createTileCache(this, "mapcache",
            mapView.model.displayModel.tileSize, 1f,
            mapView.model.frameBufferModel.overdrawFactor)

        //Открытие файла карт из папки assets с помощью корутины, поскольку кеширование карты - длительный процесс
        lifecycleScope.launch {
            val file: File
            withContext(Dispatchers.IO) {
                file = File(cacheDir, "SaratovZone.map")
                file.outputStream().use { output ->
                    assets.open("SaratovZone.map").copyTo(output)
                }
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
            mapView.model.mapViewPosition.setCenter(boundingBox.centerPoint)
            mapView.model.mapViewPosition.zoomLevel = 15.toByte()

            // Применение темы к карте
            tileRendererLayer.setXmlRenderTheme(renderTheme)

            // Добавление слоя в MapView
            mapView.layerManager.layers.add(tileRendererLayer)

            // Карта готова к взаимодействию
            isMapReady = true

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


//            mapView.setOnTouchListener { _, event ->
//                when (event.action) {
//                    MotionEvent.ACTION_DOWN -> {
//                        isLongPressTriggered = false
//                        longPressHandler.postDelayed({
//                            isLongPressTriggered = true
//
//                            // Получаем координаты касания
//                            val tappedLatLong = mapView.mapViewProjection.fromPixels(
//                                event.x.toDouble(), event.y.toDouble()
//                            )
//
//                            showAddMarkerDialog(tappedLatLong)
//                            mapView.invalidate()
//                        }, 600) // Время долгого нажатия (600 мс)
//                    }
//
//                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                        longPressHandler.removeCallbacksAndMessages(null)
//                    }
//                }
//                false
//            }
        }

        // Тестовый маркер
        //val bitmap = AndroidBitmap(BitmapFactory.decodeResource(resources, R.drawable.marker))
        //val marker = Marker(LatLong(51.602578, 46.007720), bitmap, 0, -bitmap.height / 2)
        //mapView.layerManager.layers.add(marker)
        //mapView.invalidate()

        // Поиск элементов в интерфейсе по id
        drawerLayout = findViewById(R.id.drawer_layout)
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
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при запуске сервиса: ${e.message}")
        }
    }

    private fun schedulePeriodicWork() {
        val workRequest = PeriodicWorkRequestBuilder<LocationCheckWorker>(
            30, // Интервал в минутах
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




    private fun showAddMarkerDialog(latLong: LatLong) {
        AlertDialog.Builder(this)
            .setTitle("Добавить будильник")
            .setMessage("Координаты: ${latLong.latitude}, ${latLong.longitude}")
            .setPositiveButton("Добавить") { _, _ ->
                val marker = createInteractiveMarker(this, latLong, mapView)
                mapView.layerManager.layers.add(marker)
                println("Маркер добавлен, количество слоёв: ${mapView.layerManager.layers.size()}")
                println("Координаты: ${marker.latLong}")
                mapView.invalidate()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun createInteractiveMarker(context: Context, latLong: LatLong, mapView: MapView): Marker {
        // Создаем Bitmap для маркера
        val bitmap = AndroidBitmap(BitmapFactory.decodeResource(context.resources, R.drawable.marker))

        // Создаем маркер с обработчиками
        return object : Marker(latLong, bitmap, 0, -bitmap.height / 2) {
            override fun onTap(tapLatLong: LatLong, layerXY: Point, tapXY: Point): Boolean {
                if (contains(layerXY, tapXY, mapView)) {
                    Toast.makeText(context, "Будильник: ${tapLatLong.latitude}, ${tapLatLong.longitude}",
                        Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }

            override fun onLongPress(tapLatLong: LatLong, layerXY: Point, tapXY: Point): Boolean {
                if (contains(layerXY, tapXY, mapView)) {
                    AlertDialog.Builder(context)
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
    private fun isTouchOnMarker(latLong: LatLong): Boolean {
        return mapView.layerManager.layers.any { layer ->
            layer is Marker && layer.latLong == latLong
        }
    }

}
