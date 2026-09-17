package com.eventfinder.app.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Copies images the user picks from the system photo picker into app-private
 * storage so the event keeps working offline and without any paid object store.
 *
 * The returned value is a `file://` URI string that Coil can load directly and
 * that is safe to persist in the local Room cache.
 */
object ImageStorage {

    private const val TAG = "ImageStorage"
    private const val FOLDER = "event_images"

    /** Imports [source] into internal storage; returns the local URI or null on failure. */
    fun importImage(context: Context, source: Uri): String? {
        return try {
            val dir = File(context.filesDir, FOLDER).apply { mkdirs() }
            val file = File(dir, "event_${UUID.randomUUID()}.jpg")
            val opened = context.contentResolver.openInputStream(source)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: return null
            if (!opened || file.length() == 0L) {
                file.delete()
                return null
            }
            AppLogger.i(TAG, "Imported event image to ${file.name}")
            Uri.fromFile(file).toString()
        } catch (t: Exception) {
            AppLogger.e(TAG, "Failed to import event image", t)
            null
        }
    }

    /** Deletes a previously imported file. Remote URLs are ignored. */
    fun deleteIfLocal(path: String?) {
        if (path.isNullOrBlank() || !path.startsWith("file:")) return
        runCatching {
            val file = File(Uri.parse(path).path ?: return)
            if (file.exists() && file.delete()) AppLogger.i(TAG, "Deleted event image ${file.name}")
        }
    }
}
