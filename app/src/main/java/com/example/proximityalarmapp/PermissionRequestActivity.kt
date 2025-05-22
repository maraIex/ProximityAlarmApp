package com.example.proximityalarmapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.Manifest

class PermissionRequestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = intent.getStringArrayListExtra("permissions")?.toTypedArray()
            ?: arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

        ActivityCompat.requestPermissions(
            this,
            permissions,
            REQUEST_CODE_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            // Перезапускаем сервис после получения разрешений
            startService(Intent(this, LocationTrackingService::class.java))
        }
        finish()
    }

    companion object {
        const val REQUEST_CODE_PERMISSION = 1001
    }
}