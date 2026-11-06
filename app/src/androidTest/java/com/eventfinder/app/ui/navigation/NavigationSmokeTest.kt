package com.eventfinder.app.ui.navigation

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eventfinder.app.MainActivity
import com.eventfinder.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    /**
     * MainActivity asks for notification permission on a cold start, and the
     * system dialog would sit on top of the screen under test. Grant it before
     * the activity launches so the dialog never appears.
     */
    @get:Rule(order = 0)
    val grantNotificationPermission: TestRule = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement =
            object : Statement() {
                override fun evaluate() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val instrumentation = InstrumentationRegistry.getInstrumentation()
                        instrumentation.uiAutomation.grantRuntimePermission(
                            instrumentation.targetContext.packageName,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                    base.evaluate()
                }
            }
    }

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * A cold start shows the splash and then the login screen, so the app shell
     * is only reachable after continuing as a guest.
     */
    @Test
    fun appLaunchesAndShowsBottomNav() {
        val guestLabel = composeTestRule.activity.getString(R.string.continue_as_guest)
        val homeLabel = composeTestRule.activity.getString(R.string.discover_events)
        val searchLabel = composeTestRule.activity.getString(R.string.search_title)
        val profileLabel = composeTestRule.activity.getString(R.string.profile_title)

        composeTestRule.waitUntil(timeoutMillis = 60_000) {
            composeTestRule.onAllNodesWithText(guestLabel).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(guestLabel).performClick()

        composeTestRule.waitUntil(timeoutMillis = 60_000) {
            composeTestRule.onAllNodesWithText(homeLabel).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(homeLabel).assertExists()
        composeTestRule.onNodeWithText(searchLabel).assertExists()
        composeTestRule.onNodeWithText(profileLabel).assertExists()
    }
}
