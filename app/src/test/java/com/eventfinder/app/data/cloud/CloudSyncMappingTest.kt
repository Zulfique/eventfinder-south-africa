package com.eventfinder.app.data.cloud

import com.eventfinder.app.data.local.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncMappingTest {

    private fun sampleEntity() = EventEntity(
        id = "evt-1",
        title = "Jazz Night",
        description = "An evening of local jazz.",
        category = "music",
        startDate = 1_700_000_000_000L,
        endDate = 1_700_007_200_000L,
        venueName = "The Basement",
        address = "Braamfontein, Johannesburg",
        latitude = -26.1929,
        longitude = 28.0305,
        imageUrl = "https://example.com/photo.jpg",
        isPublic = true,
        organizerId = "user-1",
        organizerName = "You",
        attendeeCount = 12,
        isCreatedByUser = true
    )

    @Test
    fun `toFirestoreMap contains all sync fields`() {
        val map = sampleEntity().toFirestoreMap()
        assertEquals("evt-1", map["id"])
        assertEquals("Jazz Night", map["title"])
        assertEquals("music", map["category"])
        assertEquals(1_700_000_000_000L, map["startDate"])
        assertEquals(true, map["isPublic"])
        assertEquals(true, map["isCreatedByUser"])
        assertTrue(map.containsKey("syncedAt"))
    }

    @Test
    fun `toEventEntity round-trips through Firestore map`() {
        val original = sampleEntity()
        val restored = original.toFirestoreMap().toEventEntity("evt-1")
        assertEquals(original, restored)
    }

    @Test
    fun `toEventEntity returns null when title missing`() {
        val map = sampleEntity().toFirestoreMap().filterKeys { it != "title" }
        assertNull(map.toEventEntity("evt-1"))
    }

    @Test
    fun `toEventEntity returns null when startDate missing`() {
        val map = sampleEntity().toFirestoreMap().filterKeys { it != "startDate" }
        assertNull(map.toEventEntity("evt-1"))
    }

    @Test
    fun `toEventEntity tolerates null coordinates`() {
        val entity = sampleEntity().copy(latitude = null, longitude = null)
        val restored = entity.toFirestoreMap().toEventEntity("evt-1")
        assertEquals(entity, restored)
    }

    @Test
    fun `toEventEntity forces isCreatedByUser true for cloud documents`() {
        val map = sampleEntity().toFirestoreMap().toMutableMap()
        map["isCreatedByUser"] = false
        val restored = map.toEventEntity("evt-1")
        assertTrue(restored!!.isCreatedByUser)
    }
}
