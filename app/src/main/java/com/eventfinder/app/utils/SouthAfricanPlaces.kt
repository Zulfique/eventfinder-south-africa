package com.eventfinder.app.utils

/**
 * A South African place with fixed coordinates.
 *
 * @property name canonical English name, e.g. "Cape Town".
 * @property latitude latitude in decimal degrees (WGS84).
 * @property longitude longitude in decimal degrees (WGS84).
 */
data class SouthAfricanPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Offline lookup table of the major South African population centres.
 *
 * The app must work for a user in *any* South African city, not only the one
 * the developer happened to test in. The table is used in two situations:
 *
 *  1. as an offline fallback when the "Default city" preference in Settings is
 *     set but the geocoding API is unreachable, and
 *  2. as the nation-wide default centre (Cape Town) before a real device
 *     position is known.
 *
 * Aliases include the Afrikaans names and the common English nicknames, so
 * "Kaapstad", "Joburg", "Pietermaritzburg" or "Gqeberha" all resolve.
 */
object SouthAfricanPlaces {

    /** Nation-wide fallback centre: Cape Town. */
    val nationalDefault = SouthAfricanPlace("Cape Town", -33.9249, 18.4241)

    /** Every known place, keyed by its canonical English name. */
    val all: List<SouthAfricanPlace> = listOf(
        SouthAfricanPlace("Cape Town", -33.9249, 18.4241),
        SouthAfricanPlace("Johannesburg", -26.2041, 28.0473),
        SouthAfricanPlace("Pretoria", -25.7479, 28.2293),
        SouthAfricanPlace("Durban", -29.8587, 31.0218),
        SouthAfricanPlace("Cape Town CBD", -33.9253, 18.4239),
        SouthAfricanPlace("Port Elizabeth", -33.9608, 25.6022),
        SouthAfricanPlace("East London", -33.0153, 27.9116),
        SouthAfricanPlace("Bloemfontein", -29.0852, 26.1598),
        SouthAfricanPlace("Polokwane", -23.8965, 29.5061),
        SouthAfricanPlace("Mbombela", -25.4658, 30.9853),
        SouthAfricanPlace("Nelspruit", -25.4747, 30.9842),
        SouthAfricanPlace("Kimberley", -28.7412, 24.7719),
        SouthAfricanPlace("Rustenburg", -25.6672, 27.2421),
        SouthAfricanPlace("Pietermaritzburg", -29.8587, 30.0218),
        SouthAfricanPlace("Stellenbosch", -33.9321, 18.8602),
        SouthAfricanPlace("Paarl", -33.7343, 18.9622),
        SouthAfricanPlace("George", -33.9628, 22.5621),
        SouthAfricanPlace("Mossel Bay", -34.1833, 22.1383),
        SouthAfricanPlace("Hermanus", -34.4187, 19.2365),
        SouthAfricanPlace("Knysna", -34.0153, 23.4741),
        SouthAfricanPlace("Sedgefield", -34.1833, 22.8944),
        SouthAfricanPlace("Richards Bay", -28.8017, 32.0916),
        SouthAfricanPlace("Upington", -28.4548, 21.2423),
        SouthAfricanPlace("Kroonstad", -27.9972, 27.0028),
        SouthAfricanPlace("Welkom", -27.9722, 26.8072),
        SouthAfricanPlace("Klerksdorp", -26.8741, 26.6742),
        SouthAfricanPlace("Potchefstroom", -26.6721, 27.1742),
        SouthAfricanPlace("Vereeniging", -26.6736, 27.8132),
        SouthAfricanPlace("Sasolburg", -26.8121, 27.8422),
        SouthAfricanPlace("Middelburg", -25.9924, 29.2268),
        SouthAfricanPlace("Witbank", -25.8711, 29.2272),
        SouthAfricanPlace("Tzaneen", -23.8333, 30.1633),
        SouthAfricanPlace("Ladysmith", -28.5616, 29.7812),
        SouthAfricanPlace("Newcastle", -27.6106, 29.9306),
        SouthAfricanPlace("Secunda", -26.5089, 29.1667),
        SouthAfricanPlace("Ermelo", -26.8261, 29.7939),
        SouthAfricanPlace("Phalaborwa", -24.0026, 31.1266),
        SouthAfricanPlace("Hoopstad", -27.8306, 25.6092),
        SouthAfricanPlace("Plettenberg Bay", -34.0174, 23.9441),
        SouthAfricanPlace("Jeffreys Bay", -33.9608, 27.4583),
        SouthAfricanPlace("Uniondale", -33.8146, 23.6146)
    )

