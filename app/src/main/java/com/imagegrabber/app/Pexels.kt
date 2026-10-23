package com.imagegrabber.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

class PexelsSource(private val pool: KeyPool) : ImageSource {
    override val id = "pexels"

    override suspend fun search(query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult =
        pool.withKey(id) { key -> withContext(Dispatchers.IO) { fetch(key, query, page, perPage, filters) } }

    private fun fetch(key: String, query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult {
        val url = "https://api.pexels.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", perPage.toString())
            .apply {
                filters.orientation?.let { addQueryParameter("orientation", it) }
                filters.size?.let { addQueryParameter("size", it) }
                filters.color?.let { addQueryParameter("color", it) }
            }
            .build()
        val res = Http.get(url, mapOf("Authorization" to key))
        res.check("Pexels")
        return parse(res.body, res.remaining)
    }

    companion object {
        fun parse(body: String, remaining: Int?): SearchResult {
            val json = parseJson(body)
            val arr = json.optJSONArray("photos") ?: throw ApiException("استجابة غير متوقعة من المصدر")
            val items = ArrayList<ImageItem>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val src = p.optJSONObject("src") ?: continue
                val id = p.optLong("id", -1L)
                if (id < 0) continue
                val original = src.str("original")
                if (!original.startsWith("https://")) continue
                val medium = src.str("medium").ifEmpty { original }
                val large = src.str("large2x").ifEmpty { src.str("large").ifEmpty { original } }
                items.add(
                    ImageItem(
                        key = "pexels:$id", source = "pexels", sourceId = id.toString(),
                        thumbUrl = medium, largeUrl = large, originalUrl = original,
                        pageUrl = p.str("url"), photographer = p.str("photographer"),
                        photographerUrl = p.str("photographer_url"),
                        license = "ترخيص Pexels (استخدام مجاني؛ يُستحسن ذكر المصوّر والمصدر)",
                        width = p.optInt("width"), height = p.optInt("height"), alt = p.str("alt")
                    )
                )
            }
            val hasNext = json.has("next_page") && !json.isNull("next_page")
            return SearchResult(items, json.optInt("total_results", items.size), hasNext, remaining)
        }
    }
}
