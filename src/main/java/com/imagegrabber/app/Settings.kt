package com.imagegrabber.app

import android.content.Context

class SettingsStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** المصدر النشط: all (تناوب) أو معرّف مصدر واحد. */
    var source: String
        get() = p.getString("source", "all") ?: "all"
        set(v) { p.edit().putString("source", v).apply() }

    fun loadFilters() = SearchFilters(
        orientation = p.getString("f_orientation", null),
        size = p.getString("f_size", null),
        color = p.getString("f_color", null),
        imageType = p.getString("f_type", null),
        category = p.getString("f_category", null),
        order = p.getString("f_order", null),
        editorsChoice = p.getBoolean("f_editors", false),
        minWidth = p.getInt("f_minw", 0),
        minHeight = p.getInt("f_minh", 0),
        minMp = p.getInt("f_minmp", 0),
        commercialOnly = p.getBoolean("f_commercial", false),
        hideDownloaded = p.getBoolean("f_hidedone", false),
        exclude = p.getString("f_exclude", "").orEmpty(),
        sortBy = p.getString("f_sort", null),
        onlySources = p.getString("f_sources", "").orEmpty(),
        background = p.getString("f_bg", null),
        style = p.getString("f_style", null),
        format = p.getString("f_format", null),
        dedupe = p.getString("f_dedupe", null),
        dedupeLimit = p.getInt("f_dedupelimit", 120)
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
            .putInt("f_minh", f.minHeight)
            .putInt("f_minmp", f.minMp)
            .putBoolean("f_commercial", f.commercialOnly)
            .putBoolean("f_hidedone", f.hideDownloaded)
            .putString("f_exclude", f.exclude)
            .putString("f_sort", f.sortBy)
            .putString("f_sources", f.onlySources)
            .putString("f_bg", f.background)
            .putString("f_style", f.style)
            .putString("f_format", f.format)
            .putString("f_dedupe", f.dedupe)
            .putInt("f_dedupelimit", f.dedupeLimit)
            .apply()
    }

    var folderUri: String?
        get() = p.getString("folderUri", null)
        set(v) { p.edit().putString("folderUri", v).apply() }

    /** LARGE = حجم كبير (~1880px) ، ORIGINAL = الأصلية. */
    var quality: String
        get() = p.getString("quality", "LARGE") ?: "LARGE"
        set(v) { p.edit().putString("quality", v).apply() }

    /** منع تنزيل صورة موجودة في المكتبة بنسخة أخرى (مصدر/رابط مختلف). */
    var blockDup: Boolean
        get() = p.getBoolean("blockDup", true)
        set(v) { p.edit().putBoolean("blockDup", v).apply() }

    var subfolders: Boolean
        get() = p.getBoolean("subfolders", true)
        set(v) { p.edit().putBoolean("subfolders", v).apply() }
}
