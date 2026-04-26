package com.mymeditation.app.util

import android.content.Context
import android.content.SharedPreferences

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
}
