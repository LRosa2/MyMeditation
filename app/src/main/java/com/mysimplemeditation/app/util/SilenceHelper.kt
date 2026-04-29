package com.mysimplemeditation.app.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

object SilenceHelper {

    fun hasNotificationPolicyAccess(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.isNotificationPolicyAccessGranted
    }

    fun requestNotificationPolicyAccess(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        context.startActivity(intent)
    }

    fun isSilenced(context: Context): Boolean {
        if (!hasNotificationPolicyAccess(context)) return false
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val currentFilter = notificationManager.currentInterruptionFilter
        return currentFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
                || currentFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
    }

    fun silencePhone(context: Context, filter: Int = NotificationManager.INTERRUPTION_FILTER_ALARMS): Boolean {
        if (!hasNotificationPolicyAccess(context)) return false
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val currentFilter = notificationManager.currentInterruptionFilter
        if (currentFilter != filter) {
            SettingsManager(context).savedInterruptionFilter = currentFilter
        }
        notificationManager.setInterruptionFilter(filter)
        return true
    }

    fun restorePhone(context: Context): Boolean {
        if (!hasNotificationPolicyAccess(context)) return false
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val savedFilter = SettingsManager(context).savedInterruptionFilter
        val filterToRestore = if (savedFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            savedFilter
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        notificationManager.setInterruptionFilter(filterToRestore)
        return true
    }
}
