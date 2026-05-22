package com.simplyroutine.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplyroutine.data.AppDatabase
import com.simplyroutine.data.Event
import com.simplyroutine.data.Settings
import com.simplyroutine.data.SettingsRepository
import com.simplyroutine.service.AlertScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

class TimetableViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val settingsRepo = SettingsRepository(app)

    val events: StateFlow<List<Event>> = db.eventDao().getAllEvents()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val settings: StateFlow<Settings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings())

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
    }

    fun addEvent(event: Event) = viewModelScope.launch {
        val id = db.eventDao().insert(event)
        AlertScheduler.schedule(getApplication(), event.copy(id = id.toInt()))
    }
    fun updateEvent(event: Event) = viewModelScope.launch {
        // Cancel alarms for the OLD alert list before overwriting
        val old = db.eventDao().getEventById(event.id)
        if (old != null) AlertScheduler.cancel(getApplication(), old)
        db.eventDao().update(event)
        AlertScheduler.schedule(getApplication(), event)
    }
    fun deleteEvent(event: Event) = viewModelScope.launch {
        AlertScheduler.cancel(getApplication(), event)
        db.eventDao().delete(event)
    }
    fun updateDayStart(minutes: Int) = viewModelScope.launch { settingsRepo.updateDayStart(minutes) }
    fun updateDayEnd(minutes: Int) = viewModelScope.launch { settingsRepo.updateDayEnd(minutes) }
    fun updateTapToAdd(value: Boolean) = viewModelScope.launch { settingsRepo.updateTapToAdd(value) }
    fun updateShowNextDayEvent(value: Boolean) = viewModelScope.launch { settingsRepo.updateShowNextDayEvent(value) }
    fun updateTimeFormat(value: String) = viewModelScope.launch { settingsRepo.updateTimeFormat(value) }
    fun updateResetScrollOnWeekChange(value: Boolean) = viewModelScope.launch { settingsRepo.updateResetScrollOnWeekChange(value) }
    fun updateDefaultAlertMinutes(value: Int) = viewModelScope.launch { settingsRepo.updateDefaultAlertMinutes(value) }
    fun updateTourSeen(value: Boolean) = viewModelScope.launch { settingsRepo.updateTourSeen(value) }
}
