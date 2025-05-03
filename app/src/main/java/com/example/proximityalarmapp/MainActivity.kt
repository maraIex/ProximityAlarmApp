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
import android.widget.TextView
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
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import androidx.cardview.widget.CardView
import kotlin.Boolean
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    // mapView вынесена сюда, потому что нужен доступ к ней вне onCreate
    private lateinit var mapView: MapView
    // Флаг окончания загрузки карты
    private var isMapReady = false

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
            mapView.model.mapViewPosition.setCenter(LatLong(51.52964, 45.98008))
            mapView.model.mapViewPosition.zoomLevel = 15.toByte()

            // Применение темы к карте
            tileRendererLayer.setXmlRenderTheme(renderTheme)

            // Добавление слоя в MapView
            mapView.layerManager.layers.add(tileRendererLayer)

            // Карта готова к взаимодействию
            isMapReady = true

        }

        //
        if (intent?.getBooleanExtra("SELECT_LOCATION", false) == true) {
            // Режим выбора местоположения
            val currentLat = intent.getDoubleExtra("CURRENT_LAT", 0.0)
            val currentLon = intent.getDoubleExtra("CURRENT_LON", 0.0)
            showLocationSelectionMode()
            return
        }
        else { BasicMode() }

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
        mapView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    touchStartTime = System.currentTimeMillis()
                    isLongPressTriggered = false
                    isScrolling = false

                    isMarkerTouched = isTouchOnMarker(event)

                    if (!isMarkerTouched && isSelectionMode) {
                        handler.postDelayed({
                            if (!isScrolling) {
                                isLongPressTriggered = true
                                val tappedLatLong = mapView.mapViewProjection.fromPixels(
                                    event.x.toDouble(), event.y.toDouble()
                                )
                                showAddMarkerDialog(tappedLatLong)
                            }
                        }, 500)
                    }
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.x - touchStartX)
                    val dy = abs(event.y - touchStartY)

                    if (dx > touchSlop || dy > touchSlop) {
                        isScrolling = true
                        handler.removeCallbacksAndMessages(null)
                    }
                    false
                }

                MotionEvent.ACTION_UP -> {
                    if (!isLongPressTriggered && !isScrolling && !isMarkerTouched) {
                        handleClick(event)
                    }
                    cleanupTouch()
                    false
                }

                MotionEvent.ACTION_CANCEL -> {
                    cleanupTouch()
                    false
                }

                else -> false
            }
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

    private fun showAddMarkerDialog(latLong: LatLong) {
        AlertDialog.Builder(this)
            .setTitle("Добавить будильник")
            .setMessage("Координаты: ${latLong.latitude}, ${latLong.longitude}")
            .setPositiveButton("Добавить") { _, _ ->
                val marker = createInteractiveMarker(this, latLong, mapView)
                mapView.layerManager.layers.add(marker)
                //println("Маркер добавлен, количество слоёв: ${mapView.layerManager.layers.size()}") был для дебага, остался для уверенности
                //println("Координаты: ${marker.latLong}") братишка для дебага
                mapView.invalidate()

                // Переход на NewAlarmActivity с передачей координат
                val intent = Intent(this, NewAlarmActivity::class.java).apply {
                    putExtra("LATITUDE", latLong.latitude)
                    putExtra("LONGITUDE", latLong.longitude)
                }
                startActivity(intent)

                // Переключаемся обратно в базовый режим когда поставили метку
                BasicMode()
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
}
