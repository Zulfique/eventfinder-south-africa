package com.eventfinder.app


import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.activity.enableEdgeToEdge
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

        // 2026 baseline: draw behind the system bars and insets-aware
        // components (TopAppBar/Scaffold) handle the padding.
        enableEdgeToEdge()


        val container: AppContainer =
            (application as EventFinderApp).container


        NotificationHelper.createChannel(this)


        deepLinkEventId.value =
            intent?.getStringExtra(ReminderReceiver.EXTRA_EVENT_ID)


        requestNotificationPermissionIfNeeded()


        setContent {
            val themeMode by container.preferences.themeMode.collectAsState(initial = "system")
            // Material You is opt-in (Settings): without it the app always uses
            // the brand-green light scheme and the #0B1929 night scheme.
            val dynamicColor by container.preferences.dynamicColorEnabled.collectAsState(initial = false)
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            // Keep the status/navigation bar icons legible on both schemes:
            // white icons on the #0B1929 night surface, dark icons on the
            // near-white light surface.
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            EventFinderTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
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