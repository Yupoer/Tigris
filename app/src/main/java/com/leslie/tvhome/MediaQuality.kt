package com.leslie.tvhome

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.view.Display

/**
 * Prefer sharp assets on capable 4K TVs without allocating 4K bitmaps or trying HEVC on
 * older/low-memory boxes. Quality can be overridden in Settings by an informed user.
 */
internal object MediaQuality {
    const val AUTO = "auto"
    const val COMPAT = "compat"
    const val UHD = "uhd"

    fun displayIsUhd(ctx: Context): Boolean {
        val manager = ctx.getSystemService(DisplayManager::class.java)
        val mode = manager?.getDisplay(Display.DEFAULT_DISPLAY)?.mode
        return mode != null && maxOf(mode.physicalWidth, mode.physicalHeight) >= 3840 &&
            minOf(mode.physicalWidth, mode.physicalHeight) >= 2160
    }

    fun enoughMemory(ctx: Context): Boolean {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return !am.isLowRamDevice && info.totalMem >= 3L * 1024 * 1024 * 1024
    }

    fun uhdWallpapers(ctx: Context): Boolean = when (Prefs(ctx).wallQuality) {
        UHD -> true
        COMPAT -> false
        else -> displayIsUhd(ctx) && enoughMemory(ctx)
    }

    fun hevcDecoderPresent(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
            !codec.isEncoder && codec.supportedTypes.any { it.equals("video/hevc", true) }
        }
    }.getOrDefault(false)

    fun canDecode4kHevc(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
            !codec.isEncoder && codec.supportedTypes.any { mime ->
                mime.equals("video/hevc", true) &&
                    codec.getCapabilitiesForType(mime).videoCapabilities
                        .areSizeAndRateSupported(3840, 2160, 24.0)
            }
        }
    }.getOrDefault(false)

    fun uhdAerial(ctx: Context): Boolean = when (Prefs(ctx).aerialQuality) {
        COMPAT -> false
        UHD -> displayIsUhd(ctx) && canDecode4kHevc()
        else -> displayIsUhd(ctx) && enoughMemory(ctx) && canDecode4kHevc()
    }

    /** The 4K address is only a candidate: not all Apple's clips have a UHD HEVC file. */
    fun aerialCandidates(avc: String, hevc: String?, want4k: Boolean, allowHevc: Boolean): List<String> =
        buildList {
            if (allowHevc && !hevc.isNullOrBlank()) {
                if (want4k && hevc.endsWith("_2K_HEVC.mov")) add(hevc.removeSuffix("_2K_HEVC.mov") + "_4K_HEVC.mov")
                add(hevc)
            }
            add(avc)
        }.distinct()
}
