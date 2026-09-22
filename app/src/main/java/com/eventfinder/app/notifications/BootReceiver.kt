package com.eventfinder.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eventfinder.app.EventFinderApp
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restores event reminders after a device reboot. AlarmManager alarms do not
 * survive reboots; this receiver queries attending events and reschedules them.
 *
 * Reminder restoration is per-account: only reminders for the currently
 * signed-in user are restored. If no user is signed in, no reminders are
 * scheduled (they will be restored on next login).
 *
 * Respects the user's remindersEnabled preference: if the user has disabled
 * reminders, no alarms are scheduled on boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as EventFinderApp
                val container = app.container

                val userId = container.preferences.sessionUserId.first()

                if (userId == null) {
                    AppLogger.i("BootReceiver", "No active session - reminders will be restored on login")
                    return@launch
                }

                val remindersEnabled = container.preferences.remindersEnabled.first()
                if (!remindersEnabled) {
                    AppLogger.i("BootReceiver", "Reminders disabled for user $userId - skipping restoration")
                    return@launch
                }

                ReminderHelper.restoreReminders(context, container.database.eventDao(), userId)
            } catch (e: Exception) {
                AppLogger.e(
                    "BootReceiver",
                    "Failed to reschedule reminders on boot",
                    e
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
