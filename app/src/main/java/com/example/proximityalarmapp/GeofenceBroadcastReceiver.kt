package com.example.proximityalarmapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import android.os.Build
import android.util.Log

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Проверка разрешений для Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("GeofenceReceiver", "Precise location permission required on Android 12+")
                return
            }
        }
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        geofencingEvent?.let { event ->
            if (event.hasError()) {
                Log.e("GeofenceReceiver", "Geofencing error: ${event.errorCode}")
                return
            }
            if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                showNotification(context)
                // Останавливаем сервис после входа в геозону
                val serviceIntent = Intent(context, LocationTrackingService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }

    private fun showNotification(context: Context) {
        // Проверяем разрешение на отправку уведомлений (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("GeofenceReceiver", "Notification permission required on Android 13+")
                return
            }
        }

        val notification = NotificationCompat.Builder(context, LocationTrackingService.CHANNEL_ID)
            .setContentTitle("Вы в целевой зоне!")
            .setContentText("Вы вошли в заданную область")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1, notification)
        } catch (e: SecurityException) {
            Log.e("GeofenceReceiver", "Notification security exception: ${e.message}")
        } catch (e: Exception) {
            Log.e("GeofenceReceiver", "Notification error: ${e.message}")
        }
    }
}