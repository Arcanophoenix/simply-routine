package com.simplyroutine.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val DAY_START           = intPreferencesKey("day_start")
    private val DAY_END             = intPreferencesKey("day_end")
    private val TAP_TO_ADD          = booleanPreferencesKey("tap_to_add")
    private val SHOW_NEXT_DAY_EVENT = booleanPreferencesKey("show_next_day_event")
    private val TIME_FORMAT         = stringPreferencesKey("time_format")
    private val RESET_SCROLL        = booleanPreferencesKey("reset_scroll_on_week_change")
    private val DEFAULT_ALERT       = intPreferencesKey("default_alert_minutes")
    private val TOUR_SEEN           = booleanPreferencesKey("tour_seen")

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            dayStartMinutes        = prefs[DAY_START]           ?: 360,
            dayEndMinutes          = prefs[DAY_END]             ?: 1320,
            tapToAdd               = prefs[TAP_TO_ADD]          ?: true,
            showNextDayEvent       = prefs[SHOW_NEXT_DAY_EVENT] ?: false,
            timeFormat             = prefs[TIME_FORMAT]         ?: "system",
            resetScrollOnWeekChange= prefs[RESET_SCROLL]        ?: false,
            defaultAlertMinutes    = prefs[DEFAULT_ALERT]       ?: -1,
            tourSeen               = prefs[TOUR_SEEN]           ?: false,
        )
    }

    suspend fun updateDayStart(minutes: Int) {
        context.dataStore.edit { it[DAY_START] = minutes }
    }

    suspend fun updateDayEnd(minutes: Int) {
        context.dataStore.edit { it[DAY_END] = minutes }
    }

    suspend fun updateTapToAdd(value: Boolean) {
        context.dataStore.edit { it[TAP_TO_ADD] = value }
    }

    suspend fun updateShowNextDayEvent(value: Boolean) {
        context.dataStore.edit { it[SHOW_NEXT_DAY_EVENT] = value }
    }

    suspend fun updateTimeFormat(value: String) {
        context.dataStore.edit { it[TIME_FORMAT] = value }
    }

    suspend fun updateResetScrollOnWeekChange(value: Boolean) {
        context.dataStore.edit { it[RESET_SCROLL] = value }
    }

    suspend fun updateDefaultAlertMinutes(value: Int) {
        context.dataStore.edit { it[DEFAULT_ALERT] = value }
    }

    suspend fun updateTourSeen(value: Boolean) {
        context.dataStore.edit { it[TOUR_SEEN] = value }
    }
}
