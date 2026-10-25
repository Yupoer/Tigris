package com.leslie.tvhome

import android.content.Context
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri
import java.util.Locale

/** The YouTube rows that the installed TV app publishes through Android TV's TvProvider. */
object YouTubeChannels {
    const val PACKAGE = "com.google.android.youtube.tv"
    private const val SCAN_LIMIT = 5_000
    private const val ITEM_LIMIT = 100
    private data class Channel(val id: Long, val name: String, val priority: Int)

    internal fun channelPriority(name: String?): Int = when (name?.trim()?.lowercase(Locale.ROOT)) {
        "為你推薦", "recommended", "recommended for you" -> 0
        "發燒影片", "發燒", "trending" -> 1
        else -> -1
    }

    fun read(ctx: Context): List<Shelf.Item> {
      return try {
        val channels = ArrayList<Channel>()
        ctx.contentResolver.query(TvContract.Channels.CONTENT_URI,
            arrayOf("_id", "package_name", "display_name"), null, null, null)?.use { cursor ->
            var scanned = 0
            while (scanned++ < SCAN_LIMIT && cursor.moveToNext()) {
                if (cursor.getString(1) != PACKAGE) continue
                val name = cursor.getString(2) ?: continue
                val priority = channelPriority(name)
                if (priority >= 0) channels.add(Channel(cursor.getLong(0), name, priority))
            }
        }
        for (channel in channels.sortedBy { it.priority }) {
            val items = preview(ctx, channel)
            // As in the private launcher, choose the first nonempty YouTube recommendation channel.
            if (items.isNotEmpty()) return items
        }
        watchNext(ctx)
      } catch (_: SecurityException) { emptyList() }
        catch (_: RuntimeException) { emptyList() }
    }

    private fun preview(ctx: Context, channel: Channel): List<Shelf.Item> {
        val uri = TvContract.buildPreviewProgramsUriForChannel(channel.id)
        val columns = arrayOf("package_name", "title", "poster_art_uri", "thumbnail_uri",
            TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
            TvContract.PreviewPrograms.COLUMN_THUMBNAIL_ASPECT_RATIO)
        val items = ArrayList<Shelf.Item>()
        ctx.contentResolver.query(uri, columns, null, null, "weight DESC, _id ASC")?.use { cursor ->
            var scanned = 0
            while (scanned++ < SCAN_LIMIT && cursor.moveToNext() && items.size < ITEM_LIMIT) {
                if (cursor.getString(0) != PACKAGE) continue
                val title = cursor.getString(1)?.trim().orEmpty()
                if (title.isEmpty() || title.length > 500) continue
                val images = imageCandidates(ctx, cursor, 2, 3, 4, 5)
                if (images.isNotEmpty()) items.add(Shelf.Item(title, "", images.first(), images.drop(1)))
            }
        }
        return items
    }

    private fun watchNext(ctx: Context): List<Shelf.Item> {
        val columns = arrayOf("package_name", "title", "poster_art_uri", "thumbnail_uri",
            TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
            TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_ASPECT_RATIO,
            TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_BROWSABLE)
        val rows = ArrayList<Pair<Long, Shelf.Item>>()
        ctx.contentResolver.query(TvContract.WatchNextPrograms.CONTENT_URI, columns, null, null, null)?.use { cursor ->
            var scanned = 0
            while (scanned++ < SCAN_LIMIT && cursor.moveToNext()) {
                if (cursor.getString(0) != PACKAGE || (!cursor.isNull(7) && cursor.getInt(7) != 1)) continue
                val title = cursor.getString(1)?.trim().orEmpty()
                if (title.isEmpty() || title.length > 500) continue
                val images = imageCandidates(ctx, cursor, 2, 3, 4, 5)
                if (images.isEmpty()) continue
                val time = if (cursor.isNull(6)) 0L else cursor.getLong(6)
                rows.add(time to Shelf.Item(title, I18n.text(R.string.i18n_shelf_009), images.first(), images.drop(1)))
            }
        }
        return rows.sortedByDescending { it.first }.take(ITEM_LIMIT).map { it.second }
    }

    private fun imageCandidates(ctx: Context, cursor: Cursor, poster: Int, thumb: Int,
                                posterAspect: Int, thumbAspect: Int): List<String> {
        val candidates = LinkedHashSet<String>()
        val posterUrl = ownedImage(ctx, cursor.getString(poster))
        val thumbUrl = ownedImage(ctx, cursor.getString(thumb))
        if (landscape(cursor, thumbAspect) && thumbUrl != null) candidates.add(thumbUrl)
        if (landscape(cursor, posterAspect) && posterUrl != null) candidates.add(posterUrl)
        if (thumbUrl != null) candidates.add(thumbUrl)
        if (posterUrl != null) candidates.add(posterUrl)
        val images = ArrayList<String>()
        for (candidate in candidates) maximumThumbnail(candidate)?.let { images.add(it) }
        images.addAll(candidates)
        return images.distinct()
    }

    private fun landscape(cursor: Cursor, at: Int) = !cursor.isNull(at) && cursor.getInt(at) in setOf(
        TvContract.PreviewPrograms.ASPECT_RATIO_16_9,
        TvContract.PreviewPrograms.ASPECT_RATIO_3_2,
        TvContract.PreviewPrograms.ASPECT_RATIO_4_3)

    private fun ownedImage(ctx: Context, text: String?): String? {
        if (text.isNullOrBlank() || text.length > 4_096) return null
        val uri = Uri.parse(text)
        return when (uri.scheme) {
            "https" -> if (uri.host != null && uri.encodedAuthority?.contains('@') != true) text else null
            "content" -> {
                val authority = uri.authority ?: return null
                if (authority == "android.media.tv" ||
                    ctx.packageManager.resolveContentProvider(authority, 0)?.packageName == PACKAGE) text else null
            }
            else -> null
        }
    }

    /** Try the official highest-resolution YouTube thumbnail, then the image the app published. */
    internal fun maximumThumbnail(published: String): String? {
        val uri = Uri.parse(published)
        val host = uri.host ?: return null
        if (uri.scheme != "https" || uri.port != -1 || uri.encodedAuthority?.contains('@') == true ||
            host !in setOf("i.ytimg.com", "img.youtube.com", "i1.ytimg.com", "i2.ytimg.com",
                "i3.ytimg.com", "i4.ytimg.com")) return null
        val video = Regex("^/(?:vi|vi_webp)/([A-Za-z0-9_-]{11})/[^/]+\\.(?:jpg|webp)$")
            .matchEntire(uri.path ?: "")?.groupValues?.get(1) ?: return null
        return "https://i.ytimg.com/vi/$video/maxresdefault.jpg"
    }
}
