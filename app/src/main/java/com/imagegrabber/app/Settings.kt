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

    fun loadFilters() = SearchFilters(
        orientation = p.getString("f_orientation", null),
        size = p.getString("f_size", null),
        color = p.getString("f_color", null),
        imageType = p.getString("f_type", null),
        category = p.getString("f_category", null),
        order = p.getString("f_order", null),
        editorsChoice = p.getBoolean("f_editors", false),
        minWidth = p.getInt("f_minw", 0)
    )

    fun saveFilters(f: SearchFilters) {
        p.edit()
            .putString("f_orientation", f.orientation)
            .putString("f_size", f.size)
            .putString("f_color", f.color)
            .putString("f_type", f.imageType)
            .putString("f_category", f.category)
            .putString("f_order", f.order)
            .putBoolean("f_editors", f.editorsChoice)
            .putInt("f_minw", f.minWidth)
            .apply()
    }

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
