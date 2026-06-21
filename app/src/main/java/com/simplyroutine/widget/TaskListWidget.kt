package com.simplyroutine.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.simplyroutine.MainActivity
import com.simplyroutine.data.AppDatabase
import com.simplyroutine.data.SettingsRepository
import com.simplyroutine.data.Task
import com.simplyroutine.data.urgencyScore
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class TaskListWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val settings = SettingsRepository(context).settingsFlow.first()

        val tasks = db.taskDao().getAllTasksOnce()
        val today = LocalDate.now()
        val sorted = tasks
            .sortedByDescending { it.urgencyScore(today) }
            .filter { task ->
                if (settings.widgetHideCompleted && task.lastCompletedDate == today) return@filter false
                if (settings.widgetHideDaysOut > 0 && task.frequencyUnit != "none") {
                    val daysLeft = daysUntilDue(task, today)
                    if (daysLeft != null && daysLeft >= settings.widgetHideDaysOut) return@filter false
                }
                true
            }

        provideContent {
            TaskListContent(sorted, today)
        }
    }
}

@Composable
private fun TaskListContent(tasks: List<Task>, today: LocalDate) {
    val context = LocalContext.current
    val openApp: Action = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            putExtra("open_tasks", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    )
    val bg = ColorProvider(Color(0xFF1C1B1F))
    val dimWhite = ColorProvider(Color.White.copy(alpha = 0.45f))

    LazyColumn(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .clickable(openApp),
    ) {
        item {
            Text(
                "TASKS",
                modifier = GlanceModifier.padding(
                    start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp,
                ),
                style = TextStyle(
                    color = dimWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        if (tasks.isEmpty()) {
            item {
                Text(
                    "No tasks yet",
                    modifier = GlanceModifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = TextStyle(color = dimWhite, fontSize = 12.sp),
                )
            }
        } else {
            items(tasks, itemId = { it.id.toLong() }) { task ->
                TaskRow(task, today, openApp)
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, today: LocalDate, openApp: Action) {
    val score = task.urgencyScore(today)
    val color = urgencyColor(score)
    val stripColor = ColorProvider(color)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(openApp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(30.dp)
                .background(stripColor),
        ) {}
        Box(modifier = GlanceModifier.width(9.dp)) {}
        Text(
            task.title,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 13.sp,
            ),
            maxLines = 1,
        )
        Box(modifier = GlanceModifier.width(6.dp)) {}
        Text(
            daysLabel(task, today),
            style = TextStyle(
                color = ColorProvider(color.copy(alpha = 0.85f)),
                fontSize = 11.sp,
            ),
        )
    }
}

private fun daysUntilDue(task: Task, today: LocalDate): Long? {
    val lastDone = task.lastCompletedDate ?: return null
    return when (task.frequencyUnit) {
        "months" -> ChronoUnit.DAYS.between(today, lastDone.plusMonths(task.frequencyDays.toLong()))
        else     -> task.frequencyDays.toLong() - ChronoUnit.DAYS.between(lastDone, today)
    }
}

private fun urgencyColor(score: Float): Color = when {
    score < 0f                                -> Color(0xFF64B5F6)  // blue — no schedule
    score == Float.MAX_VALUE || score >= 1.5f -> Color(0xFF9575CD)
    score >= 1.0f                             -> Color(0xFFFF8A65)
    score >= 0.7f                             -> Color(0xFFFFCC80)
    else                                      -> Color(0xFF81C784)
}

private fun daysLabel(task: Task, today: LocalDate): String {
    if (task.frequencyUnit == "none") return "—"
    val lastDone = task.lastCompletedDate ?: return "new"
    val daysSince = ChronoUnit.DAYS.between(lastDone, today)
    return when (task.frequencyUnit) {
        "months" -> {
            val dueDate = lastDone.plusMonths(task.frequencyDays.toLong())
            val daysLeft = ChronoUnit.DAYS.between(today, dueDate)
            when {
                daysSince == 0L -> "done"
                daysLeft < 0    -> "-${-daysLeft}d"
                daysLeft == 0L  -> "due"
                daysLeft < 45   -> "${daysLeft}d"
                else            -> "${(daysLeft / 30.5).toInt()}m"
            }
        }
        else -> {
            val daysLeft = task.frequencyDays - daysSince
            when {
                daysSince == 0L -> "done"
                daysLeft < 0    -> "-${-daysLeft}d"
                daysLeft == 0L  -> "due"
                else            -> "${daysLeft}d"
            }
        }
    }
}
