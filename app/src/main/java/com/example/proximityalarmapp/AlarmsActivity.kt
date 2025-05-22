package com.example.proximityalarmapp


import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.jvm.java

class AlarmsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AlarmAdapter

    // Инициализация AlarmViewModel
    private val viewModel by lazy {
        (application as ProximityAlarm).appContainer.alarmViewModel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarms)

        val btn_close : ImageButton = findViewById(R.id.btn_close_list)
        val closeButton = findViewById<ImageButton>(R.id.btn_close_alarm_info)
        val alarmCard = findViewById<CardView>(R.id.alarm_info_card)
        val addButton = findViewById<ImageButton>(R.id.btn_add_alarm)

        // Инициализация RecyclerView
        recyclerView = findViewById(R.id.recycler_alarms)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = AlarmAdapter(
            onClick = { alarm -> showAlarmDetails(alarm) },
            viewModel = viewModel
        )
        recyclerView.adapter = adapter

        // Подписка на изменения списка будильников
        viewModel.alarms.observe(this) { alarms ->
            adapter.submitList(alarms)
        }

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

        addButton.setOnClickListener {
            val intent = Intent(this, NewAlarmActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showAlarmDetails(alarm: Alarm) {
        val alarmCard = findViewById<CardView>(R.id.alarm_info_card)
        val alarmTitle = findViewById<TextView>(R.id.alarm_title)
        val alarmDistance = findViewById<TextView>(R.id.alarm_distance)

        alarmTitle.text = alarm.title
        alarmDistance.text = "В радиусе ${alarm.radius} метров"

        alarmCard.visibility = View.VISIBLE
        alarmCard.alpha = 0f
        alarmCard.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }
}