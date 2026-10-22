package com.imagegrabber.app

object Csv {
    private val header = listOf(
        "اسم البحث", "معرف الصورة", "الرابط الأصلي", "رابط صفحة المصدر",
        "اسم المصور", "رابط المصور", "الترخيص", "حالة التنزيل"
    )

    private fun esc(s: String): String {
        val safe = if (s.isNotEmpty() && s[0] in "=+-@") "'$s" else s
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    fun build(rows: List<ImageEntity>): String {
        val sb = StringBuilder("\uFEFF") // BOM ليفتح Excel العربية بشكل صحيح
        sb.append(header.joinToString(",") { esc(it) }).append("\r\n")
        for (r in rows) {
            sb.append(
                listOf(
                    r.searchQuery, r.sourceId, r.originalUrl, r.pageUrl,
                    r.photographer, r.photographerUrl, r.license, Status.label(r.status)
                ).joinToString(",") { esc(it) }
            ).append("\r\n")
        }
        return sb.toString()
    }
}
