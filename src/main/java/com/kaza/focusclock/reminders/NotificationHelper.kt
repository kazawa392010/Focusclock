package com.kaza.focusclock.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.Notification
import com.kaza.focusclock.MainActivity
import com.kaza.focusclock.R
import com.kaza.focusclock.util.TimeUtils
import java.time.LocalDate

object NotificationHelper {
    private const val CHANNEL_ID = "study_reminders"

    fun showTaskReminder(context: Context, taskId: Long, date: LocalDate, title: String, startMinute: Int, endMinute: Int) {
        createChannel(context)
        val notificationId = ("$taskId:$date").hashCode()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_task_id", taskId)
            putExtra("open_date", date.toString())
        }
        val openPending = PendingIntent.getActivity(context, notificationId, openIntent, immutableFlag(PendingIntent.FLAG_UPDATE_CURRENT))
        val complete = actionPending(context, notificationId + 1, "COMPLETE", taskId, date, title, startMinute, endMinute)
        val snooze = actionPending(context, notificationId + 2, "SNOOZE", taskId, date, title, startMinute, endMinute)
        val skip = actionPending(context, notificationId + 3, "SKIP", taskId, date, title, startMinute, endMinute)
        val journalIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_journal", true)
            putExtra("open_date", date.toString())
        }
        val journal = PendingIntent.getActivity(context, notificationId + 4, journalIntent, immutableFlag(PendingIntent.FLAG_UPDATE_CURRENT))
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Đến giờ học")
            .setContentText("${TimeUtils.formatMinute(startMinute)} · $title")
            .setStyle(Notification.BigTextStyle().bigText("${TimeUtils.formatMinute(startMinute)}–${TimeUtils.formatMinute(endMinute)} · $title"))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .addAction(Notification.Action.Builder(android.R.drawable.checkbox_on_background, "Hoàn thành", complete).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_lock_idle_alarm, "Hoãn 5 phút", snooze).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Bỏ qua", skip).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_edit, "Mở nhật ký", journal).build())
            .build()
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(notificationId, notification) }
    }

    private fun actionPending(context: Context, request: Int, action: String, taskId: Long, date: LocalDate, title: String, start: Int, end: Int): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = "com.kaza.focusclock.$action"
            putExtra("action_type", action); putExtra("task_id", taskId); putExtra("day_key", date.toString())
            putExtra("title", title); putExtra("start_minute", start); putExtra("end_minute", end)
        }
        return PendingIntent.getBroadcast(context, request, intent, immutableFlag(PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Nhắc nhở học tập", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Thông báo cho lịch học và nhiệm vụ Focus Clock"
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun immutableFlag(base: Int): Int = base or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
}
