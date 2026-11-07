package com.kaza.focusclock.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kaza.focusclock.data.AppDatabase
import java.time.LocalDate

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("task_id", 0L)
        if (taskId <= 0L) return
        val date = runCatching { LocalDate.parse(intent.getStringExtra("day_key")) }.getOrNull() ?: LocalDate.now()
        val title = intent.getStringExtra("title") ?: "Nhiệm vụ"
        val start = intent.getIntExtra("start_minute", 0)
        val end = intent.getIntExtra("end_minute", 0)
        if (intent.getStringExtra("action_type") == "SNOOZE") {
            ReminderScheduler.snooze(context, taskId, date, title, start, end)
            return
        }
        val db = AppDatabase(context)
        try {
            when (intent.getStringExtra("action_type")) {
                "COMPLETE" -> db.markTask(taskId, date, "COMPLETED", "Hoàn thành từ thông báo")
                "SKIP" -> db.markTask(taskId, date, "MISSED", "Bỏ qua từ thông báo")
            }
        } finally { db.close() }
    }
}
