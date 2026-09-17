package com.eventfinder.app.notifications

import java.util.concurrent.TimeUnit

/**
 * Lead times offered for event reminders (FR-04). Every event the user is
 * attending gets two local notifications: one 24 hours before it starts and a
 * second one hour before it starts.
 */
enum class ReminderLead(val offsetMillis: Long) {
    DAY_BEFORE(TimeUnit.HOURS.toMillis(24)),
    HOUR_BEFORE(TimeUnit.HOURS.toMillis(1))
}

/**
 * Pure scheduling arithmetic, deliberately free of Android framework types so
 * the reminder windows can be unit-tested on the JVM (see ReminderScheduleTest).
 */
object ReminderSchedule {

    /** Epoch millis at which the notification for [lead] should fire. */
    fun triggerAt(startDate: Long, lead: ReminderLead): Long = startDate - lead.offsetMillis

    /**
     * The reminders that are still schedulable for an event starting at
     * [startDate]. A lead is dropped when its trigger time has already passed,
     * so an event happening in 30 minutes only gets the (superseded) 1-hour
     * window skipped rather than an immediate notification.
     */
    fun leadsToSchedule(
        startDate: Long,
        now: Long = System.currentTimeMillis()
    ): List<ReminderLead> =
        ReminderLead.values().filter { triggerAt(startDate, it) > now }
}