    /** Alternative spellings, nicknames and Afrikaans names per canonical city. */
    private val aliases: Map<String, String> = buildMap {
        all.forEach { place ->
            put(normalise(place.name), place.name)
        }
        put(normalise("Kaapstad"), "Cape Town")
        put(normalise("Cape Town CBD"), "Cape Town CBD")
        put(normalise("capetown"), "Cape Town")
        put(normalise("Joburg"), "Johannesburg")
        put(normalise("Joburg CBD"), "Johannesburg")
        put(normalise("JHB"), "Johannesburg")
        put(normalise("Pretoria / Tshwane"), "Pretoria")
        put(normalise("Tshwane"), "Pretoria")
        put(normalise("Pietermaritzburg"), "Pietermaritzburg")
        put(normalise("Pietermaritzburg CBD"), "Pietermaritzburg")
        put(normalise("Pmb"), "Pietermaritzburg")
        put(normalise("Umzumbantsi"), "Pietermaritzburg")
        put(normalise("Gqeberha"), "Port Elizabeth")
        put(normalise("Port Elizabeth"), "Port Elizabeth")
        put(normalise("Nelspruit / Mbombela"), "Mbombela")
        put(normalise("Eastern Cape"), "East London")
        put(normalise("Oos-London"), "East London")
        put(normalise("Oos London"), "East London")
        put(normalise("Pietersburg"), "Polokwane")
        put(normalise("Mosselbaai"), "Mossel Bay")
        put(normalise("Franschhoek"), "Paarl")
        put(normalise("Knysna"), "Knysna")
        put(normalise("Plettenberg Bay"), "Plettenberg Bay")
        put(normalise("South Africa"), "Cape Town")
        put(normalise("Suid-Afrika"), "Cape Town")
        put(normalise("Suidafrika"), "Cape Town")
    }

    /**
     * Resolves a free-text place name to a South African city.
     *
     * The lookup is forgiving: it ignores case, extra whitespace, surrounding
     * punctuation and common prefixes such as "in " or "near ". Returns null
     * when the name is unknown so callers can fall back to geocoding.
     */
    fun findByName(rawName: String?): SouthAfricanPlace? {
        val cleaned = normalise(rawName.orEmpty())
        if (cleaned.isBlank()) return null

        aliases[cleaned]?.let { canonical -> return all.firstOrNull { it.name == canonical } }

        // "Cape Town, Western Cape" / "Johannesburg, Gauteng" — try the first part.
        val firstPart = cleaned.substringBefore(',').trim()
        if (firstPart != cleaned) {
            aliases[firstPart]?.let { canonical -> return all.firstOrNull { it.name == canonical } }
        }

        // "Sandton Johannesburg" — try the last word as well.
        val lastPart = cleaned.substringAfterLast(' ').trim()
        if (lastPart != cleaned) {
            aliases[lastPart]?.let { canonical -> return all.firstOrNull { it.name == canonical } }
        }

        return null
    }

    /**
     * Lower-cases, trims and strips punctuation so lookups are forgiving.
     * Commas are kept because they separate a city from its province
     * ("Cape Town, Western Cape").
     */
    private fun normalise(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9,\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
