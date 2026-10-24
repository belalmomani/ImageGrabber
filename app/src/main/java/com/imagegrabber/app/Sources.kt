package com.imagegrabber.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class SourceInfo(
    val id: String,
    val name: String,
    val needsKey: Boolean,
    val keyUrl: String,
    val keyName: String,
    val note: String,
    val caps: Set<String>
)

object SourceCatalog {
    val all = listOf(
        SourceInfo(
            "pexels", "Pexels", true, "https://www.pexels.com/api/", "API Key",
            "إصدار المفاتيح الجديدة متوقف حاليًا من المصدر. حد نحو 200 طلب/ساعة لكل مفتاح.",
            setOf("orientation", "square", "size", "color")
        ),
        SourceInfo(
            "pixabay", "Pixabay", true, "https://pixabay.com/api/docs/", "API Key",
            "حد نحو 100 طلب/دقيقة لكل مفتاح.",
            setOf("orientation", "color", "type", "vector", "category", "minWidth", "minHeight", "order", "popular", "editors")
        ),
        SourceInfo(
            "unsplash", "Unsplash", true, "https://unsplash.com/oauth/applications", "Access Key",
            "استخدم Access Key وليس Secret. حد منخفض للتطبيقات التجريبية (نحو 50 طلبًا/ساعة)؛ أضف عدة مفاتيح للتناوب.",
            setOf("orientation", "square", "color", "order")
        ),
        SourceInfo(
            "flickr", "Flickr", true, "https://www.flickr.com/services/apps/create/", "API Key",
            "مفتاح غير تجاري مجاني. تُعرض فقط الصور ذات ترخيص Creative Commons أو الملكية العامة.",
            setOf("orientation", "square", "order")
        ),
        SourceInfo(
            "openverse", "Openverse", false, "https://openverse.org/", "",
            "بلا مفتاح (وصول مجهول بحدود تصفح ضيقة). صور مفتوحة الترخيص.",
            setOf("orientation", "square", "size", "type", "artwork")
        ),
        SourceInfo(
            "commons", "Wikimedia Commons", false, "https://commons.wikimedia.org/", "",
            "بلا مفتاح. يدعم البحث بالعربية جيدًا. تراخيص متنوعة تظهر مع كل صورة.",
            setOf("orientation", "square")
        )
    )

    val keyed: List<String> get() = all.filter { it.needsKey }.map { it.id }

    private val allCaps = setOf("orientation", "square", "size", "color", "type", "order", "sources")

    fun byId(id: String): SourceInfo = all.firstOrNull { it.id == id } ?: all.first()
    fun name(id: String): String = all.firstOrNull { it.id == id }?.name ?: id
    fun caps(selected: String): Set<String> = if (selected == "all") allCaps else byId(selected).caps
    fun label(selected: String): String = if (selected == "all") "كل المصادر (تناوب)" else name(selected)
}

/** يجمع نتائج عدة مصادر بالتناوب (واحدة من كل مصدر بالدور). يتحمّل فشل بعض المصادر. */
class CompositeSource(private val list: List<ImageSource>) : ImageSource {
    override val id = "all"

    override suspend fun search(query: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult =
        coroutineScope {
            val per = maxOf(6, perPage / list.size)
            val outcomes = list.map { src ->
                async {
                    try {
                        Pair<SearchResult?, Exception?>(src.search(query, page, per, filters), null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Pair<SearchResult?, Exception?>(null, e)
                    }
                }
            }.awaitAll()
            val ok = outcomes.mapNotNull { it.first }
            if (ok.isEmpty()) {
                val first = outcomes.firstNotNullOfOrNull { it.second }
                throw (first as? ApiException) ?: ApiException("فشلت كل المصادر")
            }
            val merged = ArrayList<ImageItem>()
            val longest = ok.maxOf { it.items.size }
            for (i in 0 until longest) for (r in ok) r.items.getOrNull(i)?.let { merged.add(it) }
            SearchResult(
                merged, ok.sumOf { it.total }, ok.any { it.hasNext },
                ok.mapNotNull { it.remaining }.minOrNull()
            )
        }
}
