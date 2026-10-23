package com.imagegrabber.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLEncoder

class PixabaySource(private val pool: KeyPool) : ImageSource {
    override val id = "pixabay"

    override suspend fun search(query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult =
        pool.withKey(id) { key -> withContext(Dispatchers.IO) { fetch(key, query, page, perPage, filters) } }

    private fun fetch(key: String, query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult {
        val url = "https://pixabay.com/api/".toHttpUrl().newBuilder()
            .addQueryParameter("key", key)
            .addQueryParameter("q", query.take(100))
            .addQueryParameter("safesearch", "true")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", perPage.toString())
            .addQueryParameter("image_type", filters.imageType ?: "all")
            .apply {
                when (filters.orientation) {
                    "landscape" -> addQueryParameter("orientation", "horizontal")
                    "portrait" -> addQueryParameter("orientation", "vertical")
                    else -> {}
                }
                filters.category?.let { addQueryParameter("category", it) }
                filters.color?.let { addQueryParameter("colors", if (it == "violet") "lilac" else it) }
                filters.order?.let { addQueryParameter("order", it) }
                if (filters.editorsChoice) addQueryParameter("editors_choice", "true")
                if (filters.minWidth > 0) addQueryParameter("min_width", filters.minWidth.toString())
            }
            .build()
        val res = Http.get(url)
        if (res.code == 400) throw ApiException("طلب غير صالح أو مفتاح Pixabay غير صحيح", 400)
        res.check("Pixabay")
        return parse(res.body, page, perPage, res.remaining)
    }

    companion object {
        fun parse(body: String, page: Int, perPage: Int, remaining: Int?): SearchResult {
            val json = parseJson(body)
            val hits = json.optJSONArray("hits") ?: throw ApiException("استجابة غير متوقعة من المصدر")
            val items = ArrayList<ImageItem>()
            for (i in 0 until hits.length()) {
                val h = hits.optJSONObject(i) ?: continue
                val id = h.optLong("id", -1L)
                if (id < 0) continue
                val large = h.str("largeImageURL")
                if (!large.startsWith("https://")) continue
                val full = h.str("imageURL")
                val original = if (full.startsWith("https://")) full else large
                val thumb = h.str("webformatURL").ifEmpty { h.str("previewURL") }.ifEmpty { large }
                val user = h.str("user")
                val uid = h.optLong("user_id", 0L)
                val userUrl = if (user.isNotBlank() && uid > 0) {
                    "https://pixabay.com/users/" + URLEncoder.encode(user, "UTF-8") + "-" + uid + "/"
                } else ""
                items.add(
                    ImageItem(
                        key = "pixabay:$id", source = "pixabay", sourceId = id.toString(),
                        thumbUrl = thumb, largeUrl = large, originalUrl = original,
                        pageUrl = h.str("pageURL"), photographer = user, photographerUrl = userUrl,
                        license = "ترخيص Pixabay (استخدام حر دون إسناد؛ يُستحسن ذكر المصوّر والمصدر)",
                        width = h.optInt("imageWidth"), height = h.optInt("imageHeight"), alt = h.str("tags")
                    )
                )
            }
            val totalHits = json.optInt("totalHits", items.size)
            return SearchResult(items, json.optInt("total", totalHits), page * perPage < totalHits, remaining)
        }
    }
}
