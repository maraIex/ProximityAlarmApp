package com.example.proximityalarmapp

import android.Manifest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.app.*
import android.content.Context
import com.google.android.gms.location.Geofence
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.*
import kotlinx.coroutines.tasks.await

class LocationTrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var locationCallback: LocationCallback
    private var currentMode = TrackingMode.LOW_POWER

    companion object {
        const val TARGET_LAT = 55.751244
        const val TARGET_LNG = 37.618423
        const val CHANNEL_ID = "location_channel"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)
        createNotificationChannel()
        initLocationCallback()
        startLowPowerTracking()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Tracking your location" }

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun getNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Tracking")
            .setContentText("Tracking your location in background")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun initLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    checkDistance(location)
                }
            }
        }
    }

    private fun startLowPowerTracking() {
        if (!hasLocationPermission()) {
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, 600_000L)
            .setMinUpdateIntervalMillis(300_000L)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
            startForegroundServiceWithNotification()
        } catch (e: SecurityException) {
            Log.e("Location", "Permission denied: ${e.message}")
            stopSelf()
        }
    }

    private fun startForegroundServiceWithNotification() {
        val notification = getNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkDistance(location: Location) {
        val targetLocation = Location("").apply {
            latitude = TARGET_LAT
            longitude = TARGET_LNG
        }
        val distance = location.distanceTo(targetLocation)

        when {
            distance < 1000 -> activateGeofencing()
            distance < 5000 -> switchToBalancedMode()
        }
    }

    private fun switchToBalancedMode() {
        if (currentMode == TrackingMode.BALANCED || !hasLocationPermission()) return
        currentMode = TrackingMode.BALANCED

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)

            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L)
                .setMinUpdateIntervalMillis(30_000L)
                .build()

            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("Location", "Permission denied in balanced mode: ${e.message}")
        }
    }

    private fun activateGeofencing() {
        if (currentMode == TrackingMode.HIGH_ACCURACY || !hasLocationPermission()) return
        currentMode = TrackingMode.HIGH_ACCURACY

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)

            val geofence = Geofence.Builder()
                .setRequestId("target_geofence")
                .setCircularRegion(TARGET_LAT, TARGET_LNG, 200f)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setNotificationResponsiveness(3000)
                .build()

            geofencingClient.addGeofences(
                GeofencingRequest.Builder()
                    .addGeofence(geofence)
                    .build(),
                createGeofencePendingIntent()
            )
        } catch (e: SecurityException) {
            Log.e("Geofence", "Permission denied for geofencing: ${e.message}")
        }
    }

    private fun createGeofencePendingIntent(): PendingIntent {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            geofencingClient.removeGeofences(createGeofencePendingIntent())
        } catch (e: Exception) {
            Log.e("Service", "Error stopping location updates: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

enum class TrackingMode { LOW_POWER, BALANCED, HIGH_ACCURACY }

// Сервис для проверки работы фоновой системы

class LocationCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    companion object {
        const val TARGET_LAT = 55.751244
        const val TARGET_LNG = 37.618423
        const val WORK_TAG = "location_check_work"
        private const val NEARBY_RADIUS_METERS = 5000 // 5 км
    }
    override suspend fun doWork(): Result {
        return try {
            if (!checkLocationPermissions()) {
                Log.d("LocationWorker", "No location permissions")
                return Result.success()
            }

            val lastLocation = getLastLocation() ?: return Result.retry()

            if (isNearTarget(lastLocation)) {
                Log.d("LocationWorker", "User is near target - starting service")
                startLocationService()
                Result.success()
            } else {
                Log.d("LocationWorker", "User is far from target")
                Result.success()
            }
        } catch (e: SecurityException) {
            Log.e("LocationWorker", "SecurityException: ${e.message}")
            Result.failure()
        } catch (e: Exception) {
            Log.e("LocationWorker", "Error: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun getLastLocation(): Location? {
        return try {
            // Дополнительная проверка на случай, если разрешения были отозваны
            if (!checkLocationPermissions()) {
                return null
            }
            fusedLocationClient.lastLocation.await()
        } catch (e: SecurityException) {
            Log.e("LocationWorker", "SecurityException in getLastLocation: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e("LocationWorker", "Error in getLastLocation: ${e.message}")
            null
        }
    }

    private fun isNearTarget(location: Location): Boolean {
        val targetLocation = Location("").apply {
            latitude = TARGET_LAT
            longitude = TARGET_LNG
        }
        return location.distanceTo(targetLocation) < NEARBY_RADIUS_METERS
    }

    private fun startLocationService() {
        try {
            val intent = Intent(applicationContext, LocationTrackingService::class.java)

            applicationContext.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e("LocationWorker", "Failed to start service: ${e.message}")
        }
    }

    private fun checkLocationPermissions(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Для Android 10+ нужно фоновое разрешение
        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // Для Android 12+ нужно разрешение на точные геозоны
        val hasPreciseGeofence = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasFineLocation
        } else {
            true
        }

        return (hasFineLocation || hasCoarseLocation) && hasBackgroundLocation && hasPreciseGeofence
    }
}