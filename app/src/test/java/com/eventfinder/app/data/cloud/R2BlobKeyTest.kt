package com.eventfinder.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the pure URL-handling logic of the Cloudflare R2 blob storage.
 *
 * The R2 uploader replaces the Firebase Storage uploader because Firebase
 * Storage needs the paid Blaze plan while R2's free tier covers this app.
 * The upload itself is exercised against the live API, but the key
 * extraction is pure and must be unit-tested.
 */
class R2BlobKeyTest {

    @Test
    fun `extracts the bucket key from a public r2 url`() {
        assertEquals(
            "event_images/user-id/abc123.jpg",
            R2BlobKeys.fromUrl(
                "https://account.r2.cloudflarestorage.com/eventfinder/event_images/user-id/abc123.jpg"
            )
        )
    }

    @Test
    fun `extracts the bucket key from a custom domain url`() {
        assertEquals(
            "event_images/user-id/abc123.jpg",
            R2BlobKeys.fromUrl("https://cdn.eventfinder.app/event_images/user-id/abc123.jpg")
        )
    }

    @Test
    fun `null and blank urls produce no key`() {
        assertNull(R2BlobKeys.fromUrl(null))
        assertNull(R2BlobKeys.fromUrl(""))
        assertNull(R2BlobKeys.fromUrl("   "))
    }

    @Test
    fun `a local file uri is not a blob key`() {
        assertNull(
            R2BlobKeys.fromUrl(
                "file:///data/user/0/com.eventfinder.app/files/event_images/local.jpg"
            )
        )
    }

    @Test
    fun `an unrelated url is not a blob key`() {
        assertNull(R2BlobKeys.fromUrl("https://example.com/photo.jpg"))
    }

    @Test
    fun `a url without a file part produces no key`() {
        assertNull(R2BlobKeys.fromUrl("https://cdn.eventfinder.app/event_images/"))
    }

    @Test
    fun `the event image prefix is the agreed bucket layout`() {
        assertEquals("event_images/", R2BlobKeys.EVENT_IMAGE_PREFIX)
    }
}
