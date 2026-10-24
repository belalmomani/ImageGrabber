package com.imagegrabber.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/** عنصر صورة كما يأتي من أي مصدر (طبقة مستقلة عن Pexels). */
data class ImageItem(
    val key: String,
    val source: String,
    val sourceId: String,
    val thumbUrl: String,
    val largeUrl: String,
    val originalUrl: String,
    val pageUrl: String,
    val photographer: String,
    val photographerUrl: String,
    val license: String,
    val width: Int,
    val height: Int,
    val alt: String,
    val extra: String? = null
)

object Status {
    const val NONE = "NONE"
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
    const val PAUSED = "PAUSED"

    fun label(s: String): String = when (s) {
        QUEUED -> "في الانتظار"
        DOWNLOADING -> "جارٍ التنزيل"
        DONE -> "تم الحفظ"
        FAILED -> "فشل"
        PAUSED -> "متوقف مؤقتًا"
        else -> "لم يُنزَّل"
    }
}

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val imageKey: String,
    val source: String,
    val sourceId: String,
    val searchQuery: String,
    val thumbUrl: String,
    val largeUrl: String,
    val originalUrl: String,
    val pageUrl: String,
    val photographer: String,
    val photographerUrl: String,
    val license: String,
    val width: Int,
    val height: Int,
    val alt: String,
    val favorite: Boolean = false,
    val status: String = Status.NONE,
    val localUri: String? = null,
    val sizeBytes: Long = 0,
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val extra: String? = null
)

@Entity(tableName = "history")
data class SearchHistory(
    @PrimaryKey val text: String,
    val time: Long
)

fun ImageItem.toEntity(query: String) = ImageEntity(
    imageKey = key, source = source, sourceId = sourceId, searchQuery = query,
    thumbUrl = thumbUrl, largeUrl = largeUrl, originalUrl = originalUrl, pageUrl = pageUrl,
    photographer = photographer, photographerUrl = photographerUrl, license = license,
    width = width, height = height, alt = alt, extra = extra
)

fun ImageEntity.toItem() = ImageItem(
    key = imageKey, source = source, sourceId = sourceId, thumbUrl = thumbUrl,
    largeUrl = largeUrl, originalUrl = originalUrl, pageUrl = pageUrl,
    photographer = photographer, photographerUrl = photographerUrl, license = license,
    width = width, height = height, alt = alt, extra = extra
)

/** فلاتر البحث. كل مصدر يستخدم ما يدعمه فقط، وبعضها يُطبَّق داخل التطبيق على كل المصادر. */
data class SearchFilters(
    val orientation: String? = null,    // landscape / portrait / square
    val size: String? = null,           // Pexels / Openverse
    val color: String? = null,
    val imageType: String? = null,      // photo / illustration / vector
    val category: String? = null,       // Pixabay
    val order: String? = null,          // latest / popular
    val editorsChoice: Boolean = false, // Pixabay
    val minWidth: Int = 0,              // Pixabay
    val minHeight: Int = 0,             // Pixabay
    val minMp: Int = 0,                 // ميغابكسل (داخل التطبيق)
    val commercialOnly: Boolean = false,
    val hideDownloaded: Boolean = false,
    val exclude: String = "",           // كلمات مستبعدة مفصولة بفاصلة
    val sortBy: String? = null,         // big / small (ترتيب العرض)
    val onlySources: String = "",       // في وضع «كل المصادر»: معرّفات مفصولة بفاصلة (فارغ = الكل)
    val background: String? = null,     // يُضاف ككلمات للبحث (وPixabay يدعم الشفافية فعليًا)
    val style: String? = null,          // أسلوب/نوع إضافي كلمات للبحث
    val format: String? = null          // jpg / png / webp / gif
) {
    fun activeCount(caps: Set<String>): Int {
        var n = 0
        if ("orientation" in caps && orientation != null) n++
        if ("size" in caps && size != null) n++
        if ("color" in caps && color != null) n++
        if ("type" in caps && imageType != null) n++
        if ("category" in caps && category != null) n++
        if ("order" in caps && order != null) n++
        if ("editors" in caps && editorsChoice) n++
        if ("minWidth" in caps && minWidth > 0) n++
        if ("minHeight" in caps && minHeight > 0) n++
        if ("sources" in caps && onlySources.isNotBlank()) n++
        if (minMp > 0) n++
        if (commercialOnly) n++
        if (hideDownloaded) n++
        if (exclude.isNotBlank()) n++
        if (sortBy != null) n++
        if (background != null) n++
        if (style != null) n++
        if (format != null) n++
        return n
    }
}
