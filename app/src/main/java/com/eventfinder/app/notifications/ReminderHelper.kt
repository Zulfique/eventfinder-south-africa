package com.eventfinder.app.notifications

import android.content.Context
import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.local.toDomain
import com.eventfinder.app.utils.AppLogger

/**
 * Shared reminder lifecycle management. Used by:
 *  - BootReceiver: restores reminders after reboot
 *  - AuthRepositoryImpl: restores reminders after login, cancels on logout
 *
 * All methods are user-scoped: they query attending events by userId so
 * only the active user's reminders are scheduled or cancelled.
 */
object ReminderHelper {

    private const val TAG = "ReminderHelper"

    /** Schedules reminders for all upcoming events the [userId] is attending. */
    suspend fun restoreReminders(context: Context, eventDao: EventDao, userId: String) {
        val events = eventDao.getUpcomingAttendingEventsForUser(userId, System.currentTimeMillis())
        var scheduled = 0
        for (entity in events) {
            scheduled += NotificationHelper.scheduleEventReminders(context, entity.toDomain())
        }
        AppLogger.i(TAG, "Restored $scheduled reminder(s) for ${events.size} event(s) (user: $userId)")
    }

    /** Cancels all pending reminders for events the [userId] is attending. */
    suspend fun cancelReminders(context: Context, eventDao: EventDao, userId: String) {
        val events = eventDao.getUpcomingAttendingEventsForUser(userId, System.currentTimeMillis())
        for (entity in events) {
            NotificationHelper.cancelEventReminders(context, entity.id)
        }
        AppLogger.i(TAG, "Cancelled reminders for ${events.size} event(s) (user: $userId)")
    }
}
