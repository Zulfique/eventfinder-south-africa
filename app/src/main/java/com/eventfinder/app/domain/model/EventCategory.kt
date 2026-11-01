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
    CONCERTS("cat_concerts"),
    FESTIVALS("cat_festivals"),
    NIGHTLIFE("cat_nightlife"),
    FOOD("cat_food"),
    SPORTS("cat_sports"),
    ARTS("cat_arts"),
    COMEDY("cat_comedy"),
    BUSINESS("cat_business"),
    NETWORKING("cat_networking"),
    TECHNOLOGY("cat_technology"),
    EDUCATION("cat_education"),
    FAMILY("cat_family"),
    MARKETS("cat_markets"),
    OUTDOORS("cat_outdoors"),
    TRAVEL("cat_travel"),
    CHARITY("cat_charity"),
    THEATRE("cat_theatre"),
    MOVIES("cat_movies"),
    FASHION("cat_fashion"),
    CARS("cat_cars"),
    WELLNESS("cat_wellness"),
    RELIGION("cat_religion"),
    COMMUNITY("cat_community"),
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
