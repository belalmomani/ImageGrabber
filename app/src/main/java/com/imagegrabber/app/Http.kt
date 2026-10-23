package com.imagegrabber.app

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** code: 401 مفتاح غير صالح، 403/429 حد طلبات (يُجرَّب مفتاح آخر تلقائيًا). */
class ApiException(message: String, val code: Int = 0) : Exception(message)

data class SearchResult(
    val items: List<ImageItem>,
    val total: Int,
    val hasNext: Boolean,
    val remaining: Int?
)

/** واجهة مصدر الصور: لإضافة مصدر جديد يكفي تنفيذ هذه الواجهة وتسجيله في Repo وSourceCatalog. */
interface ImageSource {
    val id: String
    suspend fun search(query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult
}

class HttpResult(val code: Int, val body: String, val remaining: Int?)

object Http {
    const val UA = "ImageGrabber/0.8 (personal Android app)"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun get(url: HttpUrl, headers: Map<String, String> = emptyMap()): HttpResult {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        headers.forEach { (k, v) -> b.header(k, v) }
        return try {
            client.newCall(b.build()).execute().use { r ->
                HttpResult(r.code, r.body?.string().orEmpty(), r.header("X-Ratelimit-Remaining")?.toIntOrNull())
            }
        } catch (e: IOException) {
            throw ApiException("تعذّر الاتصال بالشبكة. تحقق من الإنترنت.")
        }
    }
}

fun HttpResult.check(source: String) {
    when {
        code == 401 -> throw ApiException("مفتاح $source غير صالح", 401)
        code == 403 -> throw ApiException("رُفض الطلب أو تجاوز الحد ($source)", 403)
        code == 429 -> throw ApiException("تم تجاوز حد الطلبات ($source)", 429)
        code !in 200..299 -> throw ApiException("خطأ من $source ($code)", code)
    }
}

fun parseJson(body: String): JSONObject = try {
    JSONObject(body)
} catch (e: JSONException) {
    throw ApiException("استجابة غير صالحة من المصدر")
}

/** قراءة نص آمنة: القيمة null في JSON تعطي "" بدل "null". */
fun JSONObject.str(name: String): String = if (isNull(name)) "" else optString(name)
