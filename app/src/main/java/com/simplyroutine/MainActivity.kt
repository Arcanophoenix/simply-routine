package com.simplyroutine

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.simplyroutine.data.Event
import com.simplyroutine.data.RepeatType
import com.simplyroutine.service.TimekeeperService
import com.simplyroutine.widget.TimetableWidgetReceiver
import com.simplyroutine.ui.AddEventDialog
import com.simplyroutine.ui.SettingsScreen
import com.simplyroutine.ui.TimetableScreen
import com.simplyroutine.ui.TimetableViewModel
import com.simplyroutine.ui.TourOverlay
import com.simplyroutine.ui.TourState
import com.simplyroutine.ui.theme.TimeKeeperTheme
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private val viewModel: TimetableViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        startForegroundService(Intent(this, TimekeeperService::class.java))

        setContent {
            TimeKeeperTheme {
                val navController = rememberNavController()
                val events by viewModel.events.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val currentTime by viewModel.currentTime.collectAsState()

                val is24Hour = remember(settings.timeFormat) {
                    when (settings.timeFormat) {
                        "24h" -> true
                        "12h" -> false
                        else  -> DateFormat.is24HourFormat(this@MainActivity)
                    }
                }

                val tourState = remember { TourState() }
                LaunchedEffect(settings.tourSeen) {
                    if (!settings.tourSeen && !tourState.active) tourState.start()
                }

                var showAddDialog by remember { mutableStateOf(false) }
                var addEventDate by remember { mutableStateOf(LocalDate.now()) }
                var addEventStartMin by remember { mutableStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
                var eventToEdit by remember { mutableStateOf<Event?>(null) }
                // Non-null when editing a single occurrence of a recurring event
                var occurrenceBase by remember { mutableStateOf<Event?>(null) }

                NavHost(navController = navController, startDestination = "timetable") {
                    composable("timetable") {
                        TimetableScreen(
                            events = events,
                            settings = settings,
                            currentTime = currentTime,
                            onAddEvent = { date, startMin ->
                                addEventDate = date
                                addEventStartMin = startMin
                                showAddDialog = true
                            },
                            onEditEvent = { eventToEdit = it },
                            onEditOccurrence = { base, date ->
                                occurrenceBase = base
                                eventToEdit = base.copy(
                                    id = 0,
                                    date = date.toEpochDay(),
                                    repeatType = RepeatType.NONE.name,
                                    repeatInterval = 1,
                                    repeatDays = 0,
                                    exceptDates = "",
                                    parentId = base.id,
                                )
                            },
                            onPinWidget = {
                                val manager = AppWidgetManager.getInstance(this@MainActivity)
                                val provider = ComponentName(this@MainActivity, TimetableWidgetReceiver::class.java)
                                if (manager.isRequestPinAppWidgetSupported) {
                                    manager.requestPinAppWidget(provider, null, null)
                                }
                            },
                            onNavigateToSettings = { navController.navigate("settings") },
                            tourState = tourState,
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            settings = settings,
                            is24Hour = is24Hour,
                            onUpdateDayStart = viewModel::updateDayStart,
                            onUpdateDayEnd = viewModel::updateDayEnd,
                            onUpdateTapToAdd = viewModel::updateTapToAdd,
                            onUpdateShowNextDayEvent = viewModel::updateShowNextDayEvent,
                            onUpdateTimeFormat = viewModel::updateTimeFormat,
                            onUpdateResetScrollOnWeekChange = viewModel::updateResetScrollOnWeekChange,
                            onUpdateDefaultAlertMinutes = viewModel::updateDefaultAlertMinutes,
                            onStartTour = { tourState.start() },
                            onNavigateBack = { navController.popBackStack() },
                        )
                    }
                }

                if (showAddDialog) {
                    AddEventDialog(
                        existingEvents = events,
                        defaultDate = addEventDate,
                        defaultStartMinutes = addEventStartMin,
                        defaultAlertMinutes = settings.defaultAlertMinutes,
                        is24Hour = is24Hour,
                        onDismiss = { showAddDialog = false },
                        onSave = { event ->
                            viewModel.addEvent(event)
                            showAddDialog = false
                        },
                        onResolveConflicts = { toUpdate, toDelete, toAdd ->
                            toUpdate.forEach { viewModel.updateEvent(it) }
                            toDelete.forEach { viewModel.deleteEvent(it) }
                            toAdd.forEach { viewModel.addEvent(it) }
                        },
                    )
                }

                TourOverlay(
                    state = tourState,
                    onFinish = { viewModel.updateTourSeen(true) },
                )

                eventToEdit?.let { event ->
                    AddEventDialog(
                        event = event,
                        existingEvents = events,
                        defaultAlertMinutes = settings.defaultAlertMinutes,
                        is24Hour = is24Hour,
                        onDismiss = {
                            eventToEdit = null
                            occurrenceBase = null
                        },
                        onSave = { updated ->
                            val base = occurrenceBase
                            if (base != null) {
                                viewModel.addEvent(updated)
                                viewModel.updateEvent(base.withException(updated.localDate))
                            } else {
                                viewModel.updateEvent(updated)
                            }
                            eventToEdit = null
                            occurrenceBase = null
                        },
                        onResolveConflicts = { toUpdate, toDelete, toAdd ->
                            toUpdate.forEach { viewModel.updateEvent(it) }
                            toDelete.forEach { viewModel.deleteEvent(it) }
                            toAdd.forEach { viewModel.addEvent(it) }
                        },
                        onDelete = { toDelete ->
                            viewModel.deleteEvent(toDelete)
                            eventToEdit = null
                            occurrenceBase = null
                        },
                        onRejoinSeries = if (event.parentId != 0 && event.id != 0) {
                            {
                                val parent = events.find { it.id == event.parentId }
                                if (parent != null) {
                                    viewModel.updateEvent(parent.withoutException(event.localDate))
                                    viewModel.deleteEvent(event)
                                }
                                eventToEdit = null
                                occurrenceBase = null
                            }
                        } else null,
                    )
                }
            }
        }
    }
}
