package com.simplyroutine.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.pm.ServiceInfo
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.SystemClock
import android.widget.RemoteViews
import com.simplyroutine.data.expandEventsForDate
import java.time.LocalDate
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.updateAll
import com.simplyroutine.R
import com.simplyroutine.data.AppDatabase
import com.simplyroutine.data.Event
import com.simplyroutine.data.SettingsRepository
import com.simplyroutine.widget.TimetableWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TimekeeperService : Service() {
    private val CHANNEL_ID = "timekeeper_channel"
    private val NOTIFICATION_ID = 1

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private val db by lazy { AppDatabase.getInstance(this) }
    private val settingsRepo by lazy { SettingsRepository(this) }

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_TIME_TICK) updateAll()
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        // Re-run updateAll immediately whenever any setting changes (drop(1) skips initial emission)
        scope.launch {
            settingsRepo.settingsFlow.drop(1).collect { updateAll() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildSimpleNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, buildSimpleNotification())
        }
        updateAll()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(timeTickReceiver)
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateAll() {
        scope.launch {
            val settings = settingsRepo.settingsFlow.first()
            val allEvents = db.eventDao().getAllEventsOnce()
            val today = LocalDate.now()
            val events = expandEventsForDate(allEvents, today)
            val now = LocalTime.now()
            val curMin = now.hour * 60 + now.minute
            val nowSec = now.hour * 3600 + now.minute * 60 + now.second

            var nextEvent = events.filter { it.startMinutes > curMin }.minByOrNull { it.startMinutes }
            var secsUntilNext = if (nextEvent != null)
                (nextEvent.startMinutes * 60 - nowSec).coerceAtLeast(0).toLong()
            else 0L

            if (nextEvent == null && settings.showNextDayEvent) {
                nextEvent = expandEventsForDate(allEvents, today.plusDays(1)).firstOrNull()
                if (nextEvent != null)
                    secsUntilNext = (86400 - nowSec + nextEvent.startMinutes * 60).toLong()
            }

            notificationManager.notify(NOTIFICATION_ID, buildNotification(events, nextEvent, secsUntilNext, curMin, now))
            TimetableWidget().updateAll(this@TimekeeperService)
        }
    }

    private fun buildSimpleNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")))
            .setContentText("Simply Routine")
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun buildNotification(
        events: List<Event>,
        nextEvent: Event?,
        secsUntilNext: Long,
        curMin: Int,
        now: LocalTime,
    ): Notification {
        val currentEvent = events.firstOrNull { it.startMinutes <= curMin && it.endMinutes > curMin }

        val views = buildRemoteViews(currentEvent, nextEvent, secsUntilNext, now)

        // Collapsed text fallback (shown before the user expands, or on older launchers)
        val collapsedText = when {
            currentEvent != null -> currentEvent.title
            nextEvent != null -> nextEventText(nextEvent, curMin)
            else -> "No upcoming events"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(now.format(DateTimeFormatter.ofPattern("h:mm a")))
            .setContentText(collapsedText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .build()
    }

    private fun buildRemoteViews(
        currentEvent: Event?,
        nextEvent: Event?,
        secsUntilNext: Long,
        now: LocalTime,
    ): RemoteViews = RemoteViews(packageName, R.layout.notification_timetable).apply {
        val topBg    = if (currentEvent != null) darkenForBackground(currentEvent.color) else 0xFF1C1B1F.toInt()
        val bottomBg = if (nextEvent != null) darkenMore(nextEvent.color) else 0xFF0E0E0E.toInt()
        setInt(R.id.notification_top,    "setBackgroundColor", topBg)
        setInt(R.id.chronometer_next,    "setBackgroundColor", bottomBg)

        if (currentEvent != null) {
            setTextViewText(R.id.tv_current_event, currentEvent.title)
            setTextColor(R.id.tv_current_event, 0xFFFFFFFF.toInt())
            val start = formatMinutes(currentEvent.startMinutes)
            val end = formatMinutes(currentEvent.endMinutes)
            setTextViewText(R.id.tv_current_range, "$start – $end")
            setTextViewText(R.id.tv_current_label, "NOW")
        } else {
            setTextViewText(R.id.tv_current_label, now.format(DateTimeFormatter.ofPattern("h:mm a")))
            setTextViewText(R.id.tv_current_event, "Free")
            setTextColor(R.id.tv_current_event, 0x99FFFFFF.toInt())
            setTextViewText(R.id.tv_current_range, "No current event")
        }

        if (nextEvent != null) {
            setBoolean(R.id.chronometer_next, "setCountDown", true)
            setChronometer(
                R.id.chronometer_next,
                SystemClock.elapsedRealtime() + secsUntilNext * 1000L,
                "Next: ${nextEvent.title} in %s",
                true,
            )
        } else {
            setBoolean(R.id.chronometer_next, "setCountDown", false)
            setChronometer(R.id.chronometer_next, SystemClock.elapsedRealtime(), null, false)
            setTextViewText(R.id.chronometer_next, "No upcoming events")
        }
    }

    private fun nextEventText(event: Event, curMin: Int): String {
        val minsUntil = event.startMinutes - curMin
        val duration = if (minsUntil < 60) "${minsUntil}m" else "${minsUntil / 60}h ${minsUntil % 60}m"
        return "Next: ${event.title} in $duration"
    }

    private fun darkenForBackground(color: Int): Int {
        val r = ((color shr 16 and 0xFF) * 0.55f).toInt()
        val g = ((color shr 8  and 0xFF) * 0.55f).toInt()
        val b = ((color        and 0xFF) * 0.55f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun darkenMore(color: Int): Int {
        val r = ((color shr 16 and 0xFF) * 0.38f).toInt()
        val g = ((color shr 8  and 0xFF) * 0.38f).toInt()
        val b = ((color        and 0xFF) * 0.38f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60 % 24
        val m = minutes % 60
        val ampm = if (h < 12) "AM" else "PM"
        val display = if (h % 12 == 0) 12 else h % 12
        return "$display:${m.toString().padStart(2, '0')} $ampm"
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }
}
