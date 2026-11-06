package com.eventfinder.app.ui.navigation

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eventfinder.app.EventFinderApp
import com.eventfinder.app.MainActivity
import com.eventfinder.app.R
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    /**
     * Prepares the device state the smoke test needs before the activity is
     * created: the launch-time notification prompt is pre-granted so its dialog
     * cannot cover the window, and a guest session is seeded so the splash goes
     * straight to the app shell instead of stopping on login.
     */
    @get:Rule(order = 0)
    val prepareDevice: TestRule = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement =
            object : Statement() {
                override fun evaluate() {
                    val instrumentation = InstrumentationRegistry.getInstrumentation()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        instrumentation.uiAutomation.grantRuntimePermission(
                            instrumentation.targetContext.packageName,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                    val app = instrumentation.targetContext.applicationContext as EventFinderApp
                    runBlocking { app.container.authRepository.continueAsGuest() }
                    base.evaluate()
                }
            }
    }

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndShowsBottomNav() {
        val homeLabel = composeTestRule.activity.getString(R.string.discover_events)
        val searchLabel = composeTestRule.activity.getString(R.string.search_title)
        val profileLabel = composeTestRule.activity.getString(R.string.profile_title)

        // The splash screen hands over once it has resolved the seeded session.
        composeTestRule.waitUntil(timeoutMillis = 120_000) {
            composeTestRule.onAllNodesWithText(homeLabel).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onAllNodesWithText(homeLabel).onFirst().assertExists()
        composeTestRule.onAllNodesWithText(searchLabel).onFirst().assertExists()
        composeTestRule.onAllNodesWithText(profileLabel).onFirst().assertExists()
    }
}
