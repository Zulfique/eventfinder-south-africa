package com.eventfinder.app.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.eventfinder.app.BuildConfig
import java.io.File

/**
 * Local crash capture.
 *
 * The project has no crash-reporting service (and no keys for one), so the
 * uncaught-exception handler writes a self-contained report to app-private
 * storage instead. Nothing leaves the device: an admin can review and share the
 * latest report from Settings, which keeps the POPIA/GDPR story intact.
 */
object CrashReporter {

    private const val MAX_REPORTS = 5
    private const val DIR_NAME = "crash_reports"
    private const val TAG = "CrashReporter"

    /** Installs the handler; call once from [android.app.Application.onCreate]. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
                .onFailure { Log.e(TAG, "Could not persist crash report", it) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                kotlin.system.exitProcess(10)
            }
        }
        AppLogger.d(TAG, "Crash handler installed")
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val directory = directory(context)
        val file = File(directory, "crash_${System.currentTimeMillis()}.txt")
        file.writeText(
            buildString {
                appendLine("EventFinder crash report")
                appendLine("time=${System.currentTimeMillis()}")
                appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("sdk=${Build.VERSION.SDK_INT}")
                appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("thread=${thread.name}")
                appendLine()
                appendLine(Log.getStackTraceString(throwable))
            }
        )
        // Keep the five most recent reports so storage use stays bounded.
        directory.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_REPORTS)
            ?.forEach { it.delete() }
    }

    fun reports(context: Context): List<File> =
        directory(context).listFiles()
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun latest(context: Context): File? = reports(context).firstOrNull()

    fun clear(context: Context) {
        directory(context).listFiles()?.forEach { it.delete() }
    }

    private fun directory(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }
}
