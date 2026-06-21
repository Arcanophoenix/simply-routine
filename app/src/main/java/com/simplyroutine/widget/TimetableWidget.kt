package com.simplyroutine.widget

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.util.TypedValue
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.simplyroutine.MainActivity
import com.simplyroutine.R
import com.simplyroutine.data.AppDatabase
import com.simplyroutine.data.Event
import com.simplyroutine.data.SettingsRepository
import com.simplyroutine.data.expandEventsForDate
import com.simplyroutine.data.forDate
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TimetableWidget : GlanceAppWidget() {
    // Exact mode lets Glance know the widget's exact dimensions at draw time
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        try {
            val db = AppDatabase.getInstance(context)
            val settings = SettingsRepository(context).settingsFlow.first()
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

            val todayOccasionTitles = db.occasionDao().getAllOccasionsOnce()
                .forDate(today)
                .map { it.title }

            provideContent {
                WidgetContent(events, now, nextEvent, secsUntilNext, todayOccasionTitles)
            }
        } catch (_: Exception) {
            provideContent {
                WidgetContent(emptyList(), LocalTime.now(), null, 0L, emptyList())
            }
        }
    }
}

private fun Color.darkenForBackground(): Color = Color(
    red = (red * 0.55f).coerceIn(0f, 1f),
    green = (green * 0.55f).coerceIn(0f, 1f),
    blue = (blue * 0.55f).coerceIn(0f, 1f),
    alpha = 1f,
)

private fun Color.darkenMore(): Color = Color(
    red = (red * 0.38f).coerceIn(0f, 1f),
    green = (green * 0.38f).coerceIn(0f, 1f),
    blue = (blue * 0.38f).coerceIn(0f, 1f),
    alpha = 1f,
)

// Occasion bar is visibly lighter than the event top section (0.55) to stand out
private fun Color.lightenForOccasion(): Color = Color(
    red = (red * 0.78f).coerceIn(0f, 1f),
    green = (green * 0.78f).coerceIn(0f, 1f),
    blue = (blue * 0.78f).coerceIn(0f, 1f),
    alpha = 1f,
)

// Pick white or near-black text based on perceived luminance of the background
private fun Color.contrastingText(): Color {
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return if (luminance > 0.35f) Color(0xFF1C1B1F) else Color.White
}

private fun formatTime(minutes: Int): String {
    val h = minutes / 60 % 24
    val m = minutes % 60
    val ampm = if (h < 12) "AM" else "PM"
    val display = if (h % 12 == 0) 12 else h % 12
    return "$display:${m.toString().padStart(2, '0')} $ampm"
}

// Font size scales inversely with title length so short titles fill the space
private fun titleFontSize(title: String) = when {
    title.length <= 5  -> 48.sp
    title.length <= 8  -> 40.sp
    title.length <= 12 -> 32.sp
    title.length <= 18 -> 24.sp
    title.length <= 26 -> 19.sp
    else               -> 15.sp
}

@Composable
private fun WidgetContent(
    events: List<Event>,
    now: LocalTime,
    nextEvent: Event?,
    secsUntilNext: Long,
    todayOccasionTitles: List<String>,
) {
    val curMin = now.hour * 60 + now.minute
    val timeLabel = now.format(DateTimeFormatter.ofPattern("h:mm a"))
    val currentEvent = events.firstOrNull { it.startMinutes <= curMin && it.endMinutes > curMin }

    val topBg    = if (currentEvent != null) Color(currentEvent.color).darkenForBackground()
                   else Color(0xFF1C1B1F)
    val bottomBg = if (nextEvent != null) Color(nextEvent.color).darkenMore()
                   else Color(0xFF0E0E0E)

    val white    = ColorProvider(Color.White)
    val dimWhite = ColorProvider(Color.White.copy(alpha = 0.6f))

    val context = LocalContext.current
    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    )

    val bottomStripH = 44.dp

    val packageName = context.packageName
    val countdownViews = android.widget.RemoteViews(packageName, R.layout.widget_countdown).apply {
        if (nextEvent != null) {
            setBoolean(R.id.widget_chronometer, "setCountDown", true)
            setChronometer(
                R.id.widget_chronometer,
                SystemClock.elapsedRealtime() + secsUntilNext * 1000L,
                "Next: ${nextEvent.title} in %s",
                true,
            )
        } else {
            setBoolean(R.id.widget_chronometer, "setCountDown", false)
            setChronometer(R.id.widget_chronometer, SystemClock.elapsedRealtime(), null, false)
            setTextViewText(R.id.widget_chronometer, "No upcoming events")
        }
    }

    // Hoist occasion computation so we can derive heights before layout
    val dm = context.resources.displayMetrics
    val availableWidthPx = (LocalSize.current.width - 24.dp).value * dm.density
    val dotJoined = todayOccasionTitles.joinToString(" · ")
    var occasionFontSp = 13f
    var occasionMaxLines = 1

    if (todayOccasionTitles.isNotEmpty()) {
        val paint = Paint()
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, occasionFontSp, dm)
        while (paint.measureText(dotJoined) > availableWidthPx && occasionFontSp > 10f) {
            occasionFontSp -= 0.5f
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, occasionFontSp, dm)
        }
        if (paint.measureText(dotJoined) > availableWidthPx) {
            occasionMaxLines = 2
            occasionFontSp = 13f
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, occasionFontSp, dm)
            while (paint.measureText(dotJoined) > availableWidthPx * 2) {
                occasionFontSp -= 0.5f
                paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, occasionFontSp, dm)
            }
        }
    }

    val occasionBarH = when {
        todayOccasionTitles.isEmpty() -> 0.dp
        occasionMaxLines == 2 -> 60.dp
        else -> 40.dp
    }
    val topH = LocalSize.current.height - bottomStripH - occasionBarH

    Column(modifier = GlanceModifier.fillMaxSize().clickable(openApp)) {

        // ── Occasion bar: shown only on days with an occasion ──
        if (todayOccasionTitles.isNotEmpty()) {
            val occasionBg = if (currentEvent != null)
                Color(currentEvent.color).lightenForOccasion()
            else
                Color(0xFF3D3560)
            val occasionText = occasionBg.contrastingText()

            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(occasionBarH)
                    .background(ColorProvider(occasionBg))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dotJoined,
                    style = TextStyle(
                        color = ColorProvider(occasionText),
                        fontSize = occasionFontSp.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = occasionMaxLines,
                )
            }
        }

        // ── Top: current event, fills remaining space ──
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(topH)
                .background(ColorProvider(topBg)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                Text(
                    text = timeLabel,
                    style = TextStyle(color = dimWhite, fontSize = 11.sp, textAlign = TextAlign.Center),
                )
                Box(modifier = GlanceModifier.height(6.dp)) {}
                val title = currentEvent?.title ?: "Free"
                Text(
                    text = title,
                    style = TextStyle(
                        color = white,
                        fontSize = titleFontSize(title),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
                if (currentEvent != null) {
                    Box(modifier = GlanceModifier.height(4.dp)) {}
                    Text(
                        text = "${formatTime(currentEvent.startMinutes)} – ${formatTime(currentEvent.endMinutes)}",
                        style = TextStyle(color = dimWhite, fontSize = 11.sp, textAlign = TextAlign.Center),
                    )
                }
            }
        }

        // ── Bottom strip: Chronometer ticks in system UI process — zero battery cost ──
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(bottomStripH)
                .background(ColorProvider(bottomBg)),
            contentAlignment = Alignment.Center,
        ) {
            AndroidRemoteViews(remoteViews = countdownViews, modifier = GlanceModifier.fillMaxSize())
        }
    }
}
