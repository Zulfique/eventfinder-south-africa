package com.eventfinder.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for the date/time helpers that drive card labels, reminder
 * scheduling and the weather lookup window.
 */
class DateTimeUtilsTest {

    private val hour = TimeUnit.HOURS.toMillis(1)
    private val day = TimeUnit.HOURS.toMillis(24)

    @Test
    fun `isInFuture is true only for future timestamps`() {
        val now = System.currentTimeMillis()
        assertTrue(DateTimeUtils.isInFuture(now + hour))
        assertFalse(DateTimeUtils.isInFuture(now - hour))
    }

    @Test
    fun `dayBefore and hourBefore subtract the right amount`() {
        val base = 1_800_000_000_000L
        assertEquals(base - day, DateTimeUtils.dayBefore(base))
        assertEquals(base - hour, DateTimeUtils.hourBefore(base))
    }

    @Test
    fun `isToday and isTomorrow follow the current clock`() {
        val now = System.currentTimeMillis()
        assertTrue(DateTimeUtils.isToday(now + 1_000L))
        assertTrue(DateTimeUtils.isTomorrow(now + day + 5_000L))
        assertFalse(DateTimeUtils.isTomorrow(now + 2 * day))
    }

    @Test
    fun `daysUntil truncates towards zero`() {
        val now = System.currentTimeMillis()
        assertEquals(0L, DateTimeUtils.daysUntil(now + hour))
        assertEquals(3L, DateTimeUtils.daysUntil(now + 3 * day + hour))
    }

    @Test
    fun `isoDate renders a stable year and month`() {
        // 1_700_000_000_000 ms = 2023-11-14 (UTC); timezone drift cannot change
        // the year-month for any real device offset.
        assertTrue(DateTimeUtils.isoDate(1_700_000_000_000L).startsWith("2023-11"))
    }

    @Test
    fun `formatShortDate and formatTime produce non-empty output`() {
        val sample = 1_700_000_000_000L
        assertTrue(DateTimeUtils.formatShortDate(sample).isNotBlank())
        assertTrue(DateTimeUtils.formatFullDateTime(sample).contains("2023"))
        assertTrue(DateTimeUtils.formatTime(sample).isNotBlank())
    }
}
