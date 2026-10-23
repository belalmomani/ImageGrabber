package com.imagegrabber.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * عميل Supabase الخاص بالتطبيق: تسجيل دخول المشرف + تخزين المفاتيح في جدول api_keys.
 * الحماية الفعلية في الخادم (RLS): لا يقرأ الجدول ولا يكتب فيه إلا حساب المشرف.
 * الرابط والمفتاح العام (anon) ليسا سريّين.
 */
class SupabaseAdmin private constructor(ctx: Context) {
    private val p = ctx.getSharedPreferences("sb", Context.MODE_PRIVATE)
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anon = BuildConfig.SUPABASE_KEY
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun hasSession(): Boolean = p.getString("refresh", "").orEmpty().isNotEmpty()
    fun signOut() { p.edit().clear().apply() }

    private fun call(method: String, path: String, body: String?, token: String?, extra: Map<String, String> = emptyMap()): Pair<Int, String> {
        val b = Request.Builder().url(base + path)
            .header("apikey", anon)
            .header("Content-Type", "application/json")
        if (token != null) b.header("Authorization", "Bearer $token")
        extra.forEach { (k, v) -> b.header(k, v) }
        b.method(method, body?.toRequestBody(jsonType))
        return try {
            Http.client.newCall(b.build()).execute().use { r -> r.code to r.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw ApiException("تعذّر الاتصال بالشبكة. تحقق من الإنترنت.")
        }
    }

    private fun errMsg(body: String): String = try {
        val o = JSONObject(body)
        o.str("error_description").ifEmpty { o.str("msg") }.ifEmpty { o.str("message") }
    } catch (e: Exception) {
        ""
    }

    private fun saveSession(o: JSONObject) {
        p.edit()
            .putString("access", o.str("access_token"))
            .putString("refresh", o.str("refresh_token"))
            .putLong("exp", System.currentTimeMillis() + o.optLong("expires_in", 3600L) * 1000L)
            .apply()
    }

    private fun validToken(): String {
        val acc = p.getString("access", "").orEmpty()
        if (acc.isNotEmpty() && p.getLong("exp", 0L) - 60_000L > System.currentTimeMillis()) return acc
        val ref = p.getString("refresh", "").orEmpty()
        if (ref.isEmpty()) throw ApiException("سجّل الدخول أولًا", 401)
        val (code, body) = call(
            "POST", "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", ref).toString(), null
        )
        if (code !in 200..299) {
            signOut()
            throw ApiException("انتهت الجلسة. سجّل الدخول من جديد", 401)
        }
        saveSession(JSONObject(body))
        return p.getString("access", "").orEmpty()
    }

    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        val (code, body) = call(
            "POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password).toString(), null
        )
        if (code !in 200..299) {
            val m = errMsg(body).lowercase()
            throw ApiException(
                when {
                    "invalid login" in m -> "البريد أو كلمة المرور غير صحيحة"
                    "not confirmed" in m -> "أكّد بريدك أولًا من الرسالة المرسلة إليه"
                    else -> "فشل تسجيل الدخول ($code)"
                }
            )
        }
        saveSession(JSONObject(body))
    }

    suspend fun signUp(email: String, password: String) = withContext(Dispatchers.IO) {
        val (code, body) = call(
            "POST", "/auth/v1/signup",
            JSONObject().put("email", email).put("password", password).toString(), null
        )
        if (code !in 200..299) {
            val m = errMsg(body).lowercase()
            throw ApiException(
                when {
                    "already" in m -> "الحساب موجود مسبقًا؛ سجّل الدخول"
                    "signup" in m && "disabled" in m -> "التسجيل الجديد معطّل في Supabase (وهذا جيد بعد إنشاء حساب المشرف)"
                    "password" in m -> "كلمة المرور ضعيفة أو قصيرة"
                    else -> "فشل إنشاء الحساب ($code)"
                }
            )
        }
    }

    private fun checkRest(code: Int, body: String) {
        when {
            code == 401 || code == 403 -> throw ApiException("غير مصرّح: هذا الحساب ليس حساب المشرف", 401)
            code == 409 -> throw ApiException("المفتاح مضاف مسبقًا")
            code !in 200..299 -> throw ApiException("خطأ من الخادم ($code)")
        }
    }

    private fun toKey(o: JSONObject) = ApiKey(o.str("id"), o.str("label"), o.str("key"), o.optBoolean("enabled", true))

    suspend fun fetchKeys(): Map<String, List<ApiKey>> = withContext(Dispatchers.IO) {
        val (code, body) = call("GET", "/rest/v1/api_keys?select=id,source,label,key,enabled&order=created_at.asc", null, validToken())
        checkRest(code, body)
        val arr = try { JSONArray(body) } catch (e: Exception) { throw ApiException("استجابة غير صالحة من Supabase") }
        val out = HashMap<String, MutableList<ApiKey>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.getOrPut(o.str("source")) { ArrayList() }.add(toKey(o))
        }
        out
    }

    suspend fun fetchSites(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val (code, body) = call("GET", "/rest/v1/source_settings?select=source,enabled", null, validToken())
        checkRest(code, body)
        val arr = try { JSONArray(body) } catch (e: Exception) { throw ApiException("استجابة غير صالحة من Supabase") }
        val out = HashMap<String, Boolean>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out[o.str("source")] = o.optBoolean("enabled", true)
        }
        out
    }

    suspend fun insertKey(source: String, label: String, key: String): ApiKey = withContext(Dispatchers.IO) {
        val payload = JSONArray().put(JSONObject().put("source", source).put("label", label).put("key", key)).toString()
        val (code, body) = call("POST", "/rest/v1/api_keys", payload, validToken(), mapOf("Prefer" to "return=representation"))
        checkRest(code, body)
        val o = try { JSONArray(body).getJSONObject(0) } catch (e: Exception) { throw ApiException("استجابة غير صالحة من Supabase") }
        toKey(o)
    }

    suspend fun setKeyEnabled(id: String, v: Boolean) = withContext(Dispatchers.IO) {
        val (code, body) = call("PATCH", "/rest/v1/api_keys?id=eq.$id", JSONObject().put("enabled", v).toString(), validToken())
        checkRest(code, body)
    }

    suspend fun deleteKey(id: String) = withContext(Dispatchers.IO) {
        val (code, body) = call("DELETE", "/rest/v1/api_keys?id=eq.$id", null, validToken())
        checkRest(code, body)
    }

    suspend fun setSite(source: String, v: Boolean) = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("source", source).put("enabled", v).toString()
        val (code, body) = call(
            "POST", "/rest/v1/source_settings?on_conflict=source", payload, validToken(),
            mapOf("Prefer" to "resolution=merge-duplicates")
        )
        checkRest(code, body)
    }

    companion object {
        @Volatile private var inst: SupabaseAdmin? = null
        fun get(ctx: Context): SupabaseAdmin = inst ?: synchronized(this) {
            inst ?: SupabaseAdmin(ctx.applicationContext).also { inst = it }
        }
    }
}
