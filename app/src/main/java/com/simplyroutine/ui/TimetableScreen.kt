package com.simplyroutine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Dialog
import com.simplyroutine.data.Event
import com.simplyroutine.data.RepeatType
import com.simplyroutine.data.Settings
import com.simplyroutine.data.Task
import com.simplyroutine.data.expandEventsForDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.launch

private const val PX_PER_MIN = 1.5f
private const val LABEL_W = 40f        // dp
private const val TIME_PADDING_DP = 14f // dp buffer above day-start and below day-end so edge labels aren't clipped
private const val INITIAL_PAGE = 500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    events: List<Event>,
    tasks: List<Task>,
    settings: Settings,
    currentTime: LocalTime,
    onAddEvent: (date: LocalDate, startMinutes: Int) -> Unit,
    onEditEvent: (Event) -> Unit,
    onEditOccurrence: (event: Event, date: LocalDate) -> Unit,
    onAddTask: (Task) -> Unit,
    onUpdateTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onPinWidget: () -> Unit,
    onNavigateToSettings: () -> Unit,
    tourState: TourState? = null,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val pxPerMin = with(density) { PX_PER_MIN.dp.toPx() }
    val labelWPx = with(density) { LABEL_W.dp.toPx() }
    val timePaddingPx = with(density) { TIME_PADDING_DP.dp.toPx() }
    val totalMin = (settings.dayEndMinutes - settings.dayStartMinutes).coerceAtLeast(1)
    val totalHeightDp = with(density) { (totalMin * pxPerMin + timePaddingPx * 2).toDp() }
    val today = remember { LocalDate.now() }
    val todayWeekStart = remember { today.with(DayOfWeek.MONDAY) }

    fun pageToWeekStart(page: Int): LocalDate =
        todayWeekStart.plusWeeks((page - INITIAL_PAGE).toLong())

    val pagerState = rememberPagerState(initialPage = INITIAL_PAGE) { INITIAL_PAGE * 2 + 1 }
    val currentWeekStart by remember { derivedStateOf { pageToWeekStart(pagerState.currentPage) } }

    val todayHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)

    val coroutineScope = rememberCoroutineScope()

    // Overlap disambiguation (safety net — overlapping events are blocked at add time)
    var eventsToChoose by remember { mutableStateOf<List<Event>?>(null) }
    // Recurring-event edit scope picker
    var recurringTap by remember { mutableStateOf<Pair<Event, LocalDate>?>(null) }
    // Month picker
    var showMonthPicker by remember { mutableStateOf(false) }
    // Task tracker
    var showTaskTracker by remember { mutableStateOf(false) }
    var pickerMonth by remember { mutableStateOf(YearMonth.now()) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    val end = currentWeekStart.plusDays(6)
                    val fmt = DateTimeFormatter.ofPattern("MMM d")
                    val fmtYear = DateTimeFormatter.ofPattern("MMM d, yyyy")
                    val title = if (currentWeekStart.year == end.year)
                        "${currentWeekStart.format(fmt)} – ${end.format(fmtYear)}"
                    else
                        "${currentWeekStart.format(fmtYear)} – ${end.format(fmtYear)}"
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clickable {
                                pickerMonth = YearMonth.from(currentWeekStart)
                                showMonthPicker = true
                            }
                            .onGloballyPositioned { tourState?.titleBounds = it.boundsInRoot() },
                    )
                },
                actions = {
                    IconButton(onClick = { showTaskTracker = true }) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = "Task tracker")
                    }
                    IconButton(
                        onClick = onPinWidget,
                        modifier = Modifier.onGloballyPositioned { tourState?.widgetBtnBounds = it.boundsInRoot() },
                    ) {
                        Icon(Icons.Default.Widgets, contentDescription = "Add widget to home screen")
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.onGloballyPositioned { tourState?.settingsBtnBounds = it.boundsInRoot() },
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val curMin = currentTime.hour * 60 + currentTime.minute
                    onAddEvent(today, curMin)
                },
                modifier = Modifier.onGloballyPositioned { tourState?.fabBounds = it.boundsInRoot() },
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add event")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WeekDayHeader(
                weekStart = currentWeekStart,
                today = today,
                labelWidthDp = LABEL_W.dp,
            )

            val scrollState = rememberScrollState()
            val resetScrollOnWeekChange = remember { settings.resetScrollOnWeekChange }

            LaunchedEffect(Unit) {
                val curMin = LocalTime.now().hour * 60 + LocalTime.now().minute
                val curY = timePaddingPx + (curMin - settings.dayStartMinutes) * pxPerMin
                val target = (curY - 120 * pxPerMin).coerceAtLeast(0f).toInt()
                scrollState.scrollTo(target)
            }

            LaunchedEffect(Unit) {
                var prevPage = pagerState.currentPage
                snapshotFlow { pagerState.currentPage }.collect { page ->
                    if (page != prevPage && resetScrollOnWeekChange) {
                        scrollState.scrollTo(0)
                    }
                    prevPage = page
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { tourState?.pagerBounds = it.boundsInRoot() },
                beyondViewportPageCount = 1,
                key = { it },
            ) { page ->
                val weekStart = pageToWeekStart(page)
                val weekEvents = remember(events, weekStart.toEpochDay()) {
                    (0..6).map { offset ->
                        expandEventsForDate(events, weekStart.plusDays(offset.toLong()))
                    }
                }

                val isCurrentWeek = weekStart == todayWeekStart

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(totalHeightDp)
                            .onGloballyPositioned { coords ->
                                if (page == pagerState.currentPage) {
                                    tourState?.canvasBounds = coords.boundsInRoot()
                                }
                            }
                            .pointerInput(weekEvents, weekStart) {
                                val dayW = (size.width - labelWPx) / 7f
                                detectTapGestures { offset ->
                                    if (offset.x < labelWPx) return@detectTapGestures
                                    val dayIndex = ((offset.x - labelWPx) / dayW)
                                        .toInt().coerceIn(0, 6)
                                    val tapMin = ((offset.y - timePaddingPx) / pxPerMin + settings.dayStartMinutes)
                                        .toInt()
                                        .coerceIn(settings.dayStartMinutes, settings.dayEndMinutes - 1)
                                    val tapDate = weekStart.plusDays(dayIndex.toLong())
                                    val tapped = weekEvents[dayIndex].filter { event ->
                                        val top = timePaddingPx + (event.startMinutes - settings.dayStartMinutes) * pxPerMin
                                        val bottom = timePaddingPx + (event.endMinutes - settings.dayStartMinutes) * pxPerMin
                                        offset.y in top..bottom
                                    }
                                    // Occurrences are copies with a stamped date — resolve back to
                                    // the base event so editing never moves a recurring event's date.
                                    fun baseOf(occ: Event) = events.find { it.id == occ.id } ?: occ
                                    when (tapped.size) {
                                        0 -> if (settings.tapToAdd) onAddEvent(tapDate, tapMin)
                                        1 -> {
                                            val base = baseOf(tapped.first())
                                            if (base.recurrence != RepeatType.NONE)
                                                recurringTap = Pair(base, tapDate)
                                            else
                                                onEditEvent(base)
                                        }
                                        else -> eventsToChoose = tapped.map { baseOf(it) }
                                    }
                                }
                            },
                    ) {
                        drawWeekGrid(
                            settings = settings,
                            weekEvents = weekEvents,
                            weekStart = weekStart,
                            today = today,
                            currentTime = currentTime,
                            pxPerMin = pxPerMin,
                            labelWPx = labelWPx,
                            timePaddingPx = timePaddingPx,
                            todayHighlight = todayHighlight,
                            measurer = textMeasurer,
                        )
                    }
                }
            }
        }
    }

    recurringTap?.let { (event, date) ->
        val fmt = DateTimeFormatter.ofPattern("EEE, MMM d")
        AlertDialog(
            onDismissRequest = { recurringTap = null },
            title = { Text("Recurring event") },
            text = { Text("Edit just ${date.format(fmt)}, or all occurrences of \"${event.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    recurringTap = null
                    onEditOccurrence(event, date)
                }) { Text("This day only") }
            },
            dismissButton = {
                TextButton(onClick = {
                    recurringTap = null
                    onEditEvent(event)
                }) { Text("All occurrences") }
            },
        )
    }

    eventsToChoose?.let { choices ->
        AlertDialog(
            onDismissRequest = { eventsToChoose = null },
            title = { Text("Select event to edit") },
            text = {
                Column {
                    choices.forEach { event ->
                        TextButton(
                            onClick = { eventsToChoose = null; onEditEvent(event) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = buildString {
                                    append(event.title)
                                    append("  ")
                                    append(
                                        LocalTime.of(event.startMinutes / 60 % 24, event.startMinutes % 60)
                                            .format(DateTimeFormatter.ofPattern("h:mm a"))
                                    )
                                    append(" – ")
                                    append(
                                        LocalTime.of(event.endMinutes / 60 % 24, event.endMinutes % 60)
                                            .format(DateTimeFormatter.ofPattern("h:mm a"))
                                    )
                                },
                                color = Color(event.color),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { eventsToChoose = null }) { Text("Cancel") }
            },
        )
    }

    if (showTaskTracker) {
        TaskTrackerDialog(
            tasks = tasks,
            onDismiss = { showTaskTracker = false },
            onAdd = onAddTask,
            onUpdate = onUpdateTask,
            onDelete = onDeleteTask,
        )
    }

    if (showMonthPicker) {
        Dialog(onDismissRequest = { showMonthPicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Month navigation header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { pickerMonth = pickerMonth.minusMonths(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                        }
                        Text(
                            pickerMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = { pickerMonth = pickerMonth.plusMonths(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                        }
                    }

                    // Day-of-week header
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Calendar grid — always 6 rows
                    val gridStart = pickerMonth.atDay(1)
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    repeat(6) { weekOffset ->
                        val rowMonday = gridStart.plusWeeks(weekOffset.toLong())
                        val isCurrentWeekRow = rowMonday == currentWeekStart
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrentWeekRow) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else Color.Transparent,
                                    RoundedCornerShape(50),
                                ),
                        ) {
                            repeat(7) { dayOffset ->
                                val date = rowMonday.plusDays(dayOffset.toLong())
                                val inMonth = YearMonth.from(date) == pickerMonth
                                val isToday = date == today
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clickable {
                                            val weeksBetween = ChronoUnit.WEEKS.between(
                                                todayWeekStart,
                                                date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                                            ).toInt()
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(INITIAL_PAGE + weeksBetween)
                                            }
                                            showMonthPicker = false
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .background(
                                                if (isToday) MaterialTheme.colorScheme.primary
                                                else Color.Transparent,
                                                CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "${date.dayOfMonth}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when {
                                                isToday -> MaterialTheme.colorScheme.onPrimary
                                                inMonth -> MaterialTheme.colorScheme.onSurface
                                                else    -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekDayHeader(
    weekStart: LocalDate,
    today: LocalDate,
    labelWidthDp: Dp,
) {
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    HorizontalDivider(thickness = 0.5.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 4.dp),
    ) {
        Spacer(Modifier.width(labelWidthDp))
        for (i in 0..6) {
            val date = weekStart.plusDays(i.toLong())
            val isToday = date == today
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = dayNames[i],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

private fun DrawScope.drawWeekGrid(
    settings: Settings,
    weekEvents: List<List<Event>>,
    weekStart: LocalDate,
    today: LocalDate,
    currentTime: LocalTime,
    pxPerMin: Float,
    labelWPx: Float,
    timePaddingPx: Float,
    todayHighlight: Color,
    measurer: TextMeasurer,
) {
    val w = size.width
    val h = size.height
    val dayW = (w - labelWPx) / 7f
    val todayIndex = (today.toEpochDay() - weekStart.toEpochDay()).toInt()

    // Today column highlight (only in the schedulable zone)
    if (todayIndex in 0..6) {
        drawRect(
            color = todayHighlight,
            topLeft = Offset(labelWPx + todayIndex * dayW, timePaddingPx),
            size = Size(dayW, h - timePaddingPx * 2),
        )
    }

    // Vertical day dividers
    for (i in 0..7) {
        val x = labelWPx + i * dayW
        drawLine(
            color = Color.Gray.copy(alpha = 0.18f),
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 1f,
        )
    }

    // Horizontal hour lines + time labels
    var minute = settings.dayStartMinutes + (60 - settings.dayStartMinutes % 60) % 60
    while (minute <= settings.dayEndMinutes) {
        val y = timePaddingPx + (minute - settings.dayStartMinutes) * pxPerMin
        drawLine(
            color = Color.Gray.copy(alpha = 0.25f),
            start = Offset(labelWPx, y),
            end = Offset(w, y),
            strokeWidth = 1f,
        )
        val label = LocalTime.of(minute / 60 % 24, 0)
            .format(DateTimeFormatter.ofPattern("h a"))
        val layout = measurer.measure(label, TextStyle(fontSize = 11.sp, color = Color.Gray))
        drawText(layout, topLeft = Offset(labelWPx - layout.size.width - 12f, y - layout.size.height / 2f))
        minute += 60
    }

    // Half-hour minor lines
    minute = settings.dayStartMinutes + (30 - settings.dayStartMinutes % 30) % 30
    while (minute <= settings.dayEndMinutes) {
        if (minute % 60 != 0) {
            val y = timePaddingPx + (minute - settings.dayStartMinutes) * pxPerMin
            drawLine(
                color = Color.Gray.copy(alpha = 0.1f),
                start = Offset(labelWPx, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }
        minute += 30
    }

    // Events — clipped to each day column and to the schedulable zone
    for (dayIndex in 0..6) {
        val colLeft = labelWPx + dayIndex * dayW
        val colRight = colLeft + dayW
        drawContext.canvas.save()
        drawContext.canvas.clipRect(Rect(colLeft, timePaddingPx, colRight, h - timePaddingPx))
        for (event in weekEvents[dayIndex]) {
            if (event.endMinutes <= settings.dayStartMinutes ||
                event.startMinutes >= settings.dayEndMinutes) continue
            val cStart = event.startMinutes.coerceAtLeast(settings.dayStartMinutes)
            val cEnd = event.endMinutes.coerceAtMost(settings.dayEndMinutes)
            val top = timePaddingPx + (cStart - settings.dayStartMinutes) * pxPerMin + 2f
            val blockH = (cEnd - cStart) * pxPerMin - 4f

            drawRoundRect(
                color = Color(event.color).copy(alpha = 0.85f),
                topLeft = Offset(colLeft + 2f, top),
                size = Size(dayW - 4f, blockH.coerceAtLeast(4f)),
                cornerRadius = CornerRadius(5f),
            )

            if (blockH > 18f) {
                val layout = measurer.measure(
                    event.title,
                    TextStyle(
                        fontSize = 9.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                drawText(layout, topLeft = Offset(colLeft + 5f, top + 3f))
            }
        }
        drawContext.canvas.restore()
    }

    // Current time indicator — only in today's column
    if (todayIndex in 0..6) {
        val curMin = currentTime.hour * 60 + currentTime.minute
        if (curMin in settings.dayStartMinutes..settings.dayEndMinutes) {
            val y = timePaddingPx + (curMin - settings.dayStartMinutes) * pxPerMin
            val x = labelWPx + todayIndex * dayW
            drawCircle(color = Color.Red, radius = 5f, center = Offset(x, y))
            drawLine(
                color = Color.Red,
                start = Offset(x, y),
                end = Offset(x + dayW, y),
                strokeWidth = 2f,
            )
        }
    }

    // Darker overlay on top and bottom padding zones to indicate non-schedulable time
    val padColor = Color.Black.copy(alpha = 0.06f)
    drawRect(color = padColor, topLeft = Offset(labelWPx, 0f), size = Size(w - labelWPx, timePaddingPx))
    drawRect(color = padColor, topLeft = Offset(labelWPx, h - timePaddingPx), size = Size(w - labelWPx, timePaddingPx))
}
