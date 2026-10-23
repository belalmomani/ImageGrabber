package com.imagegrabber.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class ApiKey(val id: String, val label: String, val key: String, val enabled: Boolean = true)

/**
 * مجمّع مفاتيح: عدة مفاتيح لكل موقع. كل طلب يستخدم المفتاح التالي بالتناوب،
 * والمفتاح الذي يبلغ حد الطلبات يدخل فترة راحة ثم يُجرَّب المفتاح الذي بعده.
 */
class KeyPool private constructor(ctx: Context) {
    private val p = ctx.getSharedPreferences("keypool", Context.MODE_PRIVATE)
    private val cooling = ConcurrentHashMap<String, Long>()
    private val pointers = ConcurrentHashMap<String, AtomicInteger>()

    init {
        migrateLegacy(ctx)
    }

    private fun migrateLegacy(ctx: Context) {
        if (p.getBoolean("migrated", false)) return
        val old = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val pex = old.getString("apiKey", "").orEmpty().trim()
        val pix = old.getString("pixabayKey", "").orEmpty().trim()
        if (pex.isNotEmpty()) add("pexels", "مفتاح سابق", pex)
        if (pix.isNotEmpty()) add("pixabay", "مفتاح سابق", pix)
        p.edit().putBoolean("migrated", true).apply()
    }

    fun stored(source: String): List<ApiKey> {
        val arr = try {
            JSONArray(p.getString("keys_$source", "[]"))
        } catch (e: JSONException) {
            JSONArray()
        }
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                ApiKey(o.optString("id"), o.optString("label"), o.optString("key"), o.optBoolean("enabled", true))
            }
        }
    }

    private fun save(source: String, list: List<ApiKey>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("label", it.label).put("key", it.key).put("enabled", it.enabled))
        }
        p.edit().putString("keys_$source", arr.toString()).apply()
    }

    private fun builtin(source: String): ApiKey? {
        val k = when (source) {
            "pexels" -> BuildConfig.PEXELS_API_KEY
            "pixabay" -> BuildConfig.PIXABAY_API_KEY
            else -> ""
        }
        return if (k.isBlank()) null else ApiKey("builtin_$source", "مفتاح مدمج (من البناء)", k, true)
    }

    fun allKeys(source: String): List<ApiKey> = listOfNotNull(builtin(source)) + stored(source)

    fun hasUsable(source: String): Boolean = allKeys(source).any { it.enabled }
    fun firstKey(source: String): String? = allKeys(source).firstOrNull { it.enabled }?.key

    /** يعيد false إذا كان المفتاح فارغًا أو مكررًا. */
    fun add(source: String, label: String, key: String): Boolean {
        val k = key.trim()
        if (k.isEmpty()) return false
        val cur = stored(source)
        if (cur.any { it.key == k }) return false
        val name = label.trim().ifEmpty { "حساب ${cur.size + 1}" }
        save(source, cur + ApiKey(UUID.randomUUID().toString(), name, k, true))
        return true
    }

    fun remove(source: String, id: String) {
        save(source, stored(source).filter { it.id != id })
        cooling.remove(id)
    }

    fun setEnabled(source: String, id: String, enabled: Boolean) {
        save(source, stored(source).map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    /** مزامنة محلية مع Supabase: استبدال المفاتيح المخزَّنة بما في الخادم. */
    fun replaceStored(source: String, list: List<ApiKey>) = save(source, list)

    fun putLocal(source: String, k: ApiKey) {
        val cur = stored(source).filter { it.id != k.id }
        save(source, cur + k)
    }

    fun siteEnabled(source: String): Boolean = p.getBoolean("site_$source", true)
    fun setSiteEnabled(source: String, v: Boolean) { p.edit().putBoolean("site_$source", v).apply() }

    fun coolingUntil(id: String): Long = cooling[id] ?: 0L

    private fun cooldownMs(source: String): Long = if (source == "pixabay") 70_000L else 20 * 60_000L

    suspend fun <T> withKey(source: String, block: suspend (String) -> T): T {
        val keys = allKeys(source).filter { it.enabled }
        if (keys.isEmpty()) {
            throw ApiException("لا يوجد مفتاح مفعّل لمصدر ${SourceCatalog.name(source)}. أضفه من لوحة التحكم.")
        }
        val now = System.currentTimeMillis()
        val start = pointers.getOrPut(source) { AtomicInteger(0) }.getAndIncrement()
        var last: ApiException? = null
        for (i in keys.indices) {
            val k = keys[Math.floorMod(start + i, keys.size)]
            if ((cooling[k.id] ?: 0L) > now) continue
            try {
                return block(k.key)
            } catch (e: ApiException) {
                when (e.code) {
                    401 -> cooling[k.id] = now + 24 * 3_600_000L
                    403, 429 -> cooling[k.id] = now + cooldownMs(source)
                    else -> throw e
                }
                last = e
            }
        }
        throw last ?: ApiException(
            "كل مفاتيح ${SourceCatalog.name(source)} في فترة راحة بسبب حد الطلبات. حاول لاحقًا أو أضف مفتاحًا آخر."
        )
    }

    companion object {
        @Volatile private var inst: KeyPool? = null
        fun get(ctx: Context): KeyPool = inst ?: synchronized(this) {
            inst ?: KeyPool(ctx.applicationContext).also { inst = it }
        }
    }
}
