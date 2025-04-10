package com.example.proximityalarmapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import kotlin.jvm.java

class AlarmsActivity : AppCompatActivity() {

    // --Commented out by Inspection (   15.03.25 1:46):private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarms)

        val btn_close : ImageButton = findViewById(R.id.btn_close_list)
        val closeButton = findViewById<ImageButton>(R.id.btn_close_alarm_info)
        val alarmCard = findViewById<CardView>(R.id.alarm_info_card)

        btn_close.setOnClickListener { finish() }

        closeButton.setOnClickListener {
            alarmCard.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    alarmCard.visibility = View.GONE
                    alarmCard.alpha = 1f // сброс прозрачности на случай повторного показа
                }
                .start()
        }
        // alarmCard.visibility = View.VISIBLE когда надо будет снова показать это окно
    }
}