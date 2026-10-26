package com.eventfinder.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the pure URL-handling logic of the Azure Blob Storage store.
 *
 * The Azure-backed uploader replaces the Firebase Storage uploader because
 * Firebase Storage needs the paid Blaze plan. The upload itself is exercised
 * against the live API, but the key extraction is pure and must be unit-tested.
 */
class AzureBlobKeysTest {

    @Test
    fun `extracts the container key from a public azure url`() {
        assertEquals(
            "event_images/user-id/abc123.jpg",
            AzureBlobKeys.fromUrl(
                "https://account.blob.core.windows.net/eventphotos/event_images/user-id/abc123.jpg"
            )
        )
    }

    @Test
    fun `extracts the container key from a custom domain url`() {
        assertEquals(
            "event_images/user-id/abc123.jpg",
            AzureBlobKeys.fromUrl("https://cdn.eventfinder.app/event_images/user-id/abc123.jpg")
        )
    }

    @Test
    fun `null and blank urls produce no key`() {
        assertNull(AzureBlobKeys.fromUrl(null))
        assertNull(AzureBlobKeys.fromUrl(""))
        assertNull(AzureBlobKeys.fromUrl("   "))
    }

    @Test
    fun `a local file uri is not a blob key`() {
        assertNull(
            AzureBlobKeys.fromUrl(
                "file:///data/user/0/com.eventfinder.app/files/event_images/local.jpg"
            )
        )
    }

    @Test
    fun `an unrelated url is not a blob key`() {
        assertNull(AzureBlobKeys.fromUrl("https://example.com/photo.jpg"))
    }

    @Test
    fun `a url without a file part produces no key`() {
        assertNull(AzureBlobKeys.fromUrl("https://cdn.eventfinder.app/event_images/"))
    }

    @Test
    fun `the event image prefix is the agreed bucket layout`() {
        assertEquals("event_images/", AzureBlobKeys.EVENT_IMAGE_PREFIX)
    }
}
