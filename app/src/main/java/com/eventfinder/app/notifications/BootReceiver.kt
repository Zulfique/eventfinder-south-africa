package com.eventfinder.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
                // Use the application's existing AppContainer instead of creating a second one.
                // This avoids a second Room database instance ( Fix 7 ).
                val appContext = context.applicationContext
                val container: AppContainer = (appContext as? EventFinderApp)?.container
                    ?: AppContainer(appContext)

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
            } catch (t: Exception) {
                AppLogger.e("BootReceiver", "Failed to reschedule reminders on boot", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
