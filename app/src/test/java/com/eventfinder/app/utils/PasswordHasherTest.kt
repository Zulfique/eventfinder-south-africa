package com.eventfinder.app.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the PBKDF2 password storage helpers used by local authentication.
 *
 * The prototype never stores plaintext passwords (OWASP Password Storage
 * Cheat Sheet) and must always fail closed for malformed stored values.
 */
class PasswordHasherTest {

    @Test
    fun `hash verifies against the original password`() {
        val stored = PasswordHasher.hash("Springbok2026!")
        assertTrue(PasswordHasher.verify("Springbok2026!", stored))
    }

    @Test
    fun `verify rejects a wrong password`() {
        val stored = PasswordHasher.hash("Springbok2026!")
        assertFalse(PasswordHasher.verify("springbok2026!", stored))
    }

    @Test
    fun `each hash uses a unique salt`() {
        val first = PasswordHasher.hash("SamePassword1!")
        val second = PasswordHasher.hash("SamePassword1!")
        assertNotEquals(first, second)
        assertTrue(PasswordHasher.verify("SamePassword1!", first))
        assertTrue(PasswordHasher.verify("SamePassword1!", second))
    }

    @Test
    fun `stored value never contains the plaintext`() {
        val stored = PasswordHasher.hash("TopSecret42!")
        assertFalse(stored.contains("TopSecret42!"))
        assertTrue(stored.contains(":"))
    }

    @Test
    fun `verify fails closed on malformed stored values`() {
        assertFalse(PasswordHasher.verify("anything", ""))
        assertFalse(PasswordHasher.verify("anything", "not-a-valid-hash"))
        assertFalse(PasswordHasher.verify("anything", "salt:hash:extra"))
    }
}
