package com.example.proximityalarmapp

import android.Manifest
import android.annotation.SuppressLint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.app.*
import android.content.Context
import com.google.android.gms.location.Geofence
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LocationTrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var locationCallback: LocationCallback
    internal val locationUpdateListener = mutableListOf<(Location) -> Unit>()
    private var currentMode = TrackingMode.LOW_POWER

    companion object {
        //  центр Саратовской области
        const val TARGET_LAT = 51.602578   // Широта
        const val TARGET_LNG = 46.007720   // Долгота
        const val CHANNEL_ID = "location_channel"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)
        createNotificationChannel()
        setupLocationCallback()
        startLowPowerTracking()
    }

    //Подписка на маркера
    fun addLocationListener(listener: (Location) -> Unit) {
        locationUpdateListener.add(listener)
    }
    //Отписка от маркера
    fun removeLocationListener(listener: (Location) -> Unit) {
        locationUpdateListener.remove(listener)
    }
    inner class LocalBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }

    override fun onBind(intent: Intent): IBinder {
        return LocalBinder()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    Log.d("LocationService", "...")

                    if (!location.isValid()) {
                        Log.w("LocationService", "Invalid location")
                        return@forEach
                    }

                    // Важное исправление: оповещаем всех listeners
                    locationUpdateListener.forEach { listener ->
                        listener(location)
                    }
                }
            }
        }
    }

    private fun Location.isValid(): Boolean {
        return latitude in -90.0..90.0 &&
                longitude in -180.0..180.0 &&
                elapsedRealtimeNanos > SystemClock.elapsedRealtimeNanos() - TimeUnit.MINUTES.toNanos(15)
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

    private fun startLowPowerTracking() {
        if (!hasLocationPermission()) {
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, 600_000L)
            .setMinUpdateIntervalMillis(300_000L)
            .setWaitForAccurateLocation(true)
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

    // LocationTrackingService.kt
    private fun startForegroundServiceWithNotification() {
        val notification = getNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(1, notification)
            }
            Log.d("Service", "Foreground service started") // Добавлено
        } catch (e: Exception) {
            Log.e("Service", "Foreground start error: ${e.message}") // Добавлено
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
            Log.d("Service", "Location updates stopped")
        } catch (e: Exception) {
            Log.e("Service", "Error in onDestroy: ${e.message}")
        }
        unbindAllListeners()
    }

    private fun unbindAllListeners() {
        locationUpdateListener.clear()
        Log.d("Service", "All listeners unbound")
    }
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
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        Log.d("LocationWorker", "Worker started")

        if (!checkLocationPermissions()) {
            Log.w("LocationWorker", "Location permissions denied")
            return Result.success() // или Result.failure() в зависимости от логики
        }

        if (!isLocationEnabled()) {
            Log.w("LocationWorker", "Location services disabled")
            return Result.retry()
        }

        val result = AtomicReference<Result>()
        val resultRef = AtomicReference<Result>()
        val latch = CountDownLatch(1)
        var locationCallback: LocationCallback? = null

        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            Log.w("LocationWorker", "Location request timeout")
            result.set(Result.retry())
            latch.countDown()
        }

        try {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                TimeUnit.SECONDS.toMillis(5) // Исправлено: закрывающая скобка для toMillis()
            ).build() // Исправлено: вызов build() после Builder

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        Log.d("LocationWorker", "Location received: $location")
                        handler.removeCallbacks(timeoutRunnable)
                        if (isNearTarget(location)) { // Исправлено: убрана лишняя скобка
                            startLocationService()
                            resultRef.set(Result.success()) // Переименовано во избежание конфликта имён
                        } else {
                            resultRef.set(Result.success())
                        }
                        latch.countDown()
                    }
                }
            }
            handler.postDelayed(timeoutRunnable, 10_000L)

            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback, // Безопасная проверка
                Looper.getMainLooper()
            )

            latch.await(10_000L, TimeUnit.MILLISECONDS)
            return resultRef.get() ?: Result.retry()

        } catch (e: SecurityException) {
            Log.e("LocationWorker", "SecurityException: ${e.message}")
            return Result.failure()
        } catch (e: Exception) {
            Log.e("LocationWorker", "Error: ${e.message}")
            return Result.retry()
        } finally {
            locationCallback?.let {
                fusedLocationClient.removeLocationUpdates(it)
            }
            handler.removeCallbacks(timeoutRunnable)
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
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