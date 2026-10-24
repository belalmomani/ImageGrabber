package com.imagegrabber.app

/** فلاتر تُطبَّق على النتائج المحمّلة داخل التطبيق (تعمل مع كل المصادر). */
object ResultFilter {
    private val nc = Regex("(^|[^A-Z])NC([^A-Z]|$)")

    fun isNonCommercial(license: String): Boolean = nc.containsMatchIn(license.uppercase())

    fun orientationOf(w: Int, h: Int): String? = when {
        w <= 0 || h <= 0 -> null
        w > h * 1.05 -> "landscape"
        h > w * 1.05 -> "portrait"
        else -> "square"
    }

    fun apply(
        items: List<ImageItem>, f: SearchFilters, caps: Set<String>, doneKeys: Set<String>
    ): List<ImageItem> {
        var list = items
        val want = f.orientation
        if (want != null && "orientation" in caps) {
            list = list.filter { val o = orientationOf(it.width, it.height); o == null || o == want }
        }
        if (f.minMp > 0) {
            val need = f.minMp * 1_000_000L
            list = list.filter { it.width <= 0 || it.height <= 0 || it.width.toLong() * it.height >= need }
        }
        if (f.minWidth > 0 && "minWidth" in caps) list = list.filter { it.width <= 0 || it.width >= f.minWidth }
        if (f.minHeight > 0 && "minHeight" in caps) list = list.filter { it.height <= 0 || it.height >= f.minHeight }
        if (f.commercialOnly) list = list.filter { !isNonCommercial(it.license) }
        if (f.hideDownloaded) list = list.filter { it.key !in doneKeys }
        val words = f.exclude.split(",", "،").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (words.isNotEmpty()) {
            list = list.filter { item ->
                val hay = (item.alt + " " + item.photographer).lowercase()
                words.none { it in hay }
            }
        }
        return when (f.sortBy) {
            "big" -> list.sortedByDescending { it.width.toLong() * it.height }
            "small" -> list.sortedBy { it.width.toLong() * it.height }
            else -> list
        }
    }
}
