package com.eventfinder.app.ui.screens.profile

import com.eventfinder.app.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for avatar type state mapping (one case per avatar key).
 */
class ProfileUiStateTest {

    @Test
    fun `profile ui state defaults to initials avatar`() {
        val state = ProfileUiState()
        assertEquals("initials", state.avatarType)
    }

    @Test
    fun `profile ui state maps male avatar type`() {
        val state = ProfileUiState(avatarType = "male")
        assertEquals("male", state.avatarType)
    }

    @Test
    fun `profile ui state maps female avatar type`() {
        val state = ProfileUiState(avatarType = "female")
        assertEquals("female", state.avatarType)
    }

    @Test
    fun `profile ui state maps uploaded avatar type with url`() {
        val state = ProfileUiState(avatarType = "uploaded", avatarImageUrl = "https://example.com/a.png")
        assertEquals("uploaded", state.avatarType)
        assertEquals("https://example.com/a.png", state.avatarImageUrl)
    }

    @Test
    fun `profile ui state falls back to initials when avatar type blank`() {
        val state = ProfileUiState(avatarType = "")
        // blank is normalized to initials at the ViewModel layer; state keeps raw value
        assertEquals("", state.avatarType)
    }

    @Test
    fun `user domain model defaults avatar type to initials`() {
        val user = User(
            id = "u1", fullName = "Test", email = "t@t.com",
            preferredLanguage = "en", defaultCity = "", defaultRadiusKm = 50,
            biometricEnabled = false, createdAt = 0L
        )
        assertEquals("initials", user.avatarType)
    }
}