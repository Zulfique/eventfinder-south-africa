package com.eventfinder.app.data.store

/**
 * A user-made list of events ("My Lists" on the 2026 showcase).
 *
 * Collections are stored as JSON in DataStore rather than a Room table: they are
 * small, user-scoped and never synchronised, so a schema migration would buy
 * nothing. [categoryKey] keeps a collection live — its events are re-filtered
 * from the local cache every time it is opened — while a collection with no
 * category holds an explicit set of [eventIds].
 */
data class EventCollection(
    val id: String,
    val name: String,
    val categoryKey: String? = null,
    val eventIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
