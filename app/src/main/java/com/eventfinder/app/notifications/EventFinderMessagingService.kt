package com.eventfinder.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eventfinder.app.MainActivity
import com.eventfinder.app.R
import com.eventfinder.app.data.cloud.FirebaseStatus
import com.eventfinder.app.utils.AppLogger
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging service (POE: "Real-time Notifications").
 *
 * Receives push messages sent from the Firebase console or server and posts
 * them as system notifications. Devices auto-subscribe to the [TOPIC_NEW_EVENTS]
 * topic so any message published to that topic reaches every install.
 *
 * When Firebase is uninitialised (no real google-services.json) the SDK never
 * binds this service, so the class is inert and the app's local AlarmManager
 * reminders continue to work exactly as before.
 */
class EventFinderMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        AppLogger.i("FCM", "Push received from ${message.from}")
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.new_events_alert_title)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return
        val eventId = message.data["eventId"]

        NotificationHelper.createChannel(this)
        if (!NotificationHelper.hasNotificationPermission(this)) {
            AppLogger.w("FCM", "Notification permission missing - dropping push")
            return
        }

        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (eventId != null) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    this,
                    notificationId,
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        NotificationHelper.postNotification(this, notificationId, builder)
        AppLogger.i("FCM", "Posted push notification: $title")
    }

    /** Re-registers the device token and topics whenever FCM rotates it. */
    override fun onNewToken(token: String) {
        AppLogger.i("FCM", "Registration token refreshed")
        subscribeToTopics()
    }

    companion object {
        const val TOPIC_NEW_EVENTS = "new_events"

        /** Subscribes this device to the shared new-events topic. Safe to call repeatedly. */
        fun subscribeToTopics() {
            if (!FirebaseStatus.isAvailable) {
                AppLogger.d("FCM", "Firebase unavailable - skipping topic subscription")
                return
            }
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_NEW_EVENTS)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        AppLogger.i("FCM", "Subscribed to topic '$TOPIC_NEW_EVENTS'")
                    } else {
                        AppLogger.w("FCM", "Topic subscription failed: ${task.exception?.message}")
                    }
                }
        }
    }
}
