package com.eventfinder.app.data.cloud

import android.net.Uri
import com.eventfinder.app.utils.AppLogger
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.UUID

/**
 * Uploads event photos to Firebase Cloud Storage (blob storage) so they are
 * available on every device the account signs into, not just the phone that
 * picked the image.
 *
 * POE requirement: "Use blob storage."
 *
 * Every public method returns null (or the original local URI) when Firebase
 * is uninitialised or an upload fails, so the create-event flow never crashes
 * and the app remains fully usable in offline-only mode.
 */
class FirebaseImageUploader {

    private val tag = "FirebaseImageUploader"

    private val storage: FirebaseStorage?
        get() = if (FirebaseStatus.isAvailable) FirebaseStorage.getInstance() else null

    /**
     * Uploads the image at [localUri] to Storage and returns a permanent
     * https download URL, or null when Firebase is unavailable or the upload
     * fails (callers should fall back to the local file URI).
     */
    suspend fun uploadEventImage(localUri: String, eventId: String): String? {
        val storageRef = storage?.reference ?: run {
            AppLogger.d(tag, "Firebase Storage unavailable - keeping local image")
            return null
        }
        return try {
            val local = Uri.parse(localUri)
            val ext = if (localUri.endsWith(".png", ignoreCase = true)) "png" else "jpg"
            val imageRef: StorageReference =
                storageRef.child("event_images/$eventId.$ext")
            imageRef.putFile(local).await()
            val downloadUrl = imageRef.downloadUrl.await().toString()
            AppLogger.i(tag, "Uploaded event image to blob storage: $downloadUrl")
            downloadUrl
        } catch (t: Exception) {
            AppLogger.w(tag, "Blob upload failed - falling back to local file: ${t.message}")
            null
        }
    }

    /** Best-effort removal of a previously uploaded blob; failures are logged only. */
    suspend fun deleteIfRemote(url: String?) {
        if (url.isNullOrBlank() || !url.startsWith("https://")) return
        val storageRef = storage?.reference ?: return
        runCatching {
            // Extract the path after /o/ and URL-decode it to get the Storage path.
            val encodedPath = url.substringAfter("/o/").substringBefore("?")
            val path = java.net.URLDecoder.decode(encodedPath, "UTF-8")
            storageRef.child(path).delete().await()
            AppLogger.i(tag, "Deleted blob: $path")
        }.onFailure {
            AppLogger.d(tag, "Blob delete skipped: ${it.message}")
        }
    }

    /** Generates a stable-enough unique id for a brand-new event's image. */
    fun newImageId(): String = "img_${UUID.randomUUID()}"
}
