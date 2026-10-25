package com.leslie.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** App-specific Top Shelf sources. Public catalogues never masquerade as signed-in recommendations. */
object Shelf {
    data class Item(val title: String, val meta: String, val image: String, val alternateImages: List<String> = emptyList())
    data class Source(val name: String, val ttl: Long, val fetch: (Context) -> List<Item>)

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    private const val LIVE_TTL = 15 * 60_000L
    private const val CATALOG_TTL = 2 * 60 * 60_000L

    private val sources get() = mapOf(
        "com.xiaodianshi.tv.yst" to Source(I18n.text(R.string.i18n_shelf_001), LIVE_TTL) { bilibili() },
        "com.douyu.xl.douyutv" to Source(I18n.text(R.string.i18n_shelf_002), LIVE_TTL) { douyu() },
        YouTubeChannels.PACKAGE to Source(I18n.text(R.string.i18n_shelf_007), LIVE_TTL, YouTubeChannels::read),
    )
    private val cache = HashMap<String, Pair<Long, List<Item>>>()

    // A newly installed streaming app may match JustWatch's Taiwan provider list by its app label.
    // Resolve the provider off the UI thread in items(), never while focus is moving.
    fun has(pkg: String) = pkg.isNotBlank()
    fun sourceName(pkg: String, label: String) = sources[pkg]?.name
        ?: JustWatchCatalog.matchedName(pkg, label)?.let { I18n.text(R.string.i18n_shelf_008, it) }
        ?: label

    fun invalidate(pkg: String) { synchronized(cache) { cache.remove(pkg) } }
    fun refreshDelay(pkg: String): Long = sources[pkg]?.ttl ?: CATALOG_TTL

    /** Items for [pkg], cached for a while; empty if the app has no shelf or the network failed. */
    fun items(ctx: Context, pkg: String, label: String): List<Item> {
        val fixed = sources[pkg]
        synchronized(cache) {
            val ttl = fixed?.ttl ?: CATALOG_TTL
            cache[pkg]?.let { (at, list) -> if (System.currentTimeMillis() - at < ttl) return list }
        }
        val src = fixed ?: run {
            val provider = JustWatchCatalog.match(ctx, pkg, label) ?: return emptyList()
            Source(I18n.text(R.string.i18n_shelf_008, provider.name), CATALOG_TTL) {
                JustWatchCatalog.titles(provider)
            }
        }
        val list = try { src.fetch(ctx) } catch (e: Exception) { Log.w("Shelf", "$pkg failed", e); emptyList() }
        if (list.isNotEmpty()) synchronized(cache) { cache[pkg] = System.currentTimeMillis() to list }
        return list
    }

    /** Downloads (or reads from the disk cache) an item's picture. */
    fun image(ctx: Context, item: Item): Bitmap? = (listOf(item.image) + item.alternateImages)
        .firstNotNullOfOrNull { imageUrl(ctx, it) }

    private fun imageUrl(ctx: Context, url: String): Bitmap? {
        if (url.startsWith("content://")) return try {
            ctx.contentResolver.openInputStream(Uri.parse(url))?.use { BitmapFactory.decodeStream(it) }
                ?.let(::trimBars)?.let(::darken)?.also { it.prepareToDraw() }
        } catch (e: Exception) { Log.w("Shelf", "content image failed", e); null }
        if (!url.startsWith("https://")) return null
        val dir = File(ctx.cacheDir, "shelf").apply { mkdirs() }
        val f = File(dir, Integer.toHexString(url.hashCode()) + ".jpg")
        if (!f.exists()) try {
            val tmp = File(dir, f.name + ".part")
            tmp.writeBytes(get(url))
            tmp.renameTo(f)
            // Keep the cache small: the newest 60 pictures.
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(60)?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w("Shelf", "image $url failed", e)
            return null
        }
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?.let(::trimBars)
            ?.let(::darken)
            // Start the GPU upload now, off the frame that first shows it.
            ?.also { it.prepareToDraw() }
    }

    /**
     * Bakes the scrim that keeps the title readable into the picture itself:
     * a full-screen gradient layer on top would cost the TV's GPU three more
     * full-screen passes on every frame of a cross-fade.
     */
    private fun darken(b: Bitmap): Bitmap {
        val out = if (b.isMutable) b else b.copy(Bitmap.Config.ARGB_8888, true).also { b.recycle() }
        ShelfScrim().apply { setBounds(0, 0, out.width, out.height) }.draw(android.graphics.Canvas(out))
        return out
    }

    /** Live-stream covers are often letterboxed; cut the black bars so the picture fills the screen. */
    private fun trimBars(b: Bitmap): Bitmap {
        fun dark(y: Int): Boolean {
            var max = 0
            for (k in 0 until 32) {
                val c = b.getPixel(k * (b.width - 1) / 31, y)
                max = maxOf(max, (c shr 16) and 255, (c shr 8) and 255, c and 255)
            }
            return max < 28
        }
        var top = 0
        while (top < b.height / 4 && dark(top)) top++
        var bottom = b.height
        while (bottom > b.height * 3 / 4 && dark(bottom - 1)) bottom--
        if (top < 4 && b.height - bottom < 4) return b
        return Bitmap.createBitmap(b, 0, top, b.width, bottom - top)
    }

    private fun bilibili(): List<Item> {
        val list = JSONObject(get("https://api.bilibili.com/x/web-interface/popular?ps=12&pn=1").decodeToString())
            .getJSONObject("data").getJSONArray("list")
        return (0 until list.length()).map { list.getJSONObject(it) }.map { v ->
            Item(
                v.optString("title"),
                I18n.text(R.string.i18n_shelf_003, v.getJSONObject("owner").optString("name"), count(v.getJSONObject("stat").optLong("view"))),
                v.optString("pic").replace("http://", "https://") + "@1280w_720h_1c.jpg",
            )
        }
    }

    private fun douyu(): List<Item> {
        val list = JSONObject(get("https://www.douyu.com/japi/weblist/apinc/allpage/6/1").decodeToString())
            .getJSONObject("data").getJSONArray("rl")
        return (0 until list.length()).map { list.getJSONObject(it) }
            .filter { it.optString("rs16").isNotEmpty() }
            .take(12)
            .map { r ->
                Item(
                    r.optString("rn"),
                    I18n.text(R.string.i18n_shelf_004, r.optString("nn"), r.optString("c2name"), count(r.optLong("ol"))),
                    // "/dy4" is the 1280×720 JPEG rendition of the AVIF cover.
                    r.optString("rs16").substringBeforeLast('/') + "/dy4",
                )
            }
    }

    private fun count(n: Long): String = if (I18n.isChinese()) {
        when {
            n >= 100_000_000 -> I18n.text(R.string.i18n_shelf_005, n / 100_000_000.0)
            n >= 10_000 -> I18n.text(R.string.i18n_shelf_006, n / 10_000.0)
            else -> n.toString()
        }
    } else {
        when {
            n >= 1_000_000 -> I18n.text(R.string.i18n_shelf_005, n / 1_000_000.0)
            n >= 1_000 -> I18n.text(R.string.i18n_shelf_006, n / 1_000.0)
            else -> n.toString()
        }
    }

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", UA)
        // B站's API answers with an empty body when sent a Referer from outside its pages.
        if ("douyu" in url) c.setRequestProperty("Referer", "https://www.douyu.com/")
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}
