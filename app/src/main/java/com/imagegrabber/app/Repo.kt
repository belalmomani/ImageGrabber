package com.imagegrabber.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow

class Repo private constructor(private val ctx: Context) {
    val settings = SettingsStore(ctx)
    private val db = AppDatabase.get(ctx)
    private val dao = db.images()
    private val hist = db.history()
    val pool = KeyPool.get(ctx)
    val sb = SupabaseAdmin.get(ctx)
    private val sources: Map<String, ImageSource> = listOf(
        PexelsSource(pool), PixabaySource(pool), UnsplashSource(pool),
        FlickrSource(pool), OpenverseSource(), CommonsSource()
    ).associateBy { it.id }

    suspend fun search(q: String, page: Int, perPage: Int, filters: SearchFilters): SearchResult {
        val sel = settings.source
        if (sel == "all") {
            val only = filters.onlySources.split(",").filter { it.isNotBlank() }.toSet()
            val active = sources.values.filter {
                (only.isEmpty() || it.id in only) &&
                    pool.siteEnabled(it.id) && (!SourceCatalog.byId(it.id).needsKey || pool.hasUsable(it.id))
            }
            if (active.isEmpty()) throw ApiException("لا توجد مصادر مفعّلة. أضف مفاتيح من لوحة التحكم.")
            return CompositeSource(active).search(q, page, perPage, filters)
        }
        return (sources[sel] ?: sources.getValue("commons")).search(q, page, perPage, filters)
    }

    /** سحب المفاتيح وإعدادات المواقع من Supabase إلى الجهاز (ورفع المحلية إن كان الخادم فارغًا). */
    suspend fun syncFromRemote() {
        var remote = sb.fetchKeys()
        var pushed = false
        for (src in SourceCatalog.keyed) {
            if (remote[src].isNullOrEmpty()) {
                for (k in pool.stored(src)) {
                    try {
                        sb.insertKey(src, k.label, k.key)
                        pushed = true
                    } catch (e: ApiException) {
                    }
                }
            }
        }
        if (pushed) remote = sb.fetchKeys()
        for (src in SourceCatalog.keyed) pool.replaceStored(src, remote[src].orEmpty())
        sb.fetchSites().forEach { (s, v) -> pool.setSiteEnabled(s, v) }
    }

    fun observeTracked(): Flow<List<ImageEntity>> = dao.observeTracked()
    fun observeFavorites(): Flow<List<ImageEntity>> = dao.observeFavorites()
    fun observeDownloads(): Flow<List<ImageEntity>> = dao.observeDownloads()
    fun observeHistory(): Flow<List<SearchHistory>> = hist.observe()

    suspend fun saveHistory(q: String) = hist.upsert(SearchHistory(q, System.currentTimeMillis()))
    suspend fun clearHistory() = hist.clear()

    /** يضمن وجود العناصر في قاعدة البيانات ويعيدها بحالتها الحالية. */
    suspend fun ensure(items: List<ImageItem>, query: String): List<ImageEntity> {
        if (items.isEmpty()) return emptyList()
        dao.insertIgnore(items.map { it.toEntity(query) })
        val map = items.map { it.key }.chunked(500).flatMap { dao.getByKeys(it) }.associateBy { it.imageKey }
        return items.mapNotNull { map[it.key] }
    }

    suspend fun toggleFavorite(item: ImageItem, query: String) {
        val e = ensure(listOf(item), query).firstOrNull() ?: return
        dao.setFavorite(e.imageKey, !e.favorite, System.currentTimeMillis())
    }

    /** يضيف إلى الطابور ما لم يُحفظ سابقًا أو يكن في الطابور. يعيد عدد المضاف. */
    suspend fun queueDownload(items: List<ImageItem>, query: String): Int {
        val ents = ensure(items, query)
        val keys = ents.filter {
            it.status == Status.NONE || it.status == Status.FAILED || it.status == Status.PAUSED
        }.map { it.imageKey }
        if (keys.isEmpty()) return 0
        val now = System.currentTimeMillis()
        keys.chunked(500).forEach { dao.queueKeys(it, now) }
        enqueueWorker()
        return keys.size
    }

    /** عند فتح التطبيق: استئناف أي طابور لم يكتمل (مثلًا بعد إغلاق النظام للتطبيق). */
    suspend fun recoverQueue() { if (dao.countActive() > 0) enqueueWorker() }

    suspend fun retryKey(key: String) {
        dao.queueKeys(listOf(key), System.currentTimeMillis())
        enqueueWorker()
    }

    suspend fun pause() {
        dao.pauseAll(System.currentTimeMillis())
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
    }

    suspend fun resume() {
        dao.moveAll(Status.PAUSED, Status.QUEUED, System.currentTimeMillis())
        enqueueWorker()
    }

    suspend fun retryFailed() {
        dao.moveAll(Status.FAILED, Status.QUEUED, System.currentTimeMillis())
        enqueueWorker()
    }

    private fun enqueueWorker() {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    companion object {
        private const val WORK_NAME = "download_queue"
        @Volatile private var inst: Repo? = null
        fun get(ctx: Context): Repo = inst ?: synchronized(this) {
            inst ?: Repo(ctx.applicationContext).also { inst = it }
        }
    }
}
