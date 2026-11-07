package com.leslie.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.File

/** References Google TV's own cached images. Neither the cache nor a second photo library is written. */
object GoogleTvPhotos {
    private const val DIRECTORY = "/sdcard/Android/data/com.google.android.apps.tv.dreamx/files/Pictures/cache"
    private const val MAX_BYTES = 8 * 1024 * 1024
    private val namePattern = Regex("[a-f0-9]{64}\\.0")
    @Volatile private var photos = emptyList<File>()
    private var refreshedAt = 0L

    fun files(): List<File> = photos

    /** Run on a worker. UI callers only read the last inventory, never open an ADB connection. */
    @Synchronized fun refresh(ctx: Context, force: Boolean = false) {
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && refreshedAt != 0L && now - refreshedAt < 30_000L) return
        refreshedAt = now
        val listing = LocalAdb.shell(ctx,
            "find $DIRECTORY -maxdepth 1 -type f -name '*.0' -exec stat -c '%n|%s|%Y' {} +", 15_000)
            ?: run { refreshedAt = 0L; return }
        photos = listing.lineSequence().mapNotNull { line ->
            val parts = line.trim().split('|')
            if (parts.size != 3) return@mapNotNull null
            val file = File(parts[0])
            val size = parts[1].toLongOrNull() ?: return@mapNotNull null
            val modified = parts[2].toLongOrNull() ?: return@mapNotNull null
            if (file.parent != DIRECTORY || !namePattern.matches(file.name) || size !in 4096..MAX_BYTES.toLong())
                return@mapNotNull null
            file to modified
        }.sortedByDescending { it.second }.take(128).map { it.first }.toList()
        Log.i("GoogleTvPhotos", "Indexed ${photos.size} original Google TV cache files (read-only)")
    }

    fun owns(file: File): Boolean = file.parent == DIRECTORY && namePattern.matches(file.name)

    /** Decode the original file in memory; no temporary file, download, or copy is created. */
    fun decode(ctx: Context, file: File, options: BitmapFactory.Options): Bitmap? {
        if (!owns(file)) return null
        return runCatching {
            // Recheck the size because Google TV can replace or evict a file after indexing it.
            val encoded = LocalAdb.shell(ctx,
                "f='$DIRECTORY/${file.name}'; n=\$(stat -c %s \"\$f\") || exit; " +
                    "[ \"\$n\" -le $MAX_BYTES ] && head -c $MAX_BYTES \"\$f\" | base64", 10_000)
                ?.takeIf { it.length <= (MAX_BYTES * 4 / 3) + 200_000 } ?: return null
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options).also {
                if (it != null) Log.d("GoogleTvPhotos", "Decoded original ${file.name}: ${it.width}x${it.height}; no file copied")
            }
        }.onFailure { Log.w("GoogleTvPhotos", "Original cached picture unavailable", it) }.getOrNull()
    }
}
