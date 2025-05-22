package com.example.proximityalarmapp

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Switch
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class AlarmAdapter(
    private val onClick: (Alarm) -> Unit,
    private val viewModel: AlarmViewModel
) : ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(AlarmDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.alarm, parent, false)
        return AlarmViewHolder(view, onClick, this::deleteAlarm) // Передаем функцию удаления
    }

    private fun deleteAlarm(alarm: Alarm, position: Int) {
        viewModel.deleteAlarm(alarm)
        notifyItemRemoved(position) // Вызываем в адаптере
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AlarmViewHolder(
        itemView: View,
        onClick: (Alarm) -> Unit,
        private val onDelete: (Alarm, Int) -> Unit // Функция для удаления
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvDescription: TextView = itemView.findViewById(R.id.tv_description)
        private val tvSchedule: TextView = itemView.findViewById(R.id.tv_schedule)
        private val tvDistance: TextView = itemView.findViewById(R.id.tv_distance)
        private val switchAlarm: Switch = itemView.findViewById(R.id.switch_alarm)
        private var currentAlarm: Alarm? = null

        init {
            itemView.setOnClickListener {
                currentAlarm?.let { onClick(it) }
            }
        }

        fun bind(alarm: Alarm) {
            currentAlarm = alarm
            tvDescription.text = alarm.title
            tvSchedule.text = formatSchedule(alarm.schedule)
            tvDistance.text = "В радиусе ${alarm.radius} метров"
            switchAlarm.isChecked = alarm.isEnabled

            itemView.findViewById<ImageView>(R.id.menu_button).setOnClickListener { v ->
                showPopupMenu(v, alarm)
            }
        }

        private fun showPopupMenu(view: View, alarm: Alarm) {
            val popup = PopupMenu(view.context, view)
            popup.inflate(R.menu.alarm_card_menu)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_delete -> {
                        confirmDelete(alarm)
                        true
                    }
                    R.id.action_edit -> true
                    else -> false
                }
            }
            popup.show()
        }

        private fun confirmDelete(alarm: Alarm) {
            AlertDialog.Builder(itemView.context)
                .setTitle("Удалить будильник?")
                .setMessage("Вы уверены, что хотите удалить '${alarm.title}'?")
                .setPositiveButton("Удалить") { _, _ ->
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onDelete(alarm, position) // Вызываем переданную функцию
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        private fun formatSchedule(schedule: List<DayOfWeek>): String {
            return when {
                schedule.size == 7 -> "Ежедневно"
                schedule.isEmpty() -> "Одноразовый"
                else -> schedule.joinToString(" ") {
                    when(it) {
                        DayOfWeek.MONDAY -> "Пн"
                        DayOfWeek.TUESDAY -> "Вт"
                        DayOfWeek.WEDNESDAY -> "Ср"
                        DayOfWeek.THURSDAY -> "Чт"
                        DayOfWeek.FRIDAY -> "Пт"
                        DayOfWeek.SATURDAY -> "Сб"
                        DayOfWeek.SUNDAY -> "Вс"
                    }
                }
            }
        }
    }
}

class AlarmDiffCallback : DiffUtil.ItemCallback<Alarm>() {
    override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm): Boolean {
        return oldItem == newItem
    }
}