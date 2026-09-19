package com.eventfinder.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.data.local.toDomain
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
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer(context)
                val database = container.database
                val userId = container.preferences.sessionUserId.first()

                if (userId == null) {
                    AppLogger.i("BootReceiver", "No active session - reminders will be restored on login")
                    return@launch
                }

                val attendingEvents = database.eventDao().getUpcomingAttendingEventsForUser(
                    userId,
                    System.currentTimeMillis()
                )
                var scheduled = 0
                for (entity in attendingEvents) {
                    val event = entity.toDomain()
                    scheduled += NotificationHelper.scheduleEventReminders(context, event)
                }
                AppLogger.i(
                    "BootReceiver",
                    "Boot completed - rescheduled $scheduled reminder(s) for ${attendingEvents.size} event(s) (user: $userId)"
                )
            } catch (t: Exception) {
                AppLogger.e("BootReceiver", "Failed to reschedule reminders on boot", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
