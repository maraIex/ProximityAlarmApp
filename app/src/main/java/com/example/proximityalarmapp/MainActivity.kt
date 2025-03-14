package com.example.proximityalarmapp

//Бибилиотек для рисования маркера на карте
// Импорт для биндингов
// Импорт дял навигации
// Импорты для MapsForge. Карты, андроид утилиты, офлайн рендерер, и считывание файлов
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback
import org.mapsforge.map.rendertheme.XmlThemeResourceProvider
import java.io.File
import java.io.InputStream


class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Подключение рендерера
        AndroidGraphicFactory.createInstance(applicationContext)

        //Инициализация карты
        val mapView = findViewById<MapView>(R.id.map)
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

        //Нахождение файла карты(вшитой в проект)
        val assetManager = assets
        //Открытие файла карт из папки assets
        val file = File(cacheDir, "SaratovZone.map")
        file.outputStream().use { output ->
            assetManager.open("SaratovZone.map").copyTo(output)
        }
        // Инициализация MapDataStore
        val mapDataStore: MapDataStore = MapFile(file)
        //Рендеринг слоев в карте
        val tileRendererLayer = TileRendererLayer(tileCache, mapDataStore,
            mapView.model.mapViewPosition, AndroidGraphicFactory.INSTANCE)
        val renderTheme = object : XmlRenderTheme {
            private var menuCallback: XmlRenderThemeMenuCallback? = null
            private var resourceProvider: XmlThemeResourceProvider? = null

            override fun getRelativePathPrefix(): String {
                return ""
            }

            override fun getRenderThemeAsStream(): InputStream {
                return assets.open("vtm/default.xml")
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
        // Применение темы к карте
        tileRendererLayer.setXmlRenderTheme(renderTheme)

        // Добавление слоя в MapView
        mapView.layerManager.layers.add(tileRendererLayer)
        //Добавление слоев в наш MapView
        mapView.layerManager.layers.add(tileRendererLayer)
        Log.d("MainActivity", "TileRendererLayer added to MapView")

        // Добавление в переменную нарисованного курсора
        val drawable: Drawable? = ContextCompat.getDrawable(this, R.drawable.marker)
        val bitmap: Bitmap = AndroidGraphicFactory.convertToBitmap(drawable)

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
}
