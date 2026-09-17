package com.eventfinder.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.eventfinder.app.R
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.EventView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the shared event widgets rendered on the Home, Search,
 * Favourites and Profile screens (FR-02/FR-03).
 *
 * Run on the emulator with: gradlew connectedDebugAndroidTest
 */
class CommonComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sampleEvent(isFavorite: Boolean = false) = Event(
        id = "e-1",
        title = "Cape Town Jazz Festival",
        description = "A celebration of South African jazz.",
        category = EventCategory.MUSIC,
        startDate = 1_800_000_000_000L,
        endDate = 1_800_007_200_000L,
        venueName = "Kirstenbosch Gardens",
        address = "Cape Town",
        latitude = -33.9249,
        longitude = 18.4241,
        imageUrl = null,
        isPublic = true,
        organizerId = "org-1",
        organizerName = "Jazz SA",
        attendeeCount = 250,
        isFavorite = isFavorite,
        isCreatedByUser = false,
        isSynced = true
    )

    @Test
    fun eventCard_displaysTitleAndVenue() {
        composeRule.setContent {
            EventCard(view = EventView(event = sampleEvent()), onClick = {})
        }

        composeRule.onNodeWithText("Cape Town Jazz Festival").assertIsDisplayed()
        composeRule.onNodeWithText("Kirstenbosch Gardens").assertIsDisplayed()
    }

    @Test
    fun eventCard_clickInvokesCallback() {
        var clicked = false
        composeRule.setContent {
            EventCard(view = EventView(event = sampleEvent()), onClick = { clicked = true })
        }

        composeRule.onNodeWithText("Cape Town Jazz Festival").performClick()

        assertTrue(clicked)
    }

    @Test
    fun eventCard_favouriteToggleInvokesCallback() {
        var toggled = false
        composeRule.setContent {
            EventCard(
                view = EventView(event = sampleEvent()),
                onClick = {},
                onFavoriteToggle = { toggled = true }
            )
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.favorite_add))
            .performClick()

        assertTrue(toggled)
    }

    @Test
    fun categoryChips_selectsACategoryAndCanClearIt() {
        val selections = mutableListOf<EventCategory?>()
        composeRule.setContent {
            CategoryChips(selected = null, onSelect = { selections.add(it) })
        }

        composeRule.onNodeWithText(context.getString(R.string.cat_music)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.all_categories)).performClick()

        assertTrue(selections.first() == EventCategory.MUSIC)
        assertTrue(selections.last() == null)
    }

    @Test
    fun categoryChips_selectsTheActiveChip() {
        composeRule.setContent {
            CategoryChips(selected = EventCategory.SPORTS, onSelect = {})
        }

        composeRule.onNodeWithText(context.getString(R.string.cat_sports)).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.cat_music)).assertIsNotSelected()
        composeRule.onNodeWithText(context.getString(R.string.all_categories)).assertIsNotSelected()
    }

    @Test
    fun emptyState_displaysTitleAndSubtitle() {
        composeRule.setContent {
            EmptyState(
                icon = Icons.Outlined.EventBusy,
                title = "Nothing here",
                subtitle = "Try again later"
            )
        }

        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
        composeRule.onNodeWithText("Try again later").assertIsDisplayed()
    }
}
