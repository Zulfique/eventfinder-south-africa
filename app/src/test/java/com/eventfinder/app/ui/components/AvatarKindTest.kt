package com.eventfinder.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * State mapping for the profile avatar: an uploaded photo always wins,
 * then the chosen generated style (1 = male, 2 = female), then initials.
 * Guards the fallback order the UI depends on.
 */
class AvatarKindTest {

    @Test
    fun `uploaded photo wins over any style`() {
        assertEquals(AvatarKind.PHOTO, resolveAvatarKind(avatarStyle = 1, hasPhoto = true))
        assertEquals(AvatarKind.PHOTO, resolveAvatarKind(avatarStyle = 2, hasPhoto = true))
        assertEquals(AvatarKind.PHOTO, resolveAvatarKind(avatarStyle = 0, hasPhoto = true))
    }

    @Test
    fun `style 1 maps to the male silhouette`() {
        assertEquals(AvatarKind.MALE, resolveAvatarKind(avatarStyle = 1, hasPhoto = false))
    }

    @Test
    fun `style 2 maps to the female silhouette`() {
        assertEquals(AvatarKind.FEMALE, resolveAvatarKind(avatarStyle = 2, hasPhoto = false))
    }

    @Test
    fun `default and unknown styles fall back to initials`() {
        assertEquals(AvatarKind.INITIALS, resolveAvatarKind(avatarStyle = 0, hasPhoto = false))
        assertEquals(AvatarKind.INITIALS, resolveAvatarKind(avatarStyle = 99, hasPhoto = false))
        assertEquals(AvatarKind.INITIALS, resolveAvatarKind(avatarStyle = -1, hasPhoto = false))
    }
}
