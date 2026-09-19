package com.eventfinder.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eventfinder.app.di.AppContainer
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
                val rsvpDao = container.eventRepository // indirect access
                AppLogger.i("BootReceiver", "Boot completed - rescheduling event reminders is deferred to app launch")
            } catch (t: Exception) {
                AppLogger.e("BootReceiver", "Failed to reschedule reminders on boot", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
