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
    private val SHOW_NOTIFICATION        = booleanPreferencesKey("show_notification")
    private val HOUSEHOLD_ID             = stringPreferencesKey("household_id")
    private val WIDGET_HIDE_COMPLETED    = booleanPreferencesKey("widget_hide_completed")
    private val WIDGET_HIDE_DAYS_OUT     = intPreferencesKey("widget_hide_days_out")

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
            showNotification       = prefs[SHOW_NOTIFICATION]   ?: true,
            householdId            = prefs[HOUSEHOLD_ID],
            widgetHideCompleted    = prefs[WIDGET_HIDE_COMPLETED] ?: false,
            widgetHideDaysOut      = prefs[WIDGET_HIDE_DAYS_OUT]  ?: 0,
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

    suspend fun updateShowNotification(value: Boolean) {
        context.dataStore.edit { it[SHOW_NOTIFICATION] = value }
    }

    suspend fun updateHouseholdId(value: String?) {
        context.dataStore.edit {
            if (value == null) it.remove(HOUSEHOLD_ID) else it[HOUSEHOLD_ID] = value
        }
    }

    suspend fun updateWidgetHideCompleted(value: Boolean) {
        context.dataStore.edit { it[WIDGET_HIDE_COMPLETED] = value }
    }

    suspend fun updateWidgetHideDaysOut(value: Int) {
        context.dataStore.edit { it[WIDGET_HIDE_DAYS_OUT] = value }
    }
}
