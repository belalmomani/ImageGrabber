package com.imagegrabber.app

import android.content.Context

class SettingsStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** مفتاح أدخله المستخدم داخل التطبيق (لا يُرفع إلى أي مستودع). */
    var userKey: String
        get() = p.getString("apiKey", "").orEmpty()
        set(v) { p.edit().putString("apiKey", v.trim()).apply() }

    /** المفتاح الفعّال: ما أدخله المستخدم، وإلا ما أُدمج وقت البناء. */
    val apiKey: String
        get() = userKey.ifBlank { BuildConfig.PEXELS_API_KEY }

    var pixabayKey: String
        get() = p.getString("pixabayKey", "").orEmpty()
        set(v) { p.edit().putString("pixabayKey", v.trim()).apply() }

    val pixabayEffective: String
        get() = pixabayKey.ifBlank { BuildConfig.PIXABAY_API_KEY }

    /** المصدر النشط: pixabay أو pexels. */
    var source: String
        get() = p.getString("source", "pixabay") ?: "pixabay"
        set(v) { p.edit().putString("source", v).apply() }

    var folderUri: String?
        get() = p.getString("folderUri", null)
        set(v) { p.edit().putString("folderUri", v).apply() }

    /** LARGE = حجم كبير (~1880px) ، ORIGINAL = الأصلية. */
    var quality: String
        get() = p.getString("quality", "LARGE") ?: "LARGE"
        set(v) { p.edit().putString("quality", v).apply() }

    var subfolders: Boolean
        get() = p.getBoolean("subfolders", true)
        set(v) { p.edit().putBoolean("subfolders", v).apply() }
}
