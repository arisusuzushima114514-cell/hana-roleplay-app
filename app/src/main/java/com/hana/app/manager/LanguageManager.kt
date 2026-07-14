package com.hana.app.manager

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageManager {
    fun applyLanguage(languageCode: String) {
        val target = LocaleListCompat.forLanguageTags(languageCode)
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() == target.toLanguageTags()) return
        AppCompatDelegate.setApplicationLocales(target)
    }
}
