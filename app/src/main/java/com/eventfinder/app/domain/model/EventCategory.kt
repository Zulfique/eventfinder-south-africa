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

        /**
         * Best-effort category from free text (title + description).
         *
         * Public feeds often publish no usable category ("OTHER",
         * "all_categories"), which used to leave most imported events uncategorised
         * and made category filters, chips and collections look empty. Distinctive
         * words are matched first; short ambiguous ones use word boundaries so
         * "brunch" never reads as a run and "display" never reads as a play.
         */
        fun inferFromText(text: String): EventCategory {
            if (text.isBlank()) return OTHER
            val t = text.lowercase()
            fun has(vararg words: String) = words.any { t.contains(it) }
            fun word(word: String) = Regex("\\b" + Regex.escape(word) + "\\b").containsMatchIn(t)

            return when {
                has("enduro", "motocross", "karting", "rally", "racing", "formula 1", "drift") ||
                    word("race") || word("mx") -> SPORTS
                has("jazz", "concert", "live music", " dj", "dj ", "gig", "choir", "orchestra", "band") -> MUSIC
                has("street food", "food", "wine", "braai", "culinary", "beer", "tasting") -> FOOD
                has("comedy", "stand-up", "stand up") -> COMEDY
                has("theatre", "theater", "ballet", "opera", "pantomime") -> THEATRE
                has("gallery", "exhibition", "fine art", "art walk") || word("art") -> ARTS
                has("market", "makers", "flea") -> MARKETS
                // Generic umbrella words sit below the specific subjects so a
                // food festival stays FOOD and a jazz festival stays MUSIC.
                has("festival", "carnival") -> FESTIVALS
                has("marathon", "parkrun", "trail run", "cycling", "rugby", "football", "soccer", "cricket", "netball") -> SPORTS
                has("conference", "summit", "expo", "networking", "seminar", "business") -> BUSINESS
                has("hackathon", "tech", "coding", "developer", "ai ") -> TECHNOLOGY
                has("family", "kids", "children", "toddler") -> FAMILY
                has("hike", "hiking", "trail", "outdoor", "nature", "surf", "beach clean") -> OUTDOORS
                has("film", "movie", "cinema", "screening") -> MOVIES
                has("church", "worship", "prayer", "faith") -> RELIGION
                has("yoga", "wellness", "meditation", "sound bath") -> WELLNESS
                has("charity", "fundraiser", "fundraising", "ngo") -> CHARITY
                has("nightclub", "night life", "nightlife", "party", "rave") -> NIGHTLIFE
                has("workshop", "course", "lecture", "class") -> EDUCATION
                has("car show", "auto show", "classic car", "motorsport") -> CARS
                has("community", "neighbourhood", "neighborhood") -> COMMUNITY
                else -> OTHER
            }
        }
    }
}
