package com.eventfinder.app.data.cloud

/**
 * Pure helpers for the **blob store's public URLs**.
 *
 * The upload itself happens on the server (which owns the storage
 * credentials), so the phone only ever handles a public URL. These helpers turn
 * that URL back into the container key needed to delete the object.
 */
object AzureBlobKeys {

    /** The path prefix every event photo lives under. */
    const val EVENT_IMAGE_PREFIX = "event_images/"

    /**
     * Extracts the bucket key from a stored public URL, or returns null when
     * the URL does not point at the blob store.
     *
     * Local `file://` URIs and non-HTTP URLs are rejected on purpose: they
     * are handled by the local file store, never by the blob store.
     */
    fun fromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (!url.startsWith("http", ignoreCase = true)) return null

        val marker = "/$EVENT_IMAGE_PREFIX"
        val index = url.indexOf(marker)
        if (index < 0) return null

        val key = url.substring(index + 1)
        if (key.length <= EVENT_IMAGE_PREFIX.length) return null
        return key
    }
}
