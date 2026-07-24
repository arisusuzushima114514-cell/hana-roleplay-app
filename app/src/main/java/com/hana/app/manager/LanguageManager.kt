package com.hana.app.manager

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LanguageManager {
    fun applyLanguage(languageCode: String) {
        val target = LocaleListCompat.forLanguageTags(languageCode)
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() == target.toLanguageTags()) return
        AppCompatDelegate.setApplicationLocales(target)
    }

    /** 手动切换 Locale 配置（兼容旧版 API） */
    fun applyLocaleConfig(context: Context, languageCode: String): Context {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
