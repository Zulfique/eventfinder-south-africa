package com.eventfinder.app.data.repository

import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventCategory
import java.util.Calendar

/**
 * Curated demo event catalogue for the South African market. Seeded into Room
 * on first launch so the prototype is fully usable offline and without any API key.
 *
 * These are labelled "(Demo)" so users know they are locally seeded examples,
 * not real upcoming events.
 *
 * Placeholder images use the free, keyless Picsum service (https://picsum.photos).
 */
object SampleEventsProvider {

    /** Returns current year + [days] days at [hour]:[minute]. */
    private fun inDays(days: Int, hour: Int, minute: Int = 0): Long {
        val now = Calendar.getInstance()
        now.add(Calendar.DAY_OF_YEAR, days)
        now.set(Calendar.HOUR_OF_DAY, hour)
        now.set(Calendar.MINUTE, minute)
        return now.timeInMillis
    }

    /** ~14 realistic demo events across Johannesburg, Cape Town and Durban. */
    fun johannesburgAndCapeTown(): List<Event> {
        val eventive = listOf(
            Event(
                id = "sample-jhb-music",
                title = "Joburg Jazz Nights (Demo)",
                description = "An intimate evening of live jazz in Maboneng precinct, featuring award-winning local ensembles.",
                category = EventCategory.MUSIC,
                startDate = inDays(12, 19, 0),
                endDate = inDays(12, 22, 0),
                venueName = "Maboneng Precinct Hall",
                address = "286 Fox Street, Maboneng, Johannesburg",
                latitude = -26.2072,
                longitude = 28.0561,
                imageUrl = "https://picsum.photos/seed/johannesburg-jazz/800/450",
                isPublic = true,
                organizerId = "sample-org-1",
                organizerName = "Maboneng Events Co.",
                attendeeCount = 240,
                isFavorite = false,
                isCreatedByUser = false
            ),
            Event(
                id = "sample-jhb-food",
                title = "Food Market Weekend (Demo)",
                description = "Street food, artisanal bakes and live cooking demonstrations across 60 stalls.",
                category = EventCategory.FOOD,
                startDate = inDays(19, 10, 0),
                endDate = inDays(20, 18, 0),
                venueName = "Bree Street Markets",
                address = "Bree Street, CBD, Johannesburg",
                latitude = -26.2056,
                longitude = 28.0406,
                imageUrl = "https://picsum.photos/seed/food-market/800/450",
                isPublic = true,
                organizerId = "sample-org-2",
                organizerName = "Bree Street Collective",
                attendeeCount = 450,
                isFavorite = false,
                isCreatedByUser = false
            ),
            Event(
                id = "sample-jhb-sports",
                title = "Parkrun Social 5km (Demo)",
                description = "Community 5km fun run around Zoo Lake. All fitness levels welcome.",
                category = EventCategory.SPORTS,
                startDate = inDays(13, 7, 30),
                endDate = inDays(13, 9, 30),
                venueName = "Zoo Lake Park",
                address = "Sunnyside Drive, Parkview, Johannesburg",
                latitude = -26.1710,
                longitude = 28.0320,
                imageUrl = "https://picsum.photos/seed/parkrun/800/450",
                isPublic = true,
                organizerId = "sample-org-3",
                organizerName = "Zoo Lake Runners",
                attendeeCount = 182,
                isFavorite = false,
                isCreatedByUser = false
            ),
            Event(
                id = "sample-jhb-business",
                title = "Startup Pitch Night (Demo)",
                description = "Early-stage founders pitch to investors and mentors. Networking to follow.",
                category = EventCategory.BUSINESS,
                startDate = inDays(41, 17, 30),
                endDate = inDays(41, 20, 0),
                venueName = "Tshimologong Digital Innovation Precinct",
                address = "41 Juta Street, Braamfontein, Johannesburg",
                latitude = -26.1918,
                longitude = 28.0333,
                imageUrl = "https://picsum.photos/seed/startup-pitch/800/450",
                isPublic = true,
                organizerId = "sample-org-4",
                organizerName = "Jozi Innovators",
                attendeeCount = 96,
                isFavorite = false,
                isCreatedByUser = false
            ),
            Event(
                id = "sample-jhb-arts",
                title = "Gallery Night Walk (Demo)",
                description = "Explore contemporary art galleries in the CBD on a guided night walk.",
                category = EventCategory.ARTS,
                startDate = inDays(26, 18, 0),
                endDate = inDays(26, 21, 0),
                venueName = "Newtown Cultural Precinct",
                address = "1 President Street, Newtown, Johannesburg",
                latitude = -26.2045,
                longitude = 28.0338,
                imageUrl = "https://picsum.photos/seed/gallery-night/800/450",
                isPublic = true,
                organizerId = "sample-org-5",
                organizerName = "Newtown Arts Trust",
                attendeeCount = 130,
                isFavorite = false,
                isCreatedByUser = false
            ),
            Event(
                id = "sample-cpt-music",
                title = "Cape Town Carnival Sound (Demo)",
                description = "A vibrant celebration of music and dance in the Company's Garden.",
                category = EventCategory.MUSIC,
                startDate = inDays(33, 15, 0),
                endDate = inDays(33, 21, 0),
                venueName = "Company's Garden",
                address = "Queen Victoria Street, Cape Town CBD",
                latitude = -33.9288,
                longitude = 18.4173,
                imageUrl = "https://picsum.photos/seed/cpt-sound/800/450",
                isPublic = true,
                organizerId = "sample-org-6",
                organizerName = "Cape Town Events",
                attendeeCount = 600,
                isFavorite = false,
                isCreatedByUser = false
            ),
            Event(
                id = "sample-cpt-food",
                title = "Hout Bay Food & Craft Market (Demo)",
                description = "Market stalls serving fresh seafood, preserves and handmade crafts by the bay.",
                category = EventCategory.FOOD,
                startDate = inDays(20, 10, 0),
                endDate = inDays(20, 15, 0),
                venueName = "Hout Bay Harbour",
                address = "Harbour Road, Hout Bay, Cape Town",
                latitude = -34.0422,
                longitude = 18.3504,
                imageUrl = "https://picsum.photos/seed/houtbay-market/800/450",
                isPublic = true,
                organizerId = "sample-org-7",
                organizerName = "Hout Bay Markets",
                attendeeCount = 320,
                isFavorite = false,
                isCreatedByUser = false
            ),
            Event(
                id = "sample-cpt-sports",
                title = "Table Mountain Trail Run (Demo)",
                description = "Guided trail run up the Platteklip Gorge route with stunning sunset views.",
                category = EventCategory.SPORTS,
                startDate = inDays(47, 6, 30),
                endDate = inDays(47, 10, 0),
                venueName = "Table Mountain Lower Cable Station",
                address = "Tafelberg Road, Cape Town",
                latitude = -33.9599,
                longitude = 18.4036,
                imageUrl = "https://picsum.photos/seed/tablemtn/800/450",
                isPublic = true,
                organizerId = "sample-org-8",
                organizerName = "Cape Trail Collective",
                attendeeCount = 78,
                isFavorite = false,
                isCreatedByUser = false
            ),
            Event(
                id = "sample-cpt-community",
                title = "Khayelitsha Community Clean-Up (Demo)",
                description = "Neighbourhood clean-up with breakfast for volunteers. Bringing the community together.",
                category = EventCategory.COMMUNITY,
                startDate = inDays(27, 8, 0),
                endDate = inDays(27, 12, 0),
                venueName = "Khayelitsha Community Hall",
                address = "Land by N2, Khayelitsha, Cape Town",
                latitude = -34.0358,
                longitude = 18.6850,
                imageUrl = "https://picsum.photos/seed/khayelitsha/800/450",
                isPublic = true,
                organizerId = "sample-org-9",
                organizerName = "Khayelitsha Civic",
                attendeeCount = 55,
                isFavorite = false,
                isCreatedByUser = false
            ),
            Event(
                id = "sample-dbn-music",
                title = "Durban Beachfront Reggae (Demo)",
                description = "Live reggae acts on the Golden Mile promenade as the sun sets over the Indian Ocean.",
                category = EventCategory.MUSIC,
                startDate = inDays(54, 16, 0),
                endDate = inDays(54, 21, 0),
                venueName = "Suncoast Promenade",
                address = "1 Battery Beach Road, Durban",
                latitude = -29.8277,
                longitude = 31.0354,
                imageUrl = "https://picsum.photos/seed/dbn-reggae/800/450",
                isPublic = true,
                organizerId = "sample-org-10",
                organizerName = "Durban Live",
                attendeeCount = 510,
                isFavorite = false,
                isCreatedByUser = false
            ),
            Event(
                id = "sample-dbn-food",
                title = "Bunny Chow Festival (Demo)",
                description = "Durban's favourite curry-through-a-loaf festival with a bunny chow eating contest.",
                category = EventCategory.FOOD,
                startDate = inDays(61, 11, 0),
                endDate = inDays(61, 17, 0),
                venueName = "Kings Park Stadium Precinct",
                address = "Masoja Msiza Road, Stamford Hill, Durban",
                latitude = -29.8273,
                longitude = 31.0311,
                imageUrl = "https://picsum.photos/seed/bunnychow/800/450",
                isPublic = true,
                organizerId = "sample-org-11",
                organizerName = "Durban Curry Club",
                attendeeCount = 265,
                isFavorite = false,
                isCreatedByUser = false
            ),
            Event(
                id = "sample-jhb-community",
                title = "Youth Coding Bootcamp Open Day (Demo)",
                description = "Free coding introduction for teens, with hands-on Scratch and Python workshops.",
                category = EventCategory.COMMUNITY,
                startDate = inDays(48, 9, 0),
                endDate = inDays(48, 14, 0),
                venueName = "Alexandra Community Centre",
                address = "London Street, Alexandra, Johannesburg",
                latitude = -26.1030,
                longitude = 28.0920,
                imageUrl = "https://picsum.photos/seed/youth-code/800/450",
                isPublic = true,
                organizerId = "sample-org-12",
                organizerName = "Code Youth SA",
                attendeeCount = 140,
                isFavorite = false,
                isCreatedByUser = false
            ),
            Event(
                id = "sample-jhb-arts-2",
                title = "Sculpture in the Park (Demo)",
                description = "Open-air sculpture exhibition with artist talks and a public vote for the people's choice award.",
                category = EventCategory.ARTS,
                startDate = inDays(28, 10, 0),
                endDate = inDays(28, 16, 0),
                venueName = "Johannesburg Botanical Gardens",
                address = "Olifants Road, Emmarentia, Johannesburg",
                latitude = -26.1642,
                longitude = 28.0239,
                imageUrl = "https://picsum.photos/seed/sculpture-park/800/450",
                isPublic = true,
                organizerId = "sample-org-13",
                organizerName = "Joburg Arts Council",
                attendeeCount = 88,
                isFavorite = false,
                isCreatedByUser = false
            ),
            Event(
                id = "sample-cpt-business",
                title = "AgriTech Innovation Forum (Demo)",
                description = "Panels and demos on water-smart farming and agritech startups in the Western Cape.",
                category = EventCategory.BUSINESS,
                startDate = inDays(52, 9, 30),
                endDate = inDays(52, 16, 0),
                venueName = "Stellenbosch Innovation Hub",
                address = "Bird Street, Stellenbosch",
                latitude = -33.9321,
                longitude = 18.8602,
                imageUrl = "https://picsum.photos/seed/agritech/800/450",
                isPublic = true,
                organizerId = "sample-org-14",
                organizerName = "Western Cape AgriHub",
                attendeeCount = 102,
                isFavorite = false,
                isCreatedByUser = false
            )
        )
        return eventive
    }
}