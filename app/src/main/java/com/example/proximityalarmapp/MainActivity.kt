package com.example.proximityalarmapp

//Бибилиотек для рисования маркера на карте
// Импорт для биндингов
// Импорт дял навигации
// Импорты для MapsForge. Карты, андроид утилиты, офлайн рендерер, и считывание файлов
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.viewModels
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
import kotlinx.coroutines.awaitAll
import org.mapsforge.core.model.Point
import org.mapsforge.core.util.MercatorProjection
import java.io.File
import java.io.InputStream
import kotlin.Boolean

class MainActivity : AppCompatActivity() {

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

            mapView.setOnTouchListener { _, event ->
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
                                mapView.invalidate()
                            }, 600)
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
