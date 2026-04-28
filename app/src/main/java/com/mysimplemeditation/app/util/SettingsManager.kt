package com.mysimplemeditation.app.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Locale

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mymeditation_settings", Context.MODE_PRIVATE)

    var rememberVolume: Boolean
        get() = prefs.getBoolean("remember_volume", false)
        set(value) = prefs.edit().putBoolean("remember_volume", value).apply()

    var playAsAlarm: Boolean
        get() = prefs.getBoolean("play_as_alarm", false)
        set(value) = prefs.edit().putBoolean("play_as_alarm", value).apply()

    var lastVolume: Int
        get() = prefs.getInt("last_volume", 80)
        set(value) = prefs.edit().putInt("last_volume", value).apply()

    var lastSessionId: Long
        get() = prefs.getLong("last_session_id", -1L)
        set(value) = prefs.edit().putLong("last_session_id", value).apply()

    var savedMediaVolume: Int
        get() = prefs.getInt("saved_media_volume", -1)
        set(value) = prefs.edit().putInt("saved_media_volume", value).apply()

    var savedAlarmVolume: Int
        get() = prefs.getInt("saved_alarm_volume", -1)
        set(value) = prefs.edit().putInt("saved_alarm_volume", value).apply()

    var timeFormat: String
        get() = prefs.getString("time_format", "24h") ?: "24h"
        set(value) = prefs.edit().putString("time_format", value).apply()

    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    var chainThresholdMinutes: Int
        get() = prefs.getInt("chain_threshold_minutes", 45)
        set(value) = prefs.edit().putInt("chain_threshold_minutes", value).apply()

    fun is24Hour(): Boolean = timeFormat == "24h"

    fun formatTimeOfDay(hour: Int, minute: Int): String {
        return if (is24Hour()) {
            String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        } else {
            val amPm = if (hour >= 12) "PM" else "AM"
            val hour12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minute, amPm)
        }
    }

    fun getDateTimeFormatPattern(): String {
        return if (is24Hour()) "dd.MM.yyyy HH:mm" else "dd.MM.yyyy hh:mm a"
    }

    fun getDateTimeFormat(): SimpleDateFormat {
        return SimpleDateFormat(getDateTimeFormatPattern(), Locale.getDefault())
    }
}
