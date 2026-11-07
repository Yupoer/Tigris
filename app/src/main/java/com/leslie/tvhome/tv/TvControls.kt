package com.leslie.tvhome.tv

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Brightness, volume, HDMI inputs and the system settings shortcuts for the Control Center.
 *
 * The first adapter in [brands] whose [TvAdapter.matches] is true handles this TV; when none
 * does, [AndroidAdapter] (standard Android APIs) does. Brand adapters extend AndroidAdapter, so
 * whatever a brand doesn't override falls back to standard Android.
 *
 * To support another brand: add `<Brand>Adapter.kt` to this package (see [CoocaaAdapter] for a
 * small one) and list it in [brands]. Every call except the two intents may block
 * (HTTP, settings, PackageManager), so call them off the main thread.
 */
class TvControls private constructor(private val ctx: Context, adapter: TvAdapter) : TvAdapter by adapter {
    constructor(ctx: Context) : this(ctx, pick(ctx))

    fun openSettings(): Boolean {
        val preferred = runCatching { settingsIntent() }
            .onFailure { Log.w(AndroidAdapter.TAG, "Cannot resolve TV settings", it) }.getOrNull()
        for (intent in listOfNotNull(preferred, Intent(Settings.ACTION_SETTINGS))) {
            try {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                Log.w(AndroidAdapter.TAG, "Cannot open TV settings with $intent", e)
            }
        }
        return false
    }

    companion object {
        /** Tried in order. Keep the ones that detect by brand before any that detect by installed packages. */
        private fun brands(ctx: Context): List<TvAdapter> = listOf(
            SonyAdapter(ctx),
            XiaomiAdapter(ctx),
            TclAdapter(ctx),
            CoocaaAdapter(ctx),
        )

        private fun pick(ctx: Context): TvAdapter = brands(ctx).firstOrNull { it.matches() } ?: AndroidAdapter(ctx)
    }
}
