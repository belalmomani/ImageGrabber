package com.imagegrabber.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

class FlickrSource(private val pool: KeyPool) : ImageSource {
    override val id = "flickr"

    override suspend fun search(query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult =
        pool.withKey(id) { key -> withContext(Dispatchers.IO) { fetch(key, query, page, perPage, filters) } }

    private fun fetch(key: String, query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult {
        val url = "https://www.flickr.com/services/rest/".toHttpUrl().newBuilder()
            .addQueryParameter("method", "flickr.photos.search")
            .addQueryParameter("api_key", key)
            .addQueryParameter("text", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", minOf(perPage, 100).toString())
            .addQueryParameter("format", "json")
            .addQueryParameter("nojsoncallback", "1")
            .addQueryParameter("media", "photos")
            .addQueryParameter("content_type", "1")
            // فقط تراخيص Creative Commons والملكية العامة (بدون "جميع الحقوق محفوظة")
            .addQueryParameter("license", "1,2,3,4,5,6,7,8,9,10")
            .addQueryParameter("safe_search", "1")
            .addQueryParameter("sort", if (filters.order == "latest") "date-posted-desc" else "relevance")
            .addQueryParameter("extras", "url_m,url_l,url_o,owner_name,license,o_dims")
            .build()
        val res = Http.get(url)
        res.check("Flickr")
        return parse(res.body, res.remaining)
    }

    companion object {
        private val licenses = mapOf(
            "1" to "CC BY-NC-SA 2.0", "2" to "CC BY-NC 2.0", "3" to "CC BY-NC-ND 2.0",
            "4" to "CC BY 2.0", "5" to "CC BY-SA 2.0", "6" to "CC BY-ND 2.0",
            "7" to "لا قيود حقوق معروفة", "8" to "عمل حكومي أمريكي", "9" to "CC0", "10" to "Public Domain Mark"
        )

        fun parse(body: String, remaining: Int?): SearchResult {
            val json = parseJson(body)
            if (json.str("stat") == "fail") {
                val code = json.optInt("code")
                val msg = "خطأ من Flickr: " + json.str("message")
                throw ApiException(msg, if (code == 100) 401 else 0)
            }
            val photos = json.optJSONObject("photos") ?: throw ApiException("استجابة غير متوقعة من المصدر")
            val arr = photos.optJSONArray("photo") ?: throw ApiException("استجابة غير متوقعة من المصدر")
            val items = ArrayList<ImageItem>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val id = p.str("id")
                val owner = p.str("owner")
                val m = p.str("url_m")
                if (id.isEmpty() || !m.startsWith("https://")) continue
                val l = p.str("url_l").ifEmpty { m }
                val o = p.str("url_o").ifEmpty { l }
                val lic = licenses[p.str("license")] ?: "ترخيص غير محدد"
                items.add(
                    ImageItem(
                        key = "flickr:$id", source = "flickr", sourceId = id,
                        thumbUrl = m, largeUrl = l, originalUrl = o,
                        pageUrl = "https://www.flickr.com/photos/$owner/$id",
                        photographer = p.str("ownername"),
                        photographerUrl = "https://www.flickr.com/people/$owner",
                        license = "الترخيص: $lic (راجع شروطه قبل إعادة النشر)",
                        width = p.optInt("width_o", p.optInt("width_l", p.optInt("width_m"))),
                        height = p.optInt("height_o", p.optInt("height_l", p.optInt("height_m"))),
                        alt = p.str("title")
                    )
                )
            }
            val total = photos.opt("total")?.toString()?.toIntOrNull() ?: items.size
            return SearchResult(items, total, photos.optInt("page") < photos.optInt("pages"), remaining)
        }
    }
}
