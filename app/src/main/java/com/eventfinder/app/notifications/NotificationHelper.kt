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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Local reminder notifications (FR-04). Schedules two system notifications per
 * attending event - 24 hours and 1 hour before it starts - using AlarmManager,
 * and cancels them again when the user declines the event.
 *
 * Notifications are scheduled entirely on-device using AlarmManager.
 * No Firebase, cloud backend, sign-in, API key, or push service is required.
 *
 * References:
 *  - Android Developers, "Create and manage notification channels":
 *    https://developer.android.com/develop/ui/views/notifications/channels
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"
    const val CHANNEL_REMINDERS = "event_reminders"
    const val CHANNEL_ALERTS = "event_alerts"
    private const val REQUEST_CODE_BASE = 8000
    private const val ALERT_ID_BASE = 9000

    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val reminders = NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.reminder_notification_title)
            }
            manager.createNotificationChannel(reminders)

            val alerts = NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.alerts_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.alerts_channel_description)
            }
            manager.createNotificationChannel(alerts)
            AppLogger.d(TAG, "Notification channels created")
        }
    }

    /**
     * Posts the local "new events" and "favourite updated" alerts produced by a
     * sync (FR-04). Delivered entirely on-device - no push service required.
     */
    fun postEventAlerts(
        context: Context,
        newEvents: List<Event>,
        updatedFavorites: List<Event>
    ) {
        if (newEvents.isEmpty() && updatedFavorites.isEmpty()) return
        createChannel(context)
        if (!hasNotificationPermission(context)) {
            AppLogger.w(TAG, "Notification permission missing - skipping event alerts")
            return
        }
        if (newEvents.isNotEmpty()) {
            val body = context.resources.getQuantityString(
                R.plurals.new_events_alert_body,
                newEvents.size,
                newEvents.size
            )
            postAlert(
                context = context,
                title = context.getString(R.string.new_events_alert_title),
                body = body,
                notificationId = ALERT_ID_BASE,
                eventId = null
            )
        }
        updatedFavorites.forEachIndexed { index, event ->
            postAlert(
                context = context,
                title = context.getString(R.string.favorite_alert_title),
                body = context.getString(R.string.favorite_alert_body, event.title),
                notificationId = ALERT_ID_BASE + 1 + index,
                eventId = event.id
            )
        }
        AppLogger.i(
            TAG,
            "Posted ${newEvents.size} new-event and ${updatedFavorites.size} favourite-update alert(s)"
        )
    }

    private fun postAlert(
        context: Context,
        title: String,
        body: String,
        notificationId: Int,
        eventId: String?
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (eventId != null) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        // Only post notification if permission is granted (lint: MissingPermission)
        if (hasNotificationPermission(context)) {
            //noinspection MissingPermission
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
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
     * Checks exact-alarm permission before using setExactAndAllowWhileIdle.
     */
    fun scheduleEventReminders(context: Context, event: Event, userId: String): Int {
        val leads = ReminderSchedule.leadsToSchedule(event.startDate)
        if (leads.isEmpty()) {
            AppLogger.w(TAG, "Event ${event.id} starts within the hour - no reminders scheduled")
            return 0
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        leads.forEach { lead ->
            val triggerAt = ReminderSchedule.triggerAt(event.startDate, lead)
            val pendingIntent = reminderIntent(context, event, lead, userId)
            if (canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            AppLogger.i(TAG, "Reminder (${lead.name}) scheduled for '${event.title}' at $triggerAt")
        }
        return leads.size
    }

    /** Cancels both the 24-hour and 1-hour reminders for [eventId]. */
    fun cancelEventReminders(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var cancelled = 0
        ReminderLead.entries.forEach { lead ->
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

    private fun reminderIntent(context: Context, event: Event, lead: ReminderLead, userId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(event.id, lead),
            Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_TITLE, event.title)
                putExtra(ReminderReceiver.EXTRA_VENUE, event.venueName)
                putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
                putExtra(ReminderReceiver.EXTRA_LEAD, lead.name)
                putExtra(ReminderReceiver.EXTRA_USER_ID, userId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    private fun requestCode(eventId: String, lead: ReminderLead): Int {
        val hash = eventId.fold(17) { result, char -> 31 * result + char.code }
        val positiveHash = hash and Int.MAX_VALUE
        return REQUEST_CODE_BASE + ((positiveHash % 1_000_000) * 2) + lead.ordinal
    }

    /** Stable notification id so re-scheduling the same reminder replaces the previous one. */
    fun notificationId(eventId: String, lead: ReminderLead): Int = requestCode(eventId, lead)
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
        const val EXTRA_USER_ID = "extra_user_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.createChannel(context)
        if (!NotificationHelper.hasNotificationPermission(context)) {
            AppLogger.w("ReminderReceiver", "Notification permission missing - skipping reminder")
            return
        }

        val preferences = com.eventfinder.app.data.store.UserPreferences(context)
        val remindersEnabled = runBlocking {
            preferences.remindersEnabled.first()
        }
        if (!remindersEnabled) {
            AppLogger.w("ReminderReceiver", "Reminders disabled for current user - skipping notification")
            return
        }

        val alarmUserId = intent.getStringExtra(EXTRA_USER_ID)
        val currentUserId = runBlocking {
            preferences.sessionUserId.first()
        }
        if (!alarmUserId.isNullOrBlank() && alarmUserId != currentUserId) {
            AppLogger.w("ReminderReceiver", "Alarm user ($alarmUserId) != current session ($currentUserId) - skipping")
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: context.getString(R.string.reminder_notification_title)
        val venue = intent.getStringExtra(EXTRA_VENUE) ?: ""
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        val lead = ReminderLead.entries
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

        // Only post notification if permission is granted (lint: MissingPermission)
        if (NotificationHelper.hasNotificationPermission(context)) {
            //noinspection MissingPermission
            NotificationManagerCompat.from(context)
                .notify(NotificationHelper.notificationId(eventId ?: title, lead), builder.build())
        }
        AppLogger.i("ReminderReceiver", "Reminder notification posted for '$title' (${lead.name})")
    }
}
