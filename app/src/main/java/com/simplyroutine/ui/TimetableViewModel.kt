package com.simplyroutine.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplyroutine.data.AppDatabase
import com.simplyroutine.data.Event
import com.simplyroutine.data.FirestoreRepository
import com.simplyroutine.data.Occasion
import com.simplyroutine.data.Settings
import com.simplyroutine.data.SettingsRepository
import com.simplyroutine.data.Task
import com.simplyroutine.service.AlertScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import androidx.glance.appwidget.updateAll
import com.simplyroutine.widget.TaskListWidget

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val settingsRepo = SettingsRepository(app)
    private val firestoreRepo = FirestoreRepository()

    val events: StateFlow<List<Event>> = db.eventDao().getAllEvents()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val tasks: StateFlow<List<Task>> = db.taskDao().getAllTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val occasions: StateFlow<List<Occasion>> = db.occasionDao().getAllOccasions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val settings: StateFlow<Settings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(tourSeen = true))

    private val _currentTime = MutableStateFlow(LocalTime.now())
    val currentTime: StateFlow<LocalTime> = _currentTime

    init {
        viewModelScope.launch {
            while (true) {
                _currentTime.value = LocalTime.now()
                val msUntilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
                delay(msUntilNextMinute)
            }
        }

        // Mirror Firestore shared tasks into Room whenever householdId is set
        viewModelScope.launch {
            settingsRepo.settingsFlow
                .map { it.householdId }
                .distinctUntilChanged()
                .flatMapLatest { hId ->
                    if (hId == null) flowOf(emptyList())
                    else firestoreRepo.observeSharedTasks(hId)
                }
                .collect { remoteTasks -> mergeRemoteTasks(remoteTasks) }
        }
    }

    private suspend fun mergeRemoteTasks(remoteTasks: List<Task>) {
        val remoteSyncIds = remoteTasks.map { it.syncId }.toSet()
        remoteTasks.forEach { remote ->
            val local = db.taskDao().getBySyncId(remote.syncId)
            if (local == null) {
                db.taskDao().insert(remote.copy(id = 0))
            } else if (local.lastCompleted != remote.lastCompleted ||
                local.title != remote.title ||
                local.frequencyDays != remote.frequencyDays ||
                local.frequencyUnit != remote.frequencyUnit
            ) {
                db.taskDao().update(
                    local.copy(
                        title = remote.title,
                        frequencyDays = remote.frequencyDays,
                        frequencyUnit = remote.frequencyUnit,
                        lastCompleted = remote.lastCompleted,
                    )
                )
            }
        }
        // If a task disappeared from Firestore (was unshared remotely), unshare it locally
        db.taskDao().getSharedTasks().forEach { local ->
            if (local.syncId !in remoteSyncIds) {
                db.taskDao().update(local.copy(shared = false))
            }
        }
        TaskListWidget().updateAll(getApplication())
    }

    fun addEvent(event: Event) = viewModelScope.launch {
        val id = db.eventDao().insert(event)
        AlertScheduler.schedule(getApplication(), event.copy(id = id.toInt()))
    }
    fun updateEvent(event: Event) = viewModelScope.launch {
        val old = db.eventDao().getEventById(event.id)
        if (old != null) AlertScheduler.cancel(getApplication(), old)
        db.eventDao().update(event)
        AlertScheduler.schedule(getApplication(), event)
    }
    fun deleteEvent(event: Event) = viewModelScope.launch {
        AlertScheduler.cancel(getApplication(), event)
        db.eventDao().delete(event)
    }

    fun addTask(task: Task) = viewModelScope.launch {
        val id = db.taskDao().insert(task)
        if (task.shared) {
            val hId = settings.value.householdId ?: return@launch
            withContext(Dispatchers.IO) { firestoreRepo.pushTask(hId, task.copy(id = id.toInt())) }
        }
        TaskListWidget().updateAll(getApplication())
    }

    fun updateTask(task: Task) = viewModelScope.launch {
        val old = db.taskDao().getBySyncId(task.syncId)
        db.taskDao().update(task)
        val hId = settings.value.householdId
        if (hId != null) {
            withContext(Dispatchers.IO) {
                when {
                    task.shared -> firestoreRepo.pushTask(hId, task)
                    old?.shared == true -> firestoreRepo.deleteTask(hId, task)
                }
            }
        }
        TaskListWidget().updateAll(getApplication())
    }

    fun deleteTask(task: Task) = viewModelScope.launch {
        db.taskDao().delete(task)
        if (task.shared) {
            val hId = settings.value.householdId ?: return@launch
            withContext(Dispatchers.IO) { firestoreRepo.deleteTask(hId, task) }
        }
        TaskListWidget().updateAll(getApplication())
    }

    fun createHousehold() = viewModelScope.launch {
        val code = withContext(Dispatchers.IO) { firestoreRepo.createHousehold() }
        settingsRepo.updateHouseholdId(code)
    }

    fun joinHousehold(code: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val normalized = code.uppercase().trim()
        val exists = withContext(Dispatchers.IO) { firestoreRepo.joinHousehold(normalized) }
        if (exists) settingsRepo.updateHouseholdId(normalized)
        onResult(exists)
    }

    fun leaveHousehold() = viewModelScope.launch {
        settingsRepo.updateHouseholdId(null)
    }

    fun addOccasion(occasion: Occasion) = viewModelScope.launch { db.occasionDao().insert(occasion) }
    fun updateOccasion(occasion: Occasion) = viewModelScope.launch { db.occasionDao().update(occasion) }
    fun deleteOccasion(occasion: Occasion) = viewModelScope.launch { db.occasionDao().delete(occasion) }

    fun updateDayStart(minutes: Int) = viewModelScope.launch { settingsRepo.updateDayStart(minutes) }
    fun updateDayEnd(minutes: Int) = viewModelScope.launch { settingsRepo.updateDayEnd(minutes) }
    fun updateTapToAdd(value: Boolean) = viewModelScope.launch { settingsRepo.updateTapToAdd(value) }
    fun updateShowNextDayEvent(value: Boolean) = viewModelScope.launch { settingsRepo.updateShowNextDayEvent(value) }
    fun updateTimeFormat(value: String) = viewModelScope.launch { settingsRepo.updateTimeFormat(value) }
    fun updateResetScrollOnWeekChange(value: Boolean) = viewModelScope.launch { settingsRepo.updateResetScrollOnWeekChange(value) }
    fun updateDefaultAlertMinutes(value: Int) = viewModelScope.launch { settingsRepo.updateDefaultAlertMinutes(value) }
    fun updateTourSeen(value: Boolean) = viewModelScope.launch { settingsRepo.updateTourSeen(value) }
    fun updateShowNotification(value: Boolean) = viewModelScope.launch { settingsRepo.updateShowNotification(value) }
    fun updateWidgetHideCompleted(value: Boolean) = viewModelScope.launch {
        settingsRepo.updateWidgetHideCompleted(value)
        TaskListWidget().updateAll(getApplication())
    }
    fun updateWidgetHideDaysOut(value: Int) = viewModelScope.launch {
        settingsRepo.updateWidgetHideDaysOut(value)
        TaskListWidget().updateAll(getApplication())
    }
}
