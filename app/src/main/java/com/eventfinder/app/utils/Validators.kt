package com.eventfinder.app.utils

import java.util.regex.Pattern

/**
 * Pure validation logic for user input. Kept free of Android dependencies so it
 * can be exhaustively unit-tested on the JVM (see `app/src/test`).
 *
 * All checks return [ValidationResult]; the UI surfaces the collected error
 * messages without ever navigating on invalid input (Application Robustness
 * requirement).
 */
sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val messages: List<String>) : ValidationResult
}

/**
 * Email validation — RFC-5322 style practical regex (matches the common
 * pattern used by Android's `Patterns.EMAIL_ADDRESS`).
 */
object EmailValidator {
    private val EMAIL_REGEX =
        Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValid(email: String): Boolean = email.isNotBlank() && EMAIL_REGEX.matcher(email.trim()).matches()
}

/** Strength buckets returned by the password rules engine. */
enum class PasswordStrength { WEAK, MEDIUM, STRONG }

/**
 * Password policy derived from NIST SP 800-63B guidance for the prototype:
 * minimum length plus entropy checks (uppercase, digit, special character).
 */
object PasswordValidator {

    fun strength(password: String): PasswordStrength =
        when {
            password.length < 8 -> PasswordStrength.WEAK
            PasswordRules.filter { it.met(password) }.size >= PasswordRules.size -> PasswordStrength.STRONG
            else -> PasswordStrength.MEDIUM
        }

    fun validate(password: String): ValidationResult {
        val missing = PasswordRules.filterNot { it.met(password) }
        return if (missing.isEmpty()) ValidationResult.Valid
        else ValidationResult.Invalid(missing.map { it.messageResId })
    }

    private val PasswordRules = listOf(
        Rule({ it.length >= 8 }, "password_too_short"),
        Rule({ it.any(Char::isUpperCase) }, "password_no_upper"),
        Rule({ it.any(Char::isDigit) }, "password_no_digit"),
        Rule({ it.any { !it.isLetterOrDigit() } }, "password_no_special")
    )

    private data class Rule(val met: (String) -> Boolean, val messageResId: String)

    /** Use the message key as a stable identifier for the UI to resolve */
    fun ruleMessageKeys(password: String): List<String> =
        PasswordRules.filterNot { it.met(password) }.map { it.messageResId }
}

/**
 * Registration / profile form validation. Every field is checked and all errors
 * are returned at once so the user can correct them in a single pass.
 */
object RegistrationValidator {

    fun validateName(name: String): ValidationResult =
        when {
            name.isBlank() -> ValidationResult.Invalid(listOf("name_required"))
            name.trim().length < 2 -> ValidationResult.Invalid(listOf("name_too_short"))
            else -> ValidationResult.Valid
        }

    fun validateForm(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): ValidationResult {
        val errors = mutableListOf<String>()

        when (val nameResult = validateName(fullName)) {
            is ValidationResult.Invalid -> errors += nameResult.messages
            else -> Unit
        }

        if (!EmailValidator.isValid(email)) {
            errors += "invalid_email"
        }

        when (PasswordValidator.validate(password)) {
            is ValidationResult.Invalid -> errors +=
                PasswordValidator.ruleMessageKeys(password)
            else -> Unit
        }

        if (password != confirmPassword) {
            errors += "passwords_mismatch"
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    fun validateLogin(email: String, password: String): ValidationResult {
        val errors = mutableListOf<String>()
        if (!EmailValidator.isValid(email)) errors += "invalid_email"
        if (password.isBlank()) errors += "invalid_password"
        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}