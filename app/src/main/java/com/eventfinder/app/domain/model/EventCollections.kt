package com.eventfinder.app.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A curated collection on the Home feed ("This weekend", "Family day out", ...).
 *
 * Collections are pure filter presets over the local catalogue: they never
 * fetch extra data and never hide the rest of the app. Exactly one can be
 * active at a time, and the Home feed applies its [category] and [window].
 */
data class EventCollection(
    val id: String,
    val title: String,
    val category: EventCategory? = null,
    val window: TimeWindow = TimeWindow.ANYTIME
) {
    /** True when [event] belongs in this collection at [now]. */
    fun matches(event: Event, now: Long): Boolean {
        if (category != null && event.category != category) return false
        return when (window) {
            TimeWindow.ANYTIME -> true
            TimeWindow.TODAY -> event.startDate <= endOfDay(now)
            TimeWindow.WEEKEND -> {
                val (from, to) = weekendWindow(now)
                event.startDate in from until to
            }
            TimeWindow.NEXT_WEEK -> event.startDate <= now + 7 * 86_400_000L
        }
    }

    companion object {
        /**
         * Curated list shown on Home. Ordered by likely usefulness; the feed
         * drops any collection that currently has no matching events.
         */
        val curated: List<EventCollection> = listOf(
            EventCollection(id = "this_weekend", title = "This weekend", window = TimeWindow.WEEKEND),
            EventCollection(id = "live_music", title = "Live music", category = EventCategory.MUSIC),
            EventCollection(id = "family", title = "Family day out", category = EventCategory.FAMILY),
            EventCollection(id = "markets", title = "Markets & makers", category = EventCategory.MARKETS),
            EventCollection(id = "outdoors", title = "Outdoor adventures", category = EventCategory.OUTDOORS),
            EventCollection(id = "comedy", title = "Comedy nights", category = EventCategory.COMEDY)
        )

        fun byId(id: String?): EventCollection? = curated.firstOrNull { it.id == id }

        private fun zone(): ZoneId = ZoneId.systemDefault()

        private fun endOfDay(now: Long): Long = Instant.ofEpochMilli(now)
            .atZone(zone())
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone())
            .toInstant()
            .toEpochMilli()

        /** Friday 00:00 through Monday 00:00 of the current (or next) weekend. */
        internal fun weekendWindow(now: Long): Pair<Long, Long> {
            val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
            val daysUntilFriday = (DayOfWeek.FRIDAY.value - today.dayOfWeek.value + 7) % 7
            // Saturday and Sunday still count as "this weekend".
            val friday = if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) {
                today.minusDays((today.dayOfWeek.value - DayOfWeek.FRIDAY.value).toLong())
            } else {
                today.plusDays(daysUntilFriday.toLong())
            }
            val start = friday.atStartOfDay(zone()).toInstant().toEpochMilli()
            val end = friday.plusDays(3).atStartOfDay(zone()).toInstant().toEpochMilli()
            return start to end
        }
    }
}
