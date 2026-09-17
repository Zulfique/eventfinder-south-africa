package com.eventfinder.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Verifies the reminder windows behind FR-04: an attending event is scheduled
 * for both a 24-hour and a 1-hour local notification, and lead times that have
 * already elapsed are skipped rather than fired immediately.
 */
class ReminderScheduleTest {

    private val hour = TimeUnit.HOURS.toMillis(1)
    private val day = TimeUnit.HOURS.toMillis(24)
    private val now = 1_700_000_000_000L

    @Test
    fun `triggerAt subtracts the lead offset`() {
        val start = now + day
        assertEquals(start - day, ReminderSchedule.triggerAt(start, ReminderLead.DAY_BEFORE))
        assertEquals(start - hour, ReminderSchedule.triggerAt(start, ReminderLead.HOUR_BEFORE))
    }

    @Test
    fun `event days away schedules both reminders`() {
        val leads = ReminderSchedule.leadsToSchedule(now + 3 * day, now)
        assertEquals(listOf(ReminderLead.DAY_BEFORE, ReminderLead.HOUR_BEFORE), leads)
    }

    @Test
    fun `event later today only schedules the one hour reminder`() {
        val leads = ReminderSchedule.leadsToSchedule(now + 5 * hour, now)
        assertEquals(listOf(ReminderLead.HOUR_BEFORE), leads)
    }

    @Test
    fun `event starting within the hour schedules nothing`() {
        assertTrue(ReminderSchedule.leadsToSchedule(now + 30 * 60 * 1000L, now).isEmpty())
    }

    @Test
    fun `past event schedules nothing`() {
        assertTrue(ReminderSchedule.leadsToSchedule(now - hour, now).isEmpty())
    }

    @Test
    fun `a lead exactly on the boundary is not scheduled`() {
        // Trigger time == now is treated as already elapsed, avoiding an immediate pop.
        val start = now + day
        assertFalse(ReminderSchedule.leadsToSchedule(start, now).contains(ReminderLead.DAY_BEFORE))
    }
}
