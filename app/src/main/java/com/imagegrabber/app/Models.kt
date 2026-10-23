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

/** فلاتر البحث. كل مصدر يستخدم ما يدعمه فقط. */
data class SearchFilters(
    val orientation: String? = null,   // landscape / portrait / square
    val size: String? = null,          // Pexels: large / medium / small
    val color: String? = null,
    val imageType: String? = null,     // Pixabay: photo / illustration / vector
    val category: String? = null,      // Pixabay
    val order: String? = null,         // Pixabay: popular / latest
    val editorsChoice: Boolean = false,// Pixabay
    val minWidth: Int = 0              // Pixabay
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
        return n
    }
}
