package com.eventfinder.app.data.cloud

import com.google.firebase.FirebaseApp

/**
 * Central gate for every Firebase call in the app.
 *
 * When google-services.json has not been replaced with a real project config
 * (fresh clone / CI), [FirebaseApp.getInstance] throws and this returns false;
 * all cloud features then degrade gracefully to offline-only Room behaviour so
 * the app still compiles, runs and passes tests without any Firebase project.
 */
object FirebaseStatus {

    /** True once a Firebase app has been initialised from google-services.json. */
    val isAvailable: Boolean
        get() = try {
            FirebaseApp.getInstance()
            true
        } catch (_: IllegalStateException) {
            false
        }
}
