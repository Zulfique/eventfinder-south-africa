package com.eventfinder.app.utils

/**
 * Normalises raw venue/address strings coming from external feeds so the UI
 * shows clean, consistent place names.
 */
object GeoNormalizer {
    /** Trims, collapses whitespace, and title-cases a venue name. */
    fun normalizeVenueName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim()
            .replace(Regex("\\s+"), " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }

    /** Trims and collapses whitespace in an address. */
    fun normalizeAddress(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim().replace(Regex("\\s+"), " ")
    }

    /** Returns true when coordinates look valid (within South Africa bounds). */
    fun isValidSaCoordinate(lat: Double?, lng: Double?): Boolean {
        if (lat == null || lng == null) return false
        return lat in -35.0..-22.0 && lng in 16.0..33.0
    }
}
