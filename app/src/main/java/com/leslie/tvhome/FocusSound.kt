package com.leslie.tvhome

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.provider.Settings
import android.view.View

/** Preloaded, short navigation feedback; never changes the TV's volume or sound settings. */
class FocusSound(private val ctx: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    private var ready = false
    private var enabled = false
    private var lastPlay = -1L
    private var stream = 0
    private val sound: Int

    init {
        pool.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
        sound = pool.load(ctx, R.raw.focus_tick, 1)
    }

    fun refresh() {
        enabled = Prefs(ctx).navigationSound &&
            Settings.System.getInt(ctx.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0
    }

    fun play(view: View, dx: Int, dy: Int) {
        if (!enabled || !ready || !view.isSoundEffectsEnabled) return
        val now = SystemClock.uptimeMillis()
        // A held D-pad can generate several events in one frame; keep them from stacking.
        if (lastPlay >= 0 && now - lastPlay < 45L) return
        lastPlay = now
        if (stream != 0) pool.stop(stream)
        val pan = dx.coerceIn(-1, 1) * 0.08f
        val rate = when { dy < 0 -> 1.04f; dy > 0 -> 0.96f; else -> 1f }
        stream = pool.play(sound, 0.42f - pan, 0.42f + pan, 1, 0, rate)
    }

    fun release() {
        ready = false
        pool.release()
    }
}
