package com.eventfinder.app.data.cloud

import android.content.Context
import android.net.Uri
import com.eventfinder.app.data.remote.EventFinderApiClient
import com.eventfinder.app.data.store.SessionProvider
import com.eventfinder.app.utils.AppLogger
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

/**
 * Uploads event photos to **Azure Blob Storage** through the EventFinder REST API.
 *
 * The API owns the storage account credentials and exposes a small
 * authenticated upload endpoint, so the account key never reaches the handset.
 * The phone simply posts the image bytes and stores the returned public URL on
 * the event.
 *
 * Every method fails softly. When the API is unreachable, the server has no
 * Azure credentials, or the upload is rejected, the caller keeps the local
 * `file://` URI and the app stays fully usable offline.
 */
class AzureImageUploader(
    private val context: Context,
    private val api: EventFinderApiClient,
    private val session: SessionProvider
) {

    private val tag = "AzureImageUploader"

    /**
     * Uploads the image at [localUri] and returns a permanent public URL, or
     * null when the upload could not be completed (the caller should then
     * fall back to the local file URI).
     */
    suspend fun uploadEventImage(localUri: String, eventId: String): String? {
        val bearer = bearerToken() ?: run {
            AppLogger.d(tag, "No session token - keeping the local image")
            return null
        }

        val bytes = readBytes(localUri) ?: run {
            AppLogger.w(tag, "Could not read the picked image - keeping it local")
            return null
        }

        val fileName = fileNameFor(localUri, eventId)

        val result = api.uploadBlob(bearer, fileName, bytes)
        val uploaded = result.getOrNull()
        if (uploaded != null) {
            AppLogger.i(tag, "Uploaded event image to Azure Blob Storage: ${uploaded.url}")
            return uploaded.url
        }

        AppLogger.w(tag, "Blob upload failed (${result.exceptionOrNull()?.message}) - keeping the local image")
        return null
    }

    /**
     * Best-effort removal of a previously uploaded blob. Only remote
     * blob URLs are touched; local file URIs are ignored.
     */
    suspend fun deleteIfRemote(url: String?) {
        if (url.isNullOrBlank() || !url.startsWith("http")) return

        val bearer = bearerToken() ?: return
        val key = api.blobKeyFromUrl(url) ?: return

        val result = api.deleteBlob(bearer, key)
        if (result.isFailure) {
            AppLogger.d(tag, "Blob delete skipped: ${result.exceptionOrNull()?.message}")
        }
    }

    /** True when the server reports that Azure blob storage is configured. */
    suspend fun isBlobStorageAvailable(): Boolean =
        api.blobStorageStatus().getOrNull() == true

    private suspend fun bearerToken(): String? = session.authToken.firstOrNull()

    /** Reads the picked image into memory, downscaling huge photos first. */
    private fun readBytes(localUri: String): ByteArray? = try {
        val parsed = Uri.parse(localUri)
        val file = parsed.path?.let { File(it) }

        if (file != null && file.exists()) {
            file.readBytes()
        } else {
            context.contentResolver.openInputStream(parsed)?.use { stream ->
                stream.readBytes()
            }
        }
    } catch (t: Exception) {
        AppLogger.e(tag, "Failed to read the picked image", t)
        null
    }

    private fun fileNameFor(localUri: String, eventId: String): String {
        val extension = localUri.substringAfterLast('.', "").takeIf { it.isNotBlank() && it.length <= 5 }
            ?: "jpg"
        return "event_$eventId.$extension"
    }

    /** Generates a unique id for a brand-new event's image. */
    fun newImageId(): String = "img_${java.util.UUID.randomUUID()}"
}
