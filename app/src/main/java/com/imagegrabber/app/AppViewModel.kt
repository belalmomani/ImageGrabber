package com.imagegrabber.app

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiEvent {
    class Msg(val text: String) : UiEvent
    object PickFolder : UiEvent
    object SaveCsv : UiEvent
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo.get(app)
    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    // ---- البحث ----
    var query by mutableStateOf("")
    var filters by mutableStateOf(repo.settings.loadFilters())
    var showFilters by mutableStateOf(false)
    var results by mutableStateOf<List<ImageItem>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasNext by mutableStateOf(false)
    var total by mutableStateOf(0)
    var remaining by mutableStateOf<Int?>(null)
    var selected by mutableStateOf<Set<String>>(emptySet())
    var detail by mutableStateOf<ImageItem?>(null)
    var pendingDownload by mutableStateOf<List<ImageItem>?>(null)

    private var page = 1
    private var activeQuery = ""
    private var reqId = 0
    val searched: Boolean get() = activeQuery.isNotBlank()

    // ---- الإعدادات ----
    var apiKeyInput by mutableStateOf(repo.settings.userKey)
    var pixabayKeyInput by mutableStateOf(repo.settings.pixabayKey)
    var source by mutableStateOf(repo.settings.source)
    var quality by mutableStateOf(repo.settings.quality)
    var subfolders by mutableStateOf(repo.settings.subfolders)
    var folderName by mutableStateOf<String?>(
        repo.settings.folderUri?.let { DocumentFile.fromTreeUri(app, Uri.parse(it))?.name }
    )

    // ---- بيانات قاعدة البيانات ----
    val tracked = repo.observeTracked()
        .map { l -> l.associateBy { it.imageKey } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val favorites = repo.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloads = repo.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = repo.observeHistory()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { viewModelScope.launch { repo.recoverQueue() } }

    private fun msg(t: String) { events.trySend(UiEvent.Msg(t)) }

    // ---- البحث والصفحات ----
    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        activeQuery = q
        page = 1
        results = emptyList()
        selected = emptySet()
        hasNext = false
        error = null
        total = 0
        viewModelScope.launch { repo.saveHistory(q) }
        load()
    }

    fun applyFilters(f: SearchFilters) {
        filters = f
        repo.settings.saveFilters(f)
        refilter()
    }

    fun searchFrom(q: String) { query = q; search() }
    fun refilter() { if (searched) search() }
    fun loadMore() { if (!loading && hasNext && error == null) load() }
    fun retryLoad() { if (searched) { error = null; load() } }

    private fun load() {
        val myId = ++reqId
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val r = repo.search(activeQuery, page, 30, filters)
                if (myId != reqId) return@launch
                val seen = results.map { it.key }.toHashSet()
                results = results + r.items.filter { it.key !in seen }
                hasNext = r.hasNext
                total = r.total
                if (r.remaining != null) remaining = r.remaining
                page++
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (myId == reqId) error = e.message
            } catch (e: Exception) {
                if (myId == reqId) error = "حدث خطأ غير متوقع"
            } finally {
                if (myId == reqId) loading = false
            }
        }
    }

    // ---- التحديد ----
    fun toggleSelect(key: String) {
        selected = if (key in selected) selected - key else selected + key
    }
    fun selectAll() { selected = results.map { it.key }.toSet() }
    fun clearSelection() { selected = emptySet() }
    fun selectedItems(): List<ImageItem> = results.filter { it.key in selected }

    // ---- المفضلة والروابط ----
    fun toggleFavorite(item: ImageItem) {
        viewModelScope.launch { repo.toggleFavorite(item, activeQuery) }
    }

    fun copyLinks(items: List<ImageItem>) {
        if (items.isEmpty()) return
        val cm = getApplication<Application>().getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("روابط الصور", items.joinToString("\n") { it.originalUrl }))
        msg("تم نسخ ${items.size} رابط")
    }

    // ---- التنزيل ----
    fun requestDownload(items: List<ImageItem>) {
        if (items.isEmpty()) return
        pendingDownload = items
    }

    fun cancelDownloadRequest() { pendingDownload = null }

    fun estimateBytes(items: List<ImageItem>): Long {
        val orig = quality == "ORIGINAL"
        return items.filter { tracked.value[it.key]?.status != Status.DONE }.sumOf {
            if (orig) it.width.toLong() * it.height * 35L / 100L else 450_000L
        }
    }

    fun confirmDownload() {
        val items = pendingDownload ?: return
        if (repo.settings.folderUri == null) {
            events.trySend(UiEvent.PickFolder)
            return
        }
        pendingDownload = null
        viewModelScope.launch {
            val n = repo.queueDownload(items, activeQuery)
            msg(if (n == 0) "الصور محفوظة أو في الطابور مسبقًا" else "أُضيفت $n صورة إلى الطابور")
            selected = emptySet()
        }
    }

    fun onFolderPicked(uri: Uri) {
        val ctx = getApplication<Application>()
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            msg("تعذّر حفظ صلاحية المجلد")
            return
        }
        repo.settings.folderUri = uri.toString()
        folderName = DocumentFile.fromTreeUri(ctx, uri)?.name
        if (pendingDownload != null) confirmDownload()
    }

    fun pickFolder() { events.trySend(UiEvent.PickFolder) }
    fun pauseDownloads() { viewModelScope.launch { repo.pause(); msg("تم الإيقاف المؤقت") } }
    fun resumeDownloads() { viewModelScope.launch { repo.resume() } }
    fun retryFailed() { viewModelScope.launch { repo.retryFailed() } }
    fun retry(key: String) { viewModelScope.launch { repo.retryKey(key) } }

    // ---- الإعدادات ----
    fun saveKey() {
        repo.settings.userKey = apiKeyInput
        repo.settings.pixabayKey = pixabayKeyInput
        msg("تم حفظ المفتاح")
    }
    fun setSourcePref(v: String) {
        source = v
        repo.settings.source = v
        if (searched) search()
    }
    fun setQualityPref(q: String) { quality = q; repo.settings.quality = q }
    fun setSubfoldersPref(v: Boolean) { subfolders = v; repo.settings.subfolders = v }
    fun clearHistory() { viewModelScope.launch { repo.clearHistory() } }

    // ---- تصدير CSV ----
    private var csvText: String? = null

    fun exportItems(items: List<ImageItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch { prepareCsv(repo.ensure(items, activeQuery)) }
    }

    fun exportEntities(list: List<ImageEntity>) = prepareCsv(list)

    private fun prepareCsv(ents: List<ImageEntity>) {
        if (ents.isEmpty()) { msg("لا توجد عناصر للتصدير"); return }
        csvText = Csv.build(ents)
        events.trySend(UiEvent.SaveCsv)
    }

    fun onCsvTarget(uri: Uri?) {
        val text = csvText ?: return
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                }
                msg("تم حفظ ملف CSV")
            } catch (e: Exception) {
                msg("تعذّر حفظ الملف")
            }
        }
    }
}
