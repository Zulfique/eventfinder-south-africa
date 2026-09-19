package com.eventfinder.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.data.local.toDomain
import com.eventfinder.app.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores event reminders after a device reboot. AlarmManager alarms do not
 * survive reboots; this receiver queries attending events and reschedules them.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer(context)
                val database = container.database
                val attendingEvents = database.eventDao().getUpcomingAttendingEvents(
                    System.currentTimeMillis()
                )
                var scheduled = 0
                for (entity in attendingEvents) {
                    val event = entity.toDomain()
                    scheduled += NotificationHelper.scheduleEventReminders(context, event)
                }
                AppLogger.i(
                    "BootReceiver",
                    "Boot completed - rescheduled $scheduled reminder(s) for ${attendingEvents.size} event(s)"
                )
            } catch (t: Exception) {
                AppLogger.e("BootReceiver", "Failed to reschedule reminders on boot", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
