package com.eventfinder.app.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Applies the stored language preference (English / Afrikaans) to a context.
 *
 * This is the Compose-friendly pattern: the current language is persisted in
 * DataStore and applied in `MainActivity.onCreate`; switching languages updates
 * the preference and recreates the activity, which re-reads the configuration.
 *
 * References:
 *  - Android Developers, "Create a resourced app with a qualified resources
 *    directory": https://developer.android.com/guide/topics/resources/localization
 */
object LocaleManager {

    /** Applies [languageCode] (e.g. "af" or "en") to [context] and returns it. */
    fun apply(context: Context, languageCode: String): Context {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }

    /** Reads the persisted language synchronously (used only at startup). */
    fun currentLanguage(context: Context): String =
        (context.applicationContext as com.eventfinder.app.EventFinderApp)
            .container.preferences.currentLanguageBlocking()
}