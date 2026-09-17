package com.eventfinder.app.utils

import android.util.Log

/**
 * Centralised logging façade used across the app.
 *
 * Every class logs under its own sub-tag (e.g. "AuthRepository") beneath the
 * shared "EventFinder" tag so lifecycle and state transitions can be traced
 * with `adb logcat -s EventFinder`.
 *
 * References:
 *  - Android Developers, "Write and View Logs with Logcat":
 *    https://developer.android.com/studio/debug/am-logcat
 */
object AppLogger {
    private const val TAG = "EventFinder"

    /** Verbose / debug messages — detailed state transitions. */
    fun d(subTag: String, message: String) {
        Log.d(TAG, "[$subTag] $message")
    }

    /** Informational messages — lifecycle milestones, sync completions. */
    fun i(subTag: String, message: String) {
        Log.i(TAG, "[$subTag] $message")
    }

    /** Warnings — recoverable problems such as failed API calls. */
    fun w(subTag: String, message: String) {
        Log.w(TAG, "[$subTag] $message")
    }

    /** Errors — exceptions and fatal state transitions. */
    fun e(subTag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, "[$subTag] $message", throwable) else Log.e(TAG, "[$subTag] $message")
    }
}