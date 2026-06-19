package com.latchi.admin

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PreparedCatalogBuilder {

    data class BuiltCatalogs(
        val liveJson: String,
        val beinJson: String,
        val moviesJson: String,
        val seriesJson: String,
        val liveCount: Int,
        val beinCount: Int,
        val moviesCount: Int,
        val seriesCount: Int
    )

    private data class PreparedItem(
        val name: String,
        val logoUrl: String,
        val streamUrl: String,
        val category: String,
        val contentType: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun build(
        rawSourceUrl: String,
        hiddenCategories: Set<String>,
        beinKeywords: List<String>,
        beinMaxKeywords: List<String>,
        alwanKeywords: List<String>
    ): BuiltCatalogs {
        val body = downloadM3u(normalizeSourceUrl(rawSourceUrl))
        val parsed = parseM3u(body)
        val curated = curateForArabicAudience(parsed).ifEmpty { parsed }

        val hidden = hiddenCategories.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val live = curated
            .filter { it.contentType == "live" && !hidden.contains(it.category.trim().lowercase()) }
            .distinctBy { it.streamUrl }
        val movies = curated
            .filter { it.contentType == "movie" && !hidden.contains(it.category.trim().lowercase()) }
            .distinctBy { it.streamUrl }
            .take(4000)
        val series = curated
            .filter { it.contentType == "series" && !hidden.contains(it.category.trim().lowercase()) }
            .distinctBy { it.streamUrl }
            .take(4000)

        fun text(item: PreparedItem) = "${item.name} ${item.category}".lowercase()
        fun containsAny(text: String, tokens: List<String>) = tokens.any { token -> text.contains(token.trim().lowercase()) }
        fun firstNumber(text: String): Int = Regex("\\d+").find(text)?.value?.toIntOrNull() ?: 999

        val bein = live
            .filter { item ->
                val t = text(item)
                containsAny(t, beinKeywords) || containsAny(t, alwanKeywords)
            }
            .sortedWith(
                compareBy<PreparedItem> { item ->
                    val t = text(item)
                    when {
                        containsAny(t, beinKeywords) && containsAny(t, beinMaxKeywords) -> 0
                        containsAny(t, beinKeywords) -> 1
                        containsAny(t, alwanKeywords) -> 2
                        else -> 3
                    }
                }.thenBy { firstNumber(text(it)) }.thenBy { text(it) }
            )

        return BuiltCatalogs(
            liveJson = toJson(live),
            beinJson = toJson(bein),
            moviesJson = toJson(movies),
            seriesJson = toJson(series),
            liveCount = live.size,
            beinCount = bein.size,
            moviesCount = movies.size,
            seriesCount = series.size
        )
    }

    private fun normalizeSourceUrl(raw: String): String {
        var url = raw.trim().replace("&amp;", "&")
        if (url.contains("get.php", ignoreCase = true)) {
            if (!url.contains("type=", ignoreCase = true)) {
                url += if (url.contains("?")) "&type=m3u_plus" else "?type=m3u_plus"
            }
            url = url.replace("type=m3u", "type=m3u_plus", ignoreCase = true)
            if (!url.contains("output=", ignoreCase = true)) url += "&output=ts"
        }
        return url
    }

    private fun downloadM3u(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            response.body?.string().orEmpty().replace("\uFEFF", "")
        }
    }

    private fun parseM3u(body: String): List<PreparedItem> {
        if (!body.contains("#EXTINF", ignoreCase = true)) return emptyList()
        val out = mutableListOf<PreparedItem>()
        var currentExt = ""
        body.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> currentExt = line
                line.isNotBlank() && !line.startsWith("#") && currentExt.isNotBlank() -> {
                    val name = currentExt.substringAfterLast(",", "Unknown")
                        .replace("[", "")
                        .replace("]", "")
                        .trim()
                        .ifBlank { "Unknown" }
                    val logo = Regex("tvg-logo=\"([^\"]*)\"").find(currentExt)?.groupValues?.getOrNull(1) ?: ""
                    val group = normalizeCategory(Regex("group-title=\"([^\"]*)\"").find(currentExt)?.groupValues?.getOrNull(1) ?: "Other")
                    val type = detectContentType(line, group, name)
                    out.add(PreparedItem(name, logo, line, group, type))
                    currentExt = ""
                }
            }
        }
        return out
    }

    private fun toJson(items: List<PreparedItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("name", item.name)
                put("logoUrl", item.logoUrl)
                put("streamUrl", item.streamUrl)
                put("category", item.category)
                put("contentType", item.contentType)
            })
        }
        return arr.toString()
    }

    private fun normalizeCategory(raw: String): String {
        var s = raw.trim().replace(Regex("\\s+"), " ").trim()
        if (!s.matches(Regex("\\d+"))) {
            s = s.replace(Regex("^\\d+\\s+"), "").trim()
        }
        return s.ifBlank { "Other" }
    }

    private fun detectContentType(url: String, group: String, name: String): String {
        val lowerUrl = url.lowercase()
        val lowerGroup = group.lowercase()
        val lowerName = name.lowercase()
        return when {
            lowerUrl.contains("/movie/") || lowerUrl.contains("/vod/") || lowerGroup.contains("movie") || lowerGroup.contains("movies") || lowerGroup.contains("vod") || lowerGroup.contains("film") || lowerGroup.contains("films") || lowerGroup.contains("cinema") || lowerGroup.contains("أفلام") || lowerGroup.contains("افلام") -> "movie"
            lowerUrl.contains("/series/") || lowerGroup.contains("series") || lowerGroup.contains("مسلسلات") || lowerGroup.contains("مسلسل") || lowerName.contains("s01") || lowerName.contains("e01") -> "series"
            else -> "live"
        }
    }

    private fun curateForArabicAudience(input: List<PreparedItem>): List<PreparedItem> {
        if (input.isEmpty()) return input
        val curated = input.filter { shouldKeepArabicCurated(it) }.distinctBy { it.streamUrl }
        return if (curated.isNotEmpty()) curated else input
    }

    private fun shouldKeepArabicCurated(item: PreparedItem): Boolean {
        val text = "${item.name} ${item.category}".lowercase()
        val adultBlocked = listOf("xxx", "adult", "porn", "sex", "18+", "for adults", "erotic", "hot").any { text.contains(it) }
        if (adultBlocked) return false

        val hasArToken = Regex("""(^|[\s\-|_\[\]():/])ar($|[\s\-|_\[\]():/])""", RegexOption.IGNORE_CASE).containsMatchIn("${item.name} ${item.category}")
        val arabicSignals = listOf(
            "arab", "arabic", "عربي", "عربية", "العربية", "عرب", "mena", "middle east", "maghreb",
            "dz", "algeria", "algerie", "algérie", "الجزائر", "جزائر", "تونس", "tunisia", "tunisie",
            "مصر", "مصري", "egy", "egypt", "المغرب", "morocco", "maroc", "ليبيا", "libya",
            "سعود", "السعودية", "ksa", "saudi", "قطر", "qatar", "امارات", "الإمارات", "uae",
            "لبنان", "lebanon", "سوريا", "syria", "العراق", "iraq", "فلسطين", "palestine",
            "الأردن", "jordan", "الكويت", "kuwait", "البحرين", "bahrain", "عمان", "oman", "اليمن", "yemen", "رمضان"
        ).any { text.contains(it.lowercase()) }

        val knownArabicBrands = listOf(
            "mbc", "rotana", "روتانا", "osn", "bein", "be in", "بي ان", "بي إن", "ssc", "alkass", "الكاس", "الكأس", "art", "shahid", "شاهد", "aljazeera", "الجزيرة", "alarabiya", "العربية", "dubai", "دبي", "abu dhabi", "abudhabi", "أبوظبي", "ابوظبي", "ksa", "saudi", "السعودية", "qatar", "قطر", "majid", "ماجد", "spacetoon", "سبيستون", "cn arabia", "cartoon network arabic", "quran", "قرآن", "islam", "اسلام", "إسلام", "makkah", "mecca", "مكة", "madinah", "المدينة"
        ).any { text.contains(it.lowercase()) }

        val sportImportant = listOf(
            "sport", "sports", "رياض", "رياضي", "bein", "ssc", "alkass", "الكاس", "الكأس", "ontime", "on time", "ad sport", "abu dhabi sport", "dubai sport", "ksa sport", "قنوات رياضية"
        ).any { text.contains(it.lowercase()) }

        val translatedVod = (item.contentType == "movie" || item.contentType == "series") && listOf(
            "مترجم", "ترجمة", "subbed", "sub", "arsub", "ar sub", "arabic sub", "translated", "vostfr", "vost", "multi sub", "مدبلج", "dubbed", "vfq"
        ).any { text.contains(it.lowercase()) }

        val arabicVod = (item.contentType == "movie" || item.contentType == "series") && listOf(
            "arab", "arabic", "عربي", "عربية", "افلام", "أفلام", "film arab", "movie arab", "مسلسل", "مسلسلات", "series arab", "ramadan", "رمضان", "egy", "egypt", "مصر"
        ).any { text.contains(it.lowercase()) }

        return hasArToken || arabicSignals || knownArabicBrands || sportImportant || arabicVod || translatedVod
    }
}
