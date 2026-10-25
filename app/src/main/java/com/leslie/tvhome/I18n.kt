package com.leslie.tvhome

import android.content.Context

/**
 * Single entry point for UI translations.
 *
 * Android resolves the resource itself: values/ is English (fallback),
 * values-zh/ is Chinese. No preference, remote permission or network access.
 * Keep language-specific copy out of domain logic and machine-readable values.
 */
object I18n {
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun text(resourceId: Int, vararg arguments: Any?): String {
        val resources = checkNotNull(appContext) { "I18n.initialize() must run in App.onCreate()" }.resources
        return if (arguments.isEmpty()) resources.getString(resourceId)
        else resources.getString(resourceId, *arguments)
    }
}
