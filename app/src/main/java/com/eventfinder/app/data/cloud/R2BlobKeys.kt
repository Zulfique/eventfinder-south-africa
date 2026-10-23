package com.eventfinder.app.data.cloud

/**
 * Pure helpers for the **Cloudflare R2** blob store.
 *
 * The upload itself happens on the server (which owns the R2 credentials),
 * so the phone only ever handles a public URL. These helpers turn that URL
 * back into the bucket key needed to delete the object.
 */
object R2BlobKeys {

    /** The path prefix every event photo lives under. */
    const val EVENT_IMAGE_PREFIX = "event_images/"

    /**
     * Extracts the bucket key from a stored public URL, or returns null when
     * the URL does not point at the blob store.
     *
     * Local `file://` URIs and non-HTTP URLs are rejected on purpose: they
     * are handled by the local file store, never by R2.
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
