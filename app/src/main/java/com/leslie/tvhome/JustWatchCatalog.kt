package com.leslie.tvhome

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Taiwan availability and artwork from the same public JustWatch GraphQL endpoint Hume uses. */
object JustWatchCatalog {
    data class Provider(val code: String, val name: String, val technicalName: String)

    private const val ENDPOINT = "https://apis.justwatch.com/graphql"
    private const val PROVIDER_TTL = 24 * 60 * 60_000L
    private const val PROVIDERS_QUERY = """
        query Providers(${"$"}country: Country!) {
          packages(country: ${"$"}country, platform: WEB) { shortName clearName technicalName }
        }
    """
    private const val TITLES_QUERY = """
        query Titles(${"$"}country: Country!, ${"$"}language: Language!, ${"$"}first: Int!, ${"$"}filter: TitleFilter) {
          popularTitles(country: ${"$"}country, filter: ${"$"}filter, first: ${"$"}first,
                        sortBy: POPULAR, sortRandomSeed: 0) {
            edges { node { id content(country: ${"$"}country, language: ${"$"}language) {
              title originalReleaseYear backdrops(profile: S1920, format: JPG) { backdropUrl }
            } } }
          }
        }
    """
    private val knownPackages = mapOf(
        "com.netflix.ninja" to "nfx",
        "com.disney.disneyplus" to "dnp",
        "com.apple.atve.androidtv.appletv" to "atp",
        "com.amazon.amazonvideo.livingroom" to "prv",
        "com.wbd.stream" to "mxx",
        "com.hbo.hbonow" to "mxx",
    )
    private val lock = Any()
    @Volatile private var providers: List<Provider> = emptyList()
    @Volatile private var providersAt = 0L

    private fun normalized(text: String): String = text.lowercase(Locale.ROOT)
        .replace("+", "plus").replace(Regex("[^a-z0-9\\p{L}]+"), "")

    /** Exact platform-name matching is intentional; fuzzy matching can show the wrong catalogue. */
    internal fun select(pkg: String, label: String, available: List<Provider>): Provider? {
        knownPackages[pkg]?.let { code -> return available.firstOrNull { it.code == code } }
        val name = normalized(label)
        if (name.isEmpty()) return null
        return available.firstOrNull { normalized(it.name) == name || normalized(it.technicalName) == name }
    }

    fun matchedName(pkg: String, label: String): String? = select(pkg, label, providers)?.name

    fun match(ctx: Context, pkg: String, label: String): Provider? {
        val list = available(ctx)
        return select(pkg, label, list) ?: knownPackages[pkg]?.let { Provider(it, label, it) }
    }

    private fun available(ctx: Context): List<Provider> {
        val now = System.currentTimeMillis()
        if (providers.isNotEmpty() && now - providersAt < PROVIDER_TTL) return providers
        synchronized(lock) {
            if (providers.isNotEmpty() && now - providersAt < PROVIDER_TTL) return providers
            val file = File(ctx.cacheDir, "justwatch-providers-tw.json")
            if (file.exists() && now - file.lastModified() < PROVIDER_TTL) {
                runCatching { parseProviders(file.readText()) }.getOrDefault(emptyList())
                    .takeIf { it.isNotEmpty() }?.let {
                    providers = it; providersAt = file.lastModified(); return it
                }
            }
            return try {
                val body = request(PROVIDERS_QUERY, JSONObject().put("country", "TW"))
                val list = parseProviders(body)
                if (list.isNotEmpty()) {
                    providers = list
                    providersAt = now
                    runCatching { file.writeText(body) }
                    list
                } else providers
            } catch (_: Exception) {
                if (providers.isNotEmpty()) providers
                else if (file.exists()) runCatching { parseProviders(file.readText()) }.getOrDefault(emptyList())
                else emptyList()
            }
        }
    }

    private fun parseProviders(body: String): List<Provider> {
        val rows = JSONObject(body).optJSONObject("data")?.optJSONArray("packages") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val code = row.optString("shortName")
            val name = row.optString("clearName")
            if (code.isBlank() || name.isBlank()) null
            else Provider(code, name, row.optString("technicalName"))
        }
    }

    fun titles(provider: Provider): List<Shelf.Item> {
        val filter = JSONObject().put("packages", JSONArray().put(provider.code))
            .put("objectTypes", JSONArray().put("SHOW").put("MOVIE"))
            .put("monetizationTypes", JSONArray().put("FLATRATE").put("FREE").put("ADS"))
        val variables = JSONObject().put("country", "TW").put("language", "zh")
            .put("first", 48).put("filter", filter)
        val rows = JSONObject(request(TITLES_QUERY, variables)).getJSONObject("data")
            .getJSONObject("popularTitles").getJSONArray("edges")
        val seen = HashSet<String>()
        val result = ArrayList<Shelf.Item>()
        for (index in 0 until rows.length()) {
            val node = rows.optJSONObject(index)?.optJSONObject("node") ?: continue
            val content = node.optJSONObject("content") ?: continue
            val id = node.optString("id")
            val title = content.optString("title").trim()
            if (id.isBlank() || title.isBlank() || id in seen) continue
            val backdrops = content.optJSONArray("backdrops") ?: continue
            val path = (0 until backdrops.length()).mapNotNull { backdrops.optJSONObject(it)?.optString("backdropUrl") }
                .firstOrNull { it.startsWith("/backdrop/") && "/s1920/" in it && ".." !in it } ?: continue
            seen.add(id)
            val year = content.optInt("originalReleaseYear").takeIf { it in 1900..2100 }?.toString() ?: ""
            result.add(Shelf.Item(title, year, "https://images.justwatch.com$path"))
            if (result.size == 12) break
        }
        return result
    }

    private fun request(query: String, variables: JSONObject): String {
        val body = JSONObject().put("query", query).put("variables", variables).toString().toByteArray(Charsets.UTF_8)
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 12_000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV)")
        return try {
            conn.outputStream.use { it.write(body) }
            if (conn.responseCode != 200) throw java.io.IOException("JustWatch HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }
}
