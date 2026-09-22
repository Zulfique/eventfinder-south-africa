package com.eventfinder.app


import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.notifications.NotificationHelper
import com.eventfinder.app.notifications.ReminderReceiver
import com.eventfinder.app.ui.navigation.EventFinderNavHost
import com.eventfinder.app.ui.theme.EventFinderTheme
import com.eventfinder.app.utils.LocaleManager
import kotlinx.coroutines.flow.MutableStateFlow


class MainActivity : FragmentActivity() {


    private val deepLinkEventId = MutableStateFlow<String?>(null)


    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                NotificationHelper.createChannel(this)
            }
        }


    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleManager.currentLanguage(newBase)
        super.attachBaseContext(LocaleManager.apply(newBase, lang))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val container: AppContainer =
            (application as EventFinderApp).container


        NotificationHelper.createChannel(this)


        deepLinkEventId.value =
            intent?.getStringExtra(ReminderReceiver.EXTRA_EVENT_ID)


        requestNotificationPermissionIfNeeded()


        setContent {
            EventFinderTheme {
                EventFinderNavHost(
                    container = container,
                    deepLinkEventId = deepLinkEventId
                )
            }
        }
    }


    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }


        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)


        deepLinkEventId.value =
            intent.getStringExtra(ReminderReceiver.EXTRA_EVENT_ID)
    }
}