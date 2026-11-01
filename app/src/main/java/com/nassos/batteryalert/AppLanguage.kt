package com.nassos.batteryalert

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** In-app language choice, applied by wrapping the Activity and Service base contexts. */
object AppLanguage {
    val supported = listOf("el", "en")

    fun wrap(base: Context): Context {
        val tag = AlertPrefs(base).language ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return base.createConfigurationContext(config)
    }

    /** The language currently in effect: the saved choice, else the system language if supported. */
    fun current(context: Context): String =
        AlertPrefs(context).language
            ?: context.resources.configuration.locales[0].language.takeIf { it in supported }
            ?: "en"
}
