package com.imagegrabber.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

/** بلا مفتاح (وصول مجهول): حدود تصفح ضيقة. */
class OpenverseSource : ImageSource {
    override val id = "openverse"

    override suspend fun search(query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult =
        withContext(Dispatchers.IO) { fetch(query, page, perPage, filters) }

    private fun fetch(query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult {
        val url = "https://api.openverse.org/v1/images/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", minOf(perPage, 20).toString())
            .addQueryParameter("mature", "false")
            .apply {
                when (filters.orientation) {
                    "landscape" -> addQueryParameter("aspect_ratio", "wide")
                    "portrait" -> addQueryParameter("aspect_ratio", "tall")
                    "square" -> addQueryParameter("aspect_ratio", "square")
                    else -> {}
                }
                filters.size?.let { addQueryParameter("size", it) }
                filters.format?.let { addQueryParameter("extension", it) }
                if (filters.commercialOnly) addQueryParameter("license_type", "commercial")
                when (filters.imageType) {
                    "photo" -> addQueryParameter("category", "photograph")
                    "illustration" -> addQueryParameter("category", "illustration")
                    "artwork" -> addQueryParameter("category", "digitized_artwork")
                    else -> {}
                }
            }
            .build()
        val res = Http.get(url)
        if (res.code == 401 || res.code == 403) throw ApiException("انتهى حد التصفح المجهول في Openverse")
        res.check("Openverse")
        return parse(res.body, page, res.remaining)
    }

    companion object {
        fun parse(body: String, page: Int, remaining: Int?): SearchResult {
            val json = parseJson(body)
            val arr = json.optJSONArray("results") ?: throw ApiException("استجابة غير متوقعة من المصدر")
            val items = ArrayList<ImageItem>()
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val id = r.str("id")
                val url = r.str("url")
                if (id.isEmpty() || !url.startsWith("https://")) continue
                val thumb = r.str("thumbnail").let { if (it.startsWith("https://")) it else url }
                val lic = r.str("license")
                val ver = r.str("license_version")
                val licText = when (lic) {
                    "cc0" -> "CC0 1.0"
                    "pdm" -> "Public Domain Mark"
                    else -> "CC ${lic.uppercase()} $ver".trim()
                }
                val licUrl = r.str("license_url")
                items.add(
                    ImageItem(
                        key = "openverse:$id", source = "openverse", sourceId = id,
                        thumbUrl = thumb, largeUrl = url, originalUrl = url,
                        pageUrl = r.str("foreign_landing_url"),
                        photographer = r.str("creator").ifEmpty { "غير معروف" },
                        photographerUrl = r.str("creator_url"),
                        license = "الترخيص: $licText" + (if (licUrl.isNotEmpty()) " ($licUrl)" else "") +
                            " — المصدر: " + r.str("source"),
                        width = r.optInt("width"), height = r.optInt("height"), alt = r.str("title")
                    )
                )
            }
            return SearchResult(
                items, json.optInt("result_count", items.size),
                page < json.optInt("page_count", 0), remaining
            )
        }
    }
}
