package com.eventfinder.app

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.notifications.NotificationHelper
import com.eventfinder.app.ui.navigation.EventFinderNavHost
import com.eventfinder.app.ui.theme.EventFinderTheme
import com.eventfinder.app.utils.LocaleManager

/**
 * Single-activity Compose app. The activity extends [FragmentActivity] because
 * the AndroidX BiometricPrompt SDK requires a FragmentActivity host (see the
 * login screen); FragmentActivity is itself a ComponentActivity, so Compose
 * `setContent` continues to work unchanged.
 */
class MainActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Always start in the user's persisted language (FR-08).
        val lang = LocaleManager.currentLanguage(newBase)
        super.attachBaseContext(LocaleManager.apply(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container: AppContainer = (application as EventFinderApp).container
        NotificationHelper.createChannel(this)

        setContent {
            EventFinderTheme {
                EventFinderNavHost(container)
            }
        }
    }
}