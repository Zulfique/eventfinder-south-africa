package com.eventfinder.app.domain.model

/**
 * Languages supported by the app (FR-08 bilingual English / Afrikaans).
 */
enum class SupportedLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    AFRIKAANS("af", "Afrikaans");

    companion object {
        fun fromCode(code: String): SupportedLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}