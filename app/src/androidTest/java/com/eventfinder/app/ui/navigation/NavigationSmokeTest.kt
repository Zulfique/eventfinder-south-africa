package com.eventfinder.app.ui.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventfinder.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndShowsBottomNav() {
        composeTestRule.onNodeWithText("Discover Events").assertExists()
        composeTestRule.onNodeWithText("Search").assertExists()
        composeTestRule.onNodeWithText("Profile").assertExists()
    }
}
