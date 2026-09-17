package com.eventfinder.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.notifications.NotificationHelper
import com.eventfinder.app.notifications.ReminderReceiver
import com.eventfinder.app.ui.navigation.EventFinderNavHost
import com.eventfinder.app.ui.theme.EventFinderTheme
import com.eventfinder.app.utils.LocaleManager
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single-activity Compose app. The activity extends [FragmentActivity] because
 * the AndroidX BiometricPrompt SDK requires a FragmentActivity host (see the
 * login screen); FragmentActivity is itself a ComponentActivity, so Compose
 * `setContent` continues to work unchanged.
 */
class MainActivity : FragmentActivity() {

    /** Event id delivered by a reminder notification tap; consumed by the nav graph. */
    private val deepLinkEventId = MutableStateFlow<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        // Always start in the user's persisted language (FR-08).
        val lang = LocaleManager.currentLanguage(newBase)
        super.attachBaseContext(LocaleManager.apply(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container: AppContainer = (application as EventFinderApp).container
        NotificationHelper.createChannel(this)

        deepLinkEventId.value = intent?.getStringExtra(ReminderReceiver.EXTRA_EVENT_ID)

        setContent {
            EventFinderTheme {
                EventFinderNavHost(container = container, deepLinkEventId = deepLinkEventId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A reminder was tapped while the app was already open.
        deepLinkEventId.value = intent.getStringExtra(ReminderReceiver.EXTRA_EVENT_ID)
    }
}