import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker

object MapMarkerManager {
    private var userMarker: Marker? = null
    private var markerBitmap: Bitmap? = null

    fun initUserMarker(context: Context, @DrawableRes iconResId: Int) {
        if (markerBitmap != null) return // Уже инициализирован

        val drawable = ContextCompat.getDrawable(context, iconResId)
        drawable?.let {
            markerBitmap = AndroidGraphicFactory.convertToBitmap(it).apply {
                Log.d("MapMarker", "Bitmap: ${width}x${height}")
            }
        } ?: Log.e("MapMarker", "Drawable not found!")
    }

    fun updateUserLocation(mapView: MapView, latLong: LatLong) {
        if (markerBitmap == null) {
            Log.e("MapMarker", "Bitmap not initialized!")
            return
        }

        // Удаляем старый маркер
        userMarker?.let { mapView.layerManager.layers.remove(it) }

        // Создаём новый маркер
        userMarker = Marker(
            latLong,
            markerBitmap!!,
            0,
            -markerBitmap!!.height / 2 // Центрирование по вертикали
        ).apply {
        }

        // Добавляем маркер на карту
        mapView.layerManager.layers.add(userMarker)
        mapView.invalidate() // Принудительное обновление карты

        Log.d("MapMarker", "Marker added at $latLong")
    }

    fun removeUserMarker(mapView: MapView) {
        userMarker?.let {
            mapView.layerManager.layers.remove(it)
            userMarker = null
        }
    }
}