package com.kaza.focusclock.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kaza.focusclock.data.SettingsStore
import java.time.LocalDate
import java.time.LocalTime

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("task_id", 0L)
        val date = runCatching { LocalDate.parse(intent.getStringExtra("day_key")) }.getOrNull() ?: return
        val preferences = SettingsStore(context)
        if (!preferences.reminderEnabled) return
        val localTime = LocalTime.now()
        val minute = localTime.hour * 60 + localTime.minute
        if (preferences.isQuietAt(minute, LocalDate.now().dayOfWeek.value)) return
        val db = com.kaza.focusclock.data.AppDatabase(context)
        val activeTask = try { db.getTasksForDate(date).firstOrNull { it.id == id && it.status == "PENDING" } } finally { db.close() }
        if (activeTask == null) return
        NotificationHelper.showTaskReminder(
            context, id, date, intent.getStringExtra("title") ?: "Nhiệm vụ học tập",
            intent.getIntExtra("start_minute", 0), intent.getIntExtra("end_minute", 0)
        )
        // Roll the 8-day scheduling window forward whenever a reminder fires.
        ReminderScheduler.rescheduleAll(context.applicationContext)
    }
}
