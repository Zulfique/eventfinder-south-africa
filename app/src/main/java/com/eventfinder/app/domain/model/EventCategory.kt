package com.eventfinder.app.domain.model

/**
 * Event categories (FR-02). Each category maps to a string resource key so the
 * labels render correctly in both English and Afrikaans.
 *
 * The `ticketmasterSegment` value (when non-null) is the Ticketmaster Discovery
 * API segment name used to classify live events during sync.
 */
enum class EventCategory(
    val labelKey: String,
    val ticketmasterSegment: String?
) {
    MUSIC("cat_music", "Music"),
    SPORTS("cat_sports", "Sports"),
    FOOD("cat_food", "Food & Drink"),
    ARTS("cat_arts", "Arts & Theatre"),
    COMMUNITY("cat_community", "Community"),
    BUSINESS("cat_business", "Business"),
    OTHER("all_categories", null);

    companion object {
        /** Resolve a Ticketmaster segment name into a local category. */
        fun fromTicketmaster(segmentName: String?): EventCategory =
            entries.firstOrNull { it.ticketmasterSegment?.equals(segmentName, ignoreCase = true) == true }
                ?: OTHER

        /** Resolve the category for a locally created event from its label key. */
        fun fromLabelKey(labelKey: String): EventCategory =
            entries.firstOrNull { it.labelKey == labelKey } ?: OTHER
    }
}