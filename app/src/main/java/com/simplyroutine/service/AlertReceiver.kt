package com.simplyroutine.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.simplyroutine.R
import com.simplyroutine.data.AppDatabase
import kotlinx.coroutines.*

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId      = intent.getIntExtra("event_id", -1).takeIf { it >= 0 } ?: return
        val title        = intent.getStringExtra("event_title") ?: return
        val color        = intent.getIntExtra("event_color", 0xFF4CAF50.toInt())
        val startMin     = intent.getIntExtra("start_minutes", 0)
        val minutesBefore = intent.getIntExtra("minutes_before", 0)

        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(manager)

        val h = startMin / 60 % 24
        val m = startMin % 60
        val timeStr = "${if (h % 12 == 0) 12 else h % 12}:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
        val body = if (minutesBefore == 0) "Starting now" else "Starting at $timeStr"

        manager.notify(
            AlertScheduler.notificationId(eventId, minutesBefore),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setColor(color)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build(),
        )

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val event = AppDatabase.getInstance(context).eventDao().getEventById(eventId)
                if (event != null) AlertScheduler.scheduleOne(context, event, minutesBefore)
            } finally {
                pending.finish()
            }
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Event Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders for upcoming events"
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "timekeeper_alerts"
    }
}
