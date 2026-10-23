package com.imagegrabber.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

class UnsplashSource(private val pool: KeyPool) : ImageSource {
    override val id = "unsplash"

    override suspend fun search(query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult =
        pool.withKey(id) { key -> withContext(Dispatchers.IO) { fetch(key, query, page, perPage, filters) } }

    private fun fetch(key: String, query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult {
        val url = "https://api.unsplash.com/search/photos".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", minOf(perPage, 30).toString())
            .addQueryParameter("content_filter", "high")
            .addQueryParameter("order_by", if (filters.order == "latest") "latest" else "relevant")
            .apply {
                when (filters.orientation) {
                    "landscape" -> addQueryParameter("orientation", "landscape")
                    "portrait" -> addQueryParameter("orientation", "portrait")
                    "square" -> addQueryParameter("orientation", "squarish")
                    else -> {}
                }
                val c = when (filters.color) {
                    "turquoise" -> "teal"
                    "violet" -> "purple"
                    "pink" -> "magenta"
                    "gray" -> "black_and_white"
                    "red", "orange", "yellow", "green", "blue", "black", "white" -> filters.color
                    else -> null
                }
                if (c != null) addQueryParameter("color", c)
            }
            .build()
        val res = Http.get(url, mapOf("Authorization" to "Client-ID $key", "Accept-Version" to "v1"))
        res.check("Unsplash")
        return parse(res.body, page, res.remaining)
    }

    companion object {
        private const val UTM = "?utm_source=ImageGrabber&utm_medium=referral"

        fun parse(body: String, page: Int, remaining: Int?): SearchResult {
            val json = parseJson(body)
            val arr = json.optJSONArray("results") ?: throw ApiException("استجابة غير متوقعة من المصدر")
            val items = ArrayList<ImageItem>()
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val id = r.str("id")
                if (id.isEmpty()) continue
                val urls = r.optJSONObject("urls") ?: continue
                val full = urls.str("full")
                if (!full.startsWith("https://")) continue
                val regular = urls.str("regular").ifEmpty { full }
                val small = urls.str("small").ifEmpty { regular }
                val user = r.optJSONObject("user")
                val userHtml = user?.optJSONObject("links")?.str("html").orEmpty()
                val links = r.optJSONObject("links")
                val html = links?.str("html").orEmpty()
                val dl = links?.str("download_location").orEmpty()
                val alt = r.str("alt_description").ifEmpty { r.str("description") }
                items.add(
                    ImageItem(
                        key = "unsplash:$id", source = "unsplash", sourceId = id,
                        thumbUrl = small, largeUrl = regular, originalUrl = full,
                        pageUrl = if (html.isNotEmpty()) html + UTM else "",
                        photographer = user?.str("name").orEmpty(),
                        photographerUrl = if (userHtml.isNotEmpty()) userHtml + UTM else "",
                        license = "ترخيص Unsplash (استخدام مجاني؛ يُشترط ذكر المصوّر والمصدر عند النشر)",
                        width = r.optInt("width"), height = r.optInt("height"), alt = alt,
                        extra = dl.ifEmpty { null }
                    )
                )
            }
            val totalPages = json.optInt("total_pages", 0)
            return SearchResult(items, json.optInt("total", items.size), page < totalPages, remaining)
        }
    }
}
