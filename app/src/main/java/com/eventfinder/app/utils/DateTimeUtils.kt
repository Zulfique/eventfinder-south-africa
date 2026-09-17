package com.eventfinder.app.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Date/time formatting helpers. Locale-aware so the bilingual
 * (English / Afrikaans) feature also affects date presentation (FR-08).
 */
object DateTimeUtils {

    private val DAY_MILLIS = TimeUnit.HOURS.toMillis(24)
    private val HOUR_MILLIS = TimeUnit.HOURS.toMillis(1)

    /** Format a timestamp for a card: "Sat, 03 Oct" style. */
    fun formatShortDate(epochMillis: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("EEE, dd MMM", locale).format(Date(epochMillis))

    /** Full detail page format: "Saturday 3 October 2026, 19:00". */
    fun formatFullDateTime(epochMillis: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", locale).format(Date(epochMillis))

    /** Time only: "19:00". */
    fun formatTime(epochMillis: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("HH:mm", locale).format(Date(epochMillis))

    /** Date only (used for weather lookup): "2026-10-03". */
    fun isoDate(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMillis))

    /** True when the timestamp is still in the future. */
    fun isInFuture(epochMillis: Long): Boolean = epochMillis > System.currentTimeMillis()

    /** Time remaining watermark used for "today / tomorrow" labels. */
    fun daysUntil(epochMillis: Long): Long =
        TimeUnit.MILLISECONDS.toDays(epochMillis - System.currentTimeMillis())

    fun isToday(epochMillis: Long): Boolean = daysUntil(epochMillis) == 0L

    fun isTomorrow(epochMillis: Long): Boolean = daysUntil(epochMillis) == 1L

    fun hourBefore(epochMillis: Long): Long = epochMillis - HOUR_MILLIS

    fun dayBefore(epochMillis: Long): Long = epochMillis - DAY_MILLIS

    /** Local date (yyyy-MM-dd) converted to an epoch timestamp at 00:00. */
    fun startOfDay(epochMillis: Long): Long = epochMillis - (epochMillis % DAY_MILLIS)
}