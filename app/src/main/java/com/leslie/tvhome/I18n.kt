package com.leslie.tvhome

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Single entry point for UI translations.
 *
 * Android resolves the resource itself: values/ is English (fallback),
 * values-zh/ is Chinese. No preference, remote permission or network access.
 * Keep language-specific copy out of domain logic and machine-readable values.
 */
object I18n {
    @Volatile private var appContext: Context? = null
    @Volatile private var taiwanResources: Resources? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        taiwanResources = null
    }

    /** Resolve the active Android TV locale instead of caching a language choice. */
    fun isChinese(): Boolean = checkNotNull(appContext) { "I18n.initialize() must run in App.onCreate()" }
        .resources.configuration.locales[0].language.equals("zh", ignoreCase = true)

    fun text(resourceId: Int, vararg arguments: Any?): String {
        val ctx = checkNotNull(appContext) { "I18n.initialize() must run in App.onCreate()" }
        // This fork uses Taiwan wording for every Chinese system locale. A Chromecast set to
        // zh-CN must not silently fall back to the Simplified catalogue in its wallpaper UI.
        val base = ctx.resources
        val resources = if (base.configuration.locales[0].language.equals("zh", true))
            taiwanResources ?: synchronized(this) {
                taiwanResources ?: ctx.createConfigurationContext(Configuration(base.configuration).apply {
                    setLocale(Locale.forLanguageTag("zh-TW"))
                }).resources.also { taiwanResources = it }
            }
        else base
        return if (arguments.isEmpty()) resources.getString(resourceId)
        else resources.getString(resourceId, *arguments)
    }
}
