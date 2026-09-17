package com.eventfinder.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure validation engine.
 *
 * These tests document the input rules the UI relies on (NFR robustness): the
 * app must never navigate or persist data when input fails validation.
 */
class ValidatorsTest {

    // ---------------------------------------------------------------- Email

    @Test
    fun `accepts well formed email addresses`() {
        val valid = listOf(
            "thabo@eventfinder.co.za",
            "a.b-c_d+e@mail.example.org",
            "  spaced@example.com  "
        )
        valid.forEach { assertTrue("expected '$it' to be valid", EmailValidator.isValid(it)) }
    }

    @Test
    fun `rejects malformed email addresses`() {
        val invalid = listOf("", "   ", "plain", "no-at.com", "user@", "@host.com", "user@host", "user@host.c")
        invalid.forEach { assertFalse("expected '$it' to be invalid", EmailValidator.isValid(it)) }
    }

    // ------------------------------------------------------------- Password

    @Test
    fun `password strength buckets respond to length and entropy rules`() {
        assertEquals(PasswordStrength.WEAK, PasswordValidator.strength("Short1!"))
        assertEquals(PasswordStrength.MEDIUM, PasswordValidator.strength("password"))
        assertEquals(PasswordStrength.MEDIUM, PasswordValidator.strength("Password1"))
        assertEquals(PasswordStrength.STRONG, PasswordValidator.strength("Password1!"))
    }

    @Test
    fun `password validation reports every unmet rule`() {
        val result = PasswordValidator.validate("abc")
        assertTrue(result is ValidationResult.Invalid)
        val keys = (result as ValidationResult.Invalid).messages
        assertTrue(keys.contains("password_too_short"))
        assertTrue(keys.contains("password_no_upper"))
        assertTrue(keys.contains("password_no_digit"))
        assertTrue(keys.contains("password_no_special"))
    }

    @Test
    fun `password validation passes for a compliant password`() {
        assertEquals(ValidationResult.Valid, PasswordValidator.validate("Springbok2026!"))
        assertTrue(PasswordValidator.ruleMessageKeys("Springbok2026!").isEmpty())
    }

    // --------------------------------------------------------------- Name

    @Test
    fun `name validation distinguishes blank from too short`() {
        assertEquals(
            ValidationResult.Invalid(listOf("name_required")),
            RegistrationValidator.validateName("   ")
        )
        assertEquals(
            ValidationResult.Invalid(listOf("name_too_short")),
            RegistrationValidator.validateName("A")
        )
        assertEquals(ValidationResult.Valid, RegistrationValidator.validateName("Thabo"))
    }

    // ---------------------------------------------------- Registration form

    @Test
    fun `registration form aggregates all errors in a single pass`() {
        val result = RegistrationValidator.validateForm(
            fullName = "",
            email = "not-an-email",
            password = "weak",
            confirmPassword = "different"
        )
        assertTrue(result is ValidationResult.Invalid)
        val keys = (result as ValidationResult.Invalid).messages
        assertTrue(keys.contains("name_required"))
        assertTrue(keys.contains("invalid_email"))
        assertTrue(keys.contains("password_too_short"))
        assertTrue(keys.contains("passwords_mismatch"))
    }

    @Test
    fun `registration form accepts valid input`() {
        assertEquals(
            ValidationResult.Valid,
            RegistrationValidator.validateForm(
                fullName = "Naledi Mokoena",
                email = "naledi@example.co.za",
                password = "Amapiano2026!",
                confirmPassword = "Amapiano2026!"
            )
        )
    }

    @Test
    fun `login validation only requires a non blank password and valid email`() {
        assertEquals(ValidationResult.Valid, RegistrationValidator.validateLogin("a@b.co", "anything"))
        assertTrue(RegistrationValidator.validateLogin("bad", "") is ValidationResult.Invalid)
    }
}
