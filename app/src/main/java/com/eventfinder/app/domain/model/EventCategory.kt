package com.eventfinder.app.domain.model

/**
 * Event categories used by the local EventFinder catalogue.
 *
 * Categories are intentionally independent of any external event provider.
 */
enum class EventCategory(
    val labelKey: String
) {
    MUSIC("cat_music"),
    SPORTS("cat_sports"),
    FOOD("cat_food"),
    ARTS("cat_arts"),
    COMMUNITY("cat_community"),
    BUSINESS("cat_business"),
    OTHER("all_categories");

    companion object {

        fun fromLabelKey(
            labelKey: String
        ): EventCategory =
            entries.firstOrNull {
                it.labelKey == labelKey
            } ?: OTHER
    }
}
