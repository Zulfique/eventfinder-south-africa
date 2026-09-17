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
import com.eventfinder.app.MainActivity
import com.eventfinder.app.R
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.utils.AppLogger

/**
 * Local reminder notifications (FR-04). Schedules two system notifications per
 * attending event - 24 hours and 1 hour before it starts - using AlarmManager,
 * and cancels them again when the user declines the event.
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
     * Schedules the 24-hour and 1-hour reminders for [event]. Returns the number
     * of notifications actually scheduled (0 when both trigger times have passed).
     * Uses exact alarms when permitted, otherwise falls back to inexact timing.
     */
    fun scheduleEventReminders(context: Context, event: Event): Int {
        val leads = ReminderSchedule.leadsToSchedule(event.startDate)
        if (leads.isEmpty()) {
            AppLogger.w(TAG, "Event ${event.id} starts within the hour - no reminders scheduled")
            return 0
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        leads.forEach { lead ->
            val triggerAt = ReminderSchedule.triggerAt(event.startDate, lead)
            val pendingIntent = reminderIntent(context, event, lead)
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } catch (_: SecurityException) {
                // SCHEDULE_EXACT_ALARM not granted: fall back to an inexact alarm.
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            AppLogger.i(TAG, "Reminder (${lead.name}) scheduled for '${event.title}' at $triggerAt")
        }
        return leads.size
    }

    /** Cancels both the 24-hour and 1-hour reminders for [eventId]. */
    fun cancelEventReminders(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var cancelled = 0
        ReminderLead.values().forEach { lead ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode(eventId, lead),
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                cancelled++
            }
        }
        AppLogger.i(TAG, "Cancelled $cancelled reminder(s) for $eventId")
    }

    private fun reminderIntent(context: Context, event: Event, lead: ReminderLead): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(event.id, lead),
            Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_TITLE, event.title)
                putExtra(ReminderReceiver.EXTRA_VENUE, event.venueName)
                putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
                putExtra(ReminderReceiver.EXTRA_LEAD, lead.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun requestCode(eventId: String, lead: ReminderLead): Int =
        REQUEST_CODE_BASE + eventId.hashCode() * 2 + lead.ordinal

    /** Stable notification id so re-scheduling the same reminder replaces the previous one. */
    fun notificationId(eventId: String, lead: ReminderLead): Int =
        REQUEST_CODE_BASE + eventId.hashCode() * 2 + lead.ordinal
}

/**
 * Broadcast receiver that posts the actual reminder notification.
 * Registered in the manifest (see AndroidManifest.xml).
 */
class ReminderReceiver : android.content.BroadcastReceiver() {

    companion object {
        const val EXTRA_TITLE = "extra_event_title"
        const val EXTRA_VENUE = "extra_event_venue"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_LEAD = "extra_event_lead"
    }

    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.createChannel(context)
        if (!NotificationHelper.hasNotificationPermission(context)) {
            AppLogger.w("ReminderReceiver", "Notification permission missing - skipping reminder")
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: context.getString(R.string.reminder_notification_title)
        val venue = intent.getStringExtra(EXTRA_VENUE) ?: ""
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        val lead = ReminderLead.values()
            .firstOrNull { it.name == intent.getStringExtra(EXTRA_LEAD) }
            ?: ReminderLead.HOUR_BEFORE

        val body = when (lead) {
            ReminderLead.DAY_BEFORE -> context.getString(R.string.reminder_tomorrow_body, title, venue)
            ReminderLead.HOUR_BEFORE -> context.getString(R.string.reminder_notification_body, title, venue)
        }

        val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(if (venue.isBlank()) title else body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (eventId != null) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    NotificationHelper.notificationId(eventId, lead),
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(EXTRA_EVENT_ID, eventId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        NotificationManagerCompat.from(context)
            .notify(NotificationHelper.notificationId(eventId ?: title, lead), builder.build())
        AppLogger.i("ReminderReceiver", "Reminder notification posted for '$title' (${lead.name})")
    }
}
