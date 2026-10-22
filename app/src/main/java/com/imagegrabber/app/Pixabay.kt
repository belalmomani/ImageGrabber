package com.imagegrabber.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class PixabaySource(private val keyProvider: () -> String) : ImageSource {
    override val id = "pixabay"

    override suspend fun search(
        query: String, page: Int, perPage: Int, filters: SearchFilters
    ): SearchResult = withContext(Dispatchers.IO) { fetch(query, page, perPage, filters) }

    private fun fetch(query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult {
        val key = keyProvider().trim()
        if (key.isEmpty()) throw ApiException("لم يُضبط مفتاح Pixabay. أدخله من تبويب الإعدادات.")
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
        val req = Request.Builder().url(url).build()
        return try {
            Http.client.newCall(req).execute().use { resp ->
                when {
                    resp.code == 401 || resp.code == 403 ->
                        throw ApiException("مفتاح API غير صالح", resp.code)
                    resp.code == 429 ->
                        throw ApiException("تم تجاوز حد الطلبات. حاول بعد دقيقة.", 429)
                    resp.code == 400 ->
                        throw ApiException("طلب غير صالح أو مفتاح غير صحيح", 400)
                    !resp.isSuccessful ->
                        throw ApiException("خطأ من الخادم (${resp.code})", resp.code)
                }
                val remaining = resp.header("X-RateLimit-Remaining")?.toIntOrNull()
                val body = resp.body?.string() ?: throw ApiException("استجابة فارغة من المصدر")
                parse(body, page, perPage, remaining)
            }
        } catch (e: IOException) {
            throw ApiException("تعذّر الاتصال بالشبكة. تحقق من الإنترنت.")
        }
    }

    internal fun parse(body: String, page: Int, perPage: Int, remaining: Int?): SearchResult {
        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw ApiException("استجابة غير صالحة من المصدر")
        }
        val hits = json.optJSONArray("hits") ?: throw ApiException("استجابة غير متوقعة من المصدر")
        val items = ArrayList<ImageItem>()
        for (i in 0 until hits.length()) {
            val h = hits.optJSONObject(i) ?: continue
            val id = h.optLong("id", -1L)
            if (id < 0) continue
            val large = h.optString("largeImageURL")
            if (!large.startsWith("https://")) continue
            val full = h.optString("imageURL")
            val original = if (full.startsWith("https://")) full else large
            val thumb = h.optString("webformatURL").ifEmpty { h.optString("previewURL") }.ifEmpty { large }
            val user = h.optString("user")
            val uid = h.optLong("user_id", 0L)
            val userUrl = if (user.isNotBlank() && uid > 0) {
                "https://pixabay.com/users/" + URLEncoder.encode(user, "UTF-8") + "-" + uid + "/"
            } else ""
            items.add(
                ImageItem(
                    key = "pixabay:$id",
                    source = "pixabay",
                    sourceId = id.toString(),
                    thumbUrl = thumb,
                    largeUrl = large,
                    originalUrl = original,
                    pageUrl = h.optString("pageURL"),
                    photographer = user,
                    photographerUrl = userUrl,
                    license = "ترخيص Pixabay (استخدام حر دون إسناد؛ يُستحسن ذكر المصوّر والمصدر)",
                    width = h.optInt("imageWidth"),
                    height = h.optInt("imageHeight"),
                    alt = h.optString("tags")
                )
            )
        }
        val totalHits = json.optInt("totalHits", items.size)
        return SearchResult(items, json.optInt("total", totalHits), page * perPage < totalHits, remaining)
    }
}
