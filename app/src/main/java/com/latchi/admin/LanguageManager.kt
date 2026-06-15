package com.latchi.admin

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import java.util.Locale

object LanguageManager {
    private const val PREFS = "admin_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun wrap(context: Context): Context {
        val language = getLanguage(context)
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "ar") ?: "ar"
    }

    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    fun restart(activity: android.app.Activity) {
        val intent = Intent(activity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }

    fun languageLabel(code: String): String = when (code) {
        "en" -> "🇬🇧 English"
        "fr" -> "🇫🇷 Français"
        else -> "🇸🇦 العربية"
    }
}
