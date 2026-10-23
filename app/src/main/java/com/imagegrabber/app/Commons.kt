package com.imagegrabber.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

/** ويكيميديا كومنز: بلا مفتاح، ويدعم البحث بالعربية. */
class CommonsSource : ImageSource {
    override val id = "commons"

    override suspend fun search(query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult =
        withContext(Dispatchers.IO) { fetch(query, page, perPage) }

    private fun fetch(query: String, page: Int, perPage: Int): SearchResult {
        val limit = minOf(perPage, 50)
        val url = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrnamespace", "6")
            .addQueryParameter("gsrsearch", "$query filetype:bitmap")
            .addQueryParameter("gsrlimit", limit.toString())
            .addQueryParameter("gsroffset", ((page - 1) * limit).toString())
            .addQueryParameter("gsrinfo", "totalhits")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|size|mime|extmetadata")
            .addQueryParameter("iiurlwidth", "1600")
            .addQueryParameter("iiextmetadatafilter", "LicenseShortName|Artist")
            .build()
        val res = Http.get(url)
        res.check("Wikimedia Commons")
        return parse(res.body, res.remaining)
    }

    companion object {
        private val okMime = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

        fun stripHtml(s: String): String =
            s.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#039;", "'").trim()

        fun smallThumb(u: String): String =
            if (u.contains("/thumb/")) u.replace(Regex("/\\d+px-"), "/480px-") else u

        fun parse(body: String, remaining: Int?): SearchResult {
            val json = parseJson(body)
            val query = json.optJSONObject("query")
            val pages = query?.optJSONArray("pages")
            val list = ArrayList<org.json.JSONObject>()
            if (pages != null) for (i in 0 until pages.length()) pages.optJSONObject(i)?.let { list.add(it) }
            list.sortBy { it.optInt("index", Int.MAX_VALUE) }
            val items = ArrayList<ImageItem>()
            for (p in list) {
                val pageId = p.optLong("pageid", -1L)
                val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val url = info.str("url")
                if (pageId < 0 || !url.startsWith("https://")) continue
                if (info.str("mime") !in okMime) continue
                val thumbFull = info.str("thumburl").let { if (it.startsWith("https://")) it else url }
                val meta = info.optJSONObject("extmetadata")
                val lic = stripHtml(meta?.optJSONObject("LicenseShortName")?.str("value").orEmpty())
                val artist = stripHtml(meta?.optJSONObject("Artist")?.str("value").orEmpty())
                val title = p.str("title").removePrefix("File:").substringBeforeLast('.')
                items.add(
                    ImageItem(
                        key = "commons:$pageId", source = "commons", sourceId = pageId.toString(),
                        thumbUrl = smallThumb(thumbFull), largeUrl = thumbFull, originalUrl = url,
                        pageUrl = info.str("descriptionurl"),
                        photographer = artist.ifEmpty { "غير معروف" }, photographerUrl = "",
                        license = "الترخيص: " + lic.ifEmpty { "غير محدد" } + " (راجع صفحة الملف للشروط)",
                        width = info.optInt("width"), height = info.optInt("height"), alt = title
                    )
                )
            }
            val total = query?.optJSONObject("searchinfo")?.optInt("totalhits", items.size) ?: items.size
            return SearchResult(items, total, json.has("continue"), remaining)
        }
    }
}
