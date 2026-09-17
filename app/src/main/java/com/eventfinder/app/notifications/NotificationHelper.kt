package com.eventfinder.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eventfinder.app.R
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.DateTimeUtils

/**
 * Local reminder notifications (FR-04). Schedules a system notification one
 * hour before a favourited event using AlarmManager.
 *
 * Firebase Cloud Messaging is the final-POE upgrade for remote push; this
 * prototype demonstrates the notification UX entirely on-device and free.
 *
 * References:
 *  - Android Developers, "Create and manage notification channels":
 *    https://developer.android.com/develop/ui/views/notifications/channels
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"
    const val CHANNEL_REMINDERS = "event_reminders"
    private const val REQUEST_CODE_BASE = 8000

    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.reminder_notification_title)
            }
            manager.createNotificationChannel(channel)
            AppLogger.d(TAG, "Notification channel created")
        }
    }

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /**
     * Schedules a reminder one hour before [event]. Returns true when scheduled.
     * Uses exact alarms when permitted, otherwise falls back to inexact timing.
     */
    fun scheduleEventReminder(context: Context, event: Event): Boolean {
        val triggerAt = DateTimeUtils.hourBefore(event.startDate)
        if (triggerAt <= System.currentTimeMillis()) {
            AppLogger.w(TAG, "Event ${event.id} starts within the hour - reminder skipped")
            return false
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, event.title)
            putExtra(ReminderReceiver.EXTRA_VENUE, event.venueName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (_: SecurityException) {
            // SCHEDULE_EXACT_ALARM not granted: fall back to an inexact alarm.
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
        AppLogger.i(TAG, "Reminder scheduled for '${event.title}' at $triggerAt")
        return true
    }
}

/**
 * Broadcast receiver that posts the actual reminder notification.
 * Registered in the manifest (see AndroidManifest.xml).
 */
class ReminderReceiver : android.content.BroadcastReceiver() {

    companion object {
        const val EXTRA_TITLE = "extra_event_title"
        const val EXTRA_VENUE = "extra_event_venue"
    }

    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.createChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            AppLogger.w("ReminderReceiver", "Notification permission missing - skipping reminder")
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: context.getString(R.string.reminder_notification_title)
        val venue = intent.getStringExtra(EXTRA_VENUE) ?: ""

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(
                if (venue.isBlank()) title
                else context.getString(R.string.reminder_notification_body, title, venue)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
        AppLogger.i("ReminderReceiver", "Reminder notification posted for '$title'")
    }
}