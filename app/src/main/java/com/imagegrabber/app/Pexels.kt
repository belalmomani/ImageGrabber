package com.imagegrabber.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

class ApiException(message: String, val code: Int = 0) : Exception(message)

data class SearchResult(
    val items: List<ImageItem>,
    val total: Int,
    val hasNext: Boolean,
    val remaining: Int?
)

/** واجهة مصدر الصور: لإضافة مصدر جديد يكفي تنفيذ هذه الواجهة. */
interface ImageSource {
    val id: String
    suspend fun search(
        query: String, page: Int, perPage: Int, orientation: String?, size: String?
    ): SearchResult
}

class PexelsSource(private val keyProvider: () -> String) : ImageSource {
    override val id = "pexels"

    override suspend fun search(
        query: String, page: Int, perPage: Int, orientation: String?, size: String?
    ): SearchResult = withContext(Dispatchers.IO) { fetch(query, page, perPage, orientation, size) }

    private fun fetch(query: String, page: Int, perPage: Int, orientation: String?, size: String?): SearchResult {
        val key = keyProvider().trim()
        if (key.isEmpty()) throw ApiException("لم يُضبط مفتاح Pexels. أدخله من تبويب الإعدادات.")
        val url = "https://api.pexels.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", perPage.toString())
            .apply {
                if (orientation != null) addQueryParameter("orientation", orientation)
                if (size != null) addQueryParameter("size", size)
            }
            .build()
        val req = Request.Builder().url(url).header("Authorization", key).build()
        return try {
            Http.client.newCall(req).execute().use { resp ->
                when {
                    resp.code == 401 || resp.code == 403 ->
                        throw ApiException("مفتاح API غير صالح", resp.code)
                    resp.code == 429 ->
                        throw ApiException("تم تجاوز حد الطلبات. حاول لاحقًا.", 429)
                    !resp.isSuccessful ->
                        throw ApiException("خطأ من الخادم (${resp.code})", resp.code)
                }
                val remaining = resp.header("X-Ratelimit-Remaining")?.toIntOrNull()
                val body = resp.body?.string() ?: throw ApiException("استجابة فارغة من المصدر")
                parse(body, remaining)
            }
        } catch (e: IOException) {
            throw ApiException("تعذّر الاتصال بالشبكة. تحقق من الإنترنت.")
        }
    }

    internal fun parse(body: String, remaining: Int?): SearchResult {
        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw ApiException("استجابة غير صالحة من المصدر")
        }
        val arr = json.optJSONArray("photos") ?: throw ApiException("استجابة غير متوقعة من المصدر")
        val items = ArrayList<ImageItem>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val src = p.optJSONObject("src") ?: continue
            val id = p.optLong("id", -1L)
            if (id < 0) continue
            val original = src.optString("original")
            if (!original.startsWith("https://")) continue
            val medium = src.optString("medium").ifEmpty { original }
            val large = src.optString("large2x").ifEmpty { src.optString("large").ifEmpty { original } }
            items.add(
                ImageItem(
                    key = "pexels:$id",
                    source = "pexels",
                    sourceId = id.toString(),
                    thumbUrl = medium,
                    largeUrl = large,
                    originalUrl = original,
                    pageUrl = p.optString("url"),
                    photographer = p.optString("photographer"),
                    photographerUrl = p.optString("photographer_url"),
                    license = "ترخيص Pexels (استخدام مجاني؛ يُستحسن ذكر المصوّر والمصدر)",
                    width = p.optInt("width"),
                    height = p.optInt("height"),
                    alt = p.optString("alt")
                )
            )
        }
        val hasNext = json.has("next_page") && !json.isNull("next_page")
        return SearchResult(items, json.optInt("total_results", items.size), hasNext, remaining)
    }
}
