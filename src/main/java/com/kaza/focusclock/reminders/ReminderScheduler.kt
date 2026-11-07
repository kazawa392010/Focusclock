package com.kaza.focusclock.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kaza.focusclock.data.AppDatabase
import com.kaza.focusclock.data.SettingsStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {
    fun cancelTask(context: Context, taskId: Long) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (offset in 0..8) {
            val date = LocalDate.now().plusDays(offset.toLong())
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.kaza.focusclock.REMINDER.${alarmId(taskId, date)}"
            }
            val flags = PendingIntent.FLAG_NO_CREATE or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            PendingIntent.getBroadcast(context, alarmId(taskId, date), intent, flags)?.let { alarms.cancel(it) }
        }
    }

    fun rescheduleAll(context: Context) {
        val db = AppDatabase(context)
        try {
            val settings = SettingsStore(context)
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val today = LocalDate.now()
            // Cancel every known template/date pair, even if a user just changed the repeat-day mask.
            db.getAllTemplates().forEach { task ->
                for (offset in 0..7) {
                    val date = today.plusDays(offset.toLong())
                    val pi = alarmPendingIntent(context, task.id, date, task.title, task.startMinute, task.endMinute, PendingIntent.FLAG_NO_CREATE)
                    if (pi != null) alarms.cancel(pi)
                }
            }
            if (!settings.reminderEnabled) return
            for ((date, task) in db.getTasksDueInRange(today, 8)) {
                if (task.reminderMinutes < 0) continue
                val reminderDateTime = date.atStartOfDay().plusMinutes((task.startMinute - task.reminderMinutes).toLong())
                val now = LocalDateTime.now()
                if (!reminderDateTime.isAfter(now)) continue
                val minute = reminderDateTime.hour * 60 + reminderDateTime.minute
                if (settings.isQuietAt(minute, reminderDateTime.dayOfWeek.value)) continue
                val triggerAt = reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val intent = alarmPendingIntent(context, task.id, date, task.title, task.startMinute, task.endMinute, PendingIntent.FLAG_UPDATE_CURRENT) ?: continue
                setAlarm(alarms, triggerAt, intent)
            }
        } finally {
            db.close()
        }
    }

    fun snooze(context: Context, taskId: Long, date: LocalDate, title: String, startMinute: Int, endMinute: Int) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = alarmPendingIntent(context, taskId, date, title, startMinute, endMinute, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val triggerAt = System.currentTimeMillis() + 5 * 60 * 1000L
        setAlarm(alarms, triggerAt, intent)
        val db = AppDatabase(context)
        try { db.logTaskEvent(taskId, date, "SNOOZED", "Hoãn 5 phút") } finally { db.close() }
    }

    private fun setAlarm(alarms: AlarmManager, triggerAt: Long, intent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
                return
            } catch (_: SecurityException) {
                // Permission may have been revoked between checking and scheduling.
            }
        }
        // Fallback is deliberately inexact when exact-alarm access is unavailable.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
    }

    private fun alarmPendingIntent(
        context: Context,
        taskId: Long,
        date: LocalDate,
        title: String,
        startMinute: Int,
        endMinute: Int,
        flag: Int
    ): PendingIntent? {
        val requestCode = alarmId(taskId, date)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.kaza.focusclock.REMINDER.$requestCode"
            putExtra("task_id", taskId)
            putExtra("day_key", date.toString())
            putExtra("title", title)
            putExtra("start_minute", startMinute)
            putExtra("end_minute", endMinute)
        }
        val flags = flag or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun alarmId(taskId: Long, date: LocalDate): Int = ("$taskId:$date").hashCode()
}
