package com.example.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(val code: String, val toggleBadge: String, val fullName: String) {
    ENGLISH(code = "en", toggleBadge = "தமிழ்", fullName = "English"),
    TAMIL(code = "ta", toggleBadge = "EN", fullName = "தமிழ்")
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

/**
 * Helper to pick English or Tamil string based on active [AppLanguage].
 */
fun tr(lang: AppLanguage, en: String, ta: String): String {
    return if (lang == AppLanguage.TAMIL) ta else en
}
