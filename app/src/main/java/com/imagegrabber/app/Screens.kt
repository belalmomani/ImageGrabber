@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.imagegrabber.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.filter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun openUrl(ctx: Context, url: String) {
    if (!url.startsWith("http")) return
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
    }
}

fun fmtBytes(b: Long): String =
    if (b >= 1_048_576L) "%.1f MB".format(b / 1_048_576.0) else "${b / 1024} KB"

@Composable
fun ImageCell(
    item: ImageItem, rec: ImageEntity?, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = item.thumbUrl, contentDescription = item.alt,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        if (selected) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (rec?.favorite == true) {
                Icon(Icons.Default.Favorite, null, tint = Color.Red, modifier = Modifier.size(20.dp))
            }
            if (rec != null && rec.status != Status.NONE) {
                Text(
                    Status.label(rec.status), color = Color.White, fontSize = 10.sp,
                    modifier = Modifier
                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp)
                )
            }
        }
    }
}

// ───────────────────────── البحث ─────────────────────────
@Composable
fun SearchScreen(vm: AppViewModel) {
    val tracked by vm.tracked.collectAsState()
    val history by vm.history.collectAsState()
    val gridState = rememberLazyGridState()
    val focus = LocalFocusManager.current
    val selecting = vm.selected.isNotEmpty()
    val doneKeys = remember(tracked) { tracked.filterValues { it.status == Status.DONE }.keys }
    val shown = remember(vm.results, vm.filters, tracked, vm.source) {
        ResultFilter.apply(vm.results, vm.filters, SourceCatalog.caps(vm.source), doneKeys)
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val vis = gridState.layoutInfo.visibleItemsInfo
            val last = vis.lastOrNull()?.index ?: 0
            Triple(vis.isNotEmpty() && last >= gridState.layoutInfo.totalItemsCount - 6, vm.results.size, vm.loading)
        }.filter { it.first && !it.third }.collect { vm.loadMore() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = vm.query, onValueChange = { vm.query = it },
                placeholder = { Text("ابحث عن صور (عربي أو English)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(); focus.clearFocus() }),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { vm.search(); focus.clearFocus() }) { Text("بحث") }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val n = vm.filters.activeCount(SourceCatalog.caps(vm.source))
            OutlinedButton(onClick = { vm.showFilters = true }) { Text(if (n > 0) "تصفية ($n)" else "تصفية") }
            if (n > 0) TextButton(onClick = { vm.applyFilters(SearchFilters()) }) { Text("مسح الفلاتر") }
        }
        if (vm.showFilters) FilterSheet(vm)
        if (vm.searched) {
            val extra = Styles.extras(vm.filters)
            if (extra.isNotBlank()) Text("كلمات مضافة للبحث: $extra", fontSize = 11.sp)
        }

        if (selecting) {
            val sel = vm.selectedItems()
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Text("${sel.size} محددة", modifier = Modifier.padding(horizontal = 8.dp))
                TextButton(onClick = { vm.selectAll(shown) }) { Text("الكل") }
                TextButton(onClick = { vm.requestDownload(sel) }) { Text("تنزيل") }
                TextButton(onClick = { vm.copyLinks(sel) }) { Text("نسخ الروابط") }
                TextButton(onClick = { vm.exportItems(sel) }) { Text("CSV") }
                TextButton(onClick = { vm.clearSelection() }) { Text("إلغاء") }
            }
        } else if (vm.searched) {
            val rem = vm.remaining?.let { " • متبقٍّ من حد الطلبات: $it" } ?: ""
            Text(
                "النتائج: ${vm.total} • معروض: ${shown.size}$rem  (اضغط مطولًا للتحديد)",
                fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), state = gridState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(shown, key = { it.key }) { item ->
                    ImageCell(
                        item = item, rec = tracked[item.key], selected = item.key in vm.selected,
                        onClick = { if (selecting) vm.toggleSelect(item.key) else vm.detail = item },
                        onLongClick = { vm.toggleSelect(item.key) }
                    )
                }
                if (vm.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                if (vm.error != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { vm.retryLoad() }) { Text("إعادة المحاولة") }
                        }
                    }
                }
            }
            if (shown.isEmpty() && !vm.loading && vm.error == null) {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (vm.searched) {
                        if (vm.results.isNotEmpty()) {
                            Text("استُبعدت كل النتائج المحمّلة بسبب الفلاتر.")
                            if (vm.hasNext) OutlinedButton(onClick = { vm.loadMore() }) { Text("تحميل المزيد") }
                        } else {
                            Text("لا توجد نتائج. جرّب كلمة أخرى (الإنجليزية غالبًا أدق).")
                        }
                    } else {
                        Text("اكتب اسمًا واضغط «بحث».")
                        history.forEach { h ->
                            TextButton(onClick = { vm.searchFrom(h.text) }) { Text(h.text) }
                        }
                        if (history.isNotEmpty()) TextButton(onClick = { vm.clearHistory() }) { Text("مسح السجل") }
                    }
                }
            }
        }
        Text("المصدر: ${SourceCatalog.label(vm.source)}", fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
    }
}

// ───────────────────────── المفضلة ─────────────────────────
@Composable
fun FavoritesScreen(vm: AppViewModel) {
    val favs by vm.favorites.collectAsState()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("المفضلة (${favs.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.exportEntities(favs) }) { Text("تصدير CSV") }
        }
        if (favs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد صور مفضلة بعد.") }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(favs, key = { it.imageKey }) { e ->
                    val item = e.toItem()
                    ImageCell(item, e, false, onClick = { vm.detail = item }, onLongClick = { vm.detail = item })
                }
            }
        }
    }
}

// ───────────────────────── التنزيلات ─────────────────────────
@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val list by vm.downloads.collectAsState()
    val done = list.count { it.status == Status.DONE }
    val failed = list.count { it.status == Status.FAILED }
    val waiting = list.count { it.status == Status.QUEUED || it.status == Status.DOWNLOADING }
    val paused = list.count { it.status == Status.PAUSED }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("سجل التنزيلات", style = MaterialTheme.typography.titleLarge)
        Text("ناجح: $done • فاشل: $failed • قيد الانتظار: $waiting • متوقف: $paused")
        LinearProgressIndicator(
            progress = { if (list.isEmpty()) 0f else done.toFloat() / list.size },
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { vm.pauseDownloads() }) { Text("إيقاف مؤقت") }
            OutlinedButton(onClick = { vm.resumeDownloads() }) { Text("استئناف") }
            OutlinedButton(onClick = { vm.retryFailed() }) { Text("إعادة الفاشلة") }
            OutlinedButton(onClick = { vm.exportEntities(list) }) { Text("CSV") }
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد تنزيلات بعد.") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.imageKey }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clickableRow { vm.detail = e.toItem() },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = e.thumbUrl, contentDescription = e.alt,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Column(Modifier.weight(1f)) {
                            Text(e.photographer.ifBlank { e.imageKey })
                            Text(
                                Status.label(e.status) + (if (e.status == Status.DONE) " • ${fmtBytes(e.sizeBytes)}" else "") +
                                    (e.error?.let { " • $it" } ?: ""),
                                fontSize = 12.sp
                            )
                            Text("بحث: ${e.searchQuery}", fontSize = 11.sp)
                        }
                        if (e.status == Status.FAILED) TextButton(onClick = { vm.retry(e.imageKey) }) { Text("إعادة") }
                    }
                }
            }
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

// ───────────────────────── الإعدادات ─────────────────────────
@Composable
fun SettingsScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("الإعدادات", style = MaterialTheme.typography.titleLarge)

        Text("مصدر الصور", style = MaterialTheme.typography.titleMedium)
        (listOf("all") + SourceCatalog.all.map { it.id }).forEach { sid ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = vm.source == sid, onClick = { vm.setSourcePref(sid) })
                Text(SourceCatalog.label(sid))
            }
        }
        OutlinedButton(onClick = { vm.showAdminLogin = true }, modifier = Modifier.fillMaxWidth()) {
            Text("لوحة التحكم (المشرف): المفاتيح والمصادر")
        }

        Text("مجلد الحفظ", style = MaterialTheme.typography.titleMedium)
        Text(vm.folderName ?: "لم يُختر بعد")
        OutlinedButton(onClick = { vm.pickFolder() }) { Text("اختيار مجلد") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.subfolders, onCheckedChange = { vm.setSubfoldersPref(it) })
            Text("  مجلد فرعي باسم البحث")
        }

        Text("جودة التنزيل", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = vm.quality == "LARGE", onClick = { vm.setQualityPref("LARGE") })
            Text("كبيرة (~1880px، أخف)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = vm.quality == "ORIGINAL", onClick = { vm.setQualityPref("ORIGINAL") })
            Text("الأصلية (أثقل)")
        }

        Text("التراخيص والشروط", style = MaterialTheme.typography.titleMedium)
        Text(
            "التنزيل يتم صورة واحدة كل مرة التزامًا بشروط المصدر. لا تفترض أن كل صورة صالحة لإعادة النشر؛ " +
                "راجع ترخيص Pexels، واحتفظ باسم المصوّر والمصدر (يتضمنهما تصدير CSV).",
            fontSize = 12.sp
        )
    }
}

// ───────────────────────── التفاصيل والحوار ─────────────────────────
@Composable
fun DetailSheet(vm: AppViewModel, item: ImageItem) {
    val ctx = LocalContext.current
    val tracked by vm.tracked.collectAsState()
    val rec = tracked[item.key]
    ModalBottomSheet(onDismissRequest = { vm.detail = null }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = item.largeUrl, contentDescription = item.alt,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
            )
            Text("المصوّر: ${item.photographer}")
            Text("الأبعاد: ${item.width}×${item.height}")
            Text("المصدر: ${SourceCatalog.name(item.source)}")
            Text("الترخيص: ${item.license}")
            if (item.alt.isNotBlank()) Text("الوصف: ${item.alt}")
            Text("الحالة: ${Status.label(rec?.status ?: Status.NONE)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.requestDownload(listOf(item)) }, modifier = Modifier.weight(1f)) { Text("تنزيل") }
                OutlinedButton(onClick = { vm.toggleFavorite(item) }, modifier = Modifier.weight(1f)) {
                    Text(if (rec?.favorite == true) "إزالة من المفضلة" else "أضف للمفضلة")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.copyLinks(listOf(item)) }, modifier = Modifier.weight(1f)) { Text("نسخ الرابط") }
                OutlinedButton(onClick = { openUrl(ctx, item.pageUrl) }, modifier = Modifier.weight(1f)) { Text("صفحة المصدر") }
            }
            TextButton(onClick = { openUrl(ctx, item.photographerUrl) }) { Text("صفحة المصوّر") }
        }
    }
}

@Composable
fun DownloadConfirmDialog(vm: AppViewModel, items: List<ImageItem>) {
    val tracked by vm.tracked.collectAsState()
    val todo = items.count { tracked[it.key]?.status != Status.DONE }
    val skipped = items.size - todo
    AlertDialog(
        onDismissRequest = { vm.cancelDownloadRequest() },
        title = { Text("تأكيد التنزيل") },
        text = {
            Text(
                "سيتم تنزيل $todo صورة" + (if (skipped > 0) " (تخطي $skipped محفوظة سابقًا)" else "") +
                    ".\nالحجم التقديري ≈ ${fmtBytes(vm.estimateBytes(items))}" +
                    (if (vm.folderName == null) "\nسيُطلب منك اختيار مجلد الحفظ." else "")
            )
        },
        confirmButton = { TextButton(onClick = { vm.confirmDownload() }) { Text("تنزيل") } },
        dismissButton = { TextButton(onClick = { vm.cancelDownloadRequest() }) { Text("إلغاء") } }
    )
}

// ───────────────────────── الفلاتر ─────────────────────────
@Composable
private fun ChipGroup(title: String, options: List<Pair<String?, String>>, selected: String?, onSelect: (String?) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (v, l) ->
            FilterChip(selected = selected == v, onClick = { onSelect(v) }, label = { Text(l) })
        }
    }
}

@Composable
private fun ColorChips(selected: String?, onSelect: (String?) -> Unit) {
    Text("اللون", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("الكل") })
        ColorCatalog.all.forEach { c ->
            FilterChip(
                selected = selected == c.id, onClick = { onSelect(c.id) }, label = { Text(c.label) },
                leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(c.swatch))) }
            )
        }
    }
}

@Composable
fun FilterSheet(vm: AppViewModel) {
    var f by remember { mutableStateOf(vm.filters) }
    val caps = SourceCatalog.caps(vm.source)
    val categories = listOf<Pair<String?, String>>(
        null to "الكل", "backgrounds" to "خلفيات", "fashion" to "أزياء", "nature" to "طبيعة", "science" to "علوم",
        "education" to "تعليم", "feelings" to "مشاعر", "health" to "صحة", "people" to "أشخاص", "religion" to "دين",
        "places" to "أماكن", "animals" to "حيوانات", "industry" to "صناعة", "computer" to "حاسوب", "food" to "طعام",
        "sports" to "رياضة", "transportation" to "مواصلات", "travel" to "سفر", "buildings" to "مباني",
        "business" to "أعمال", "music" to "موسيقى"
    )
    ModalBottomSheet(onDismissRequest = { vm.showFilters = false }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("تصفية النتائج", style = MaterialTheme.typography.titleLarge)
            Text("المصدر: ${SourceCatalog.label(vm.source)} (تُطبَّق الفلاتر التي يدعمها كل مصدر)", fontSize = 12.sp)
            if (caps.isEmpty()) Text("هذا المصدر لا يدعم فلاتر إضافية.")
            if ("orientation" in caps) {
                val orient = buildList<Pair<String?, String>> {
                    add(null to "الكل"); add("landscape" to "أفقي"); add("portrait" to "عمودي")
                    if ("square" in caps) add("square" to "مربع")
                }
                ChipGroup("الاتجاه", orient, f.orientation) { f = f.copy(orientation = it) }
            }
            if ("color" in caps) ColorChips(f.color) { f = f.copy(color = it) }
            if ("size" in caps) {
                ChipGroup(
                    "الحجم",
                    listOf<Pair<String?, String>>(null to "الكل", "large" to "كبير", "medium" to "متوسط", "small" to "صغير"),
                    f.size
                ) { f = f.copy(size = it) }
            }
            if ("type" in caps) {
                val types = buildList<Pair<String?, String>> {
                    add(null to "الكل"); add("photo" to "فوتوغرافية"); add("illustration" to "رسم")
                    if ("vector" in caps) add("vector" to "فيكتور")
                    if ("artwork" in caps) add("artwork" to "عمل فني")
                }
                ChipGroup("نوع الصورة", types, f.imageType) { f = f.copy(imageType = it) }
            }
            if ("category" in caps) ChipGroup("التصنيف", categories, f.category) { f = f.copy(category = it) }
            if ("minWidth" in caps) {
                ChipGroup(
                    "أقل عرض (بكسل)",
                    listOf<Pair<String?, String>>(null to "الكل", "800" to "800", "1280" to "1280", "1920" to "1920", "3000" to "3000"),
                    if (f.minWidth > 0) f.minWidth.toString() else null
                ) { f = f.copy(minWidth = it?.toIntOrNull() ?: 0) }
            }
            if ("order" in caps) {
                val orders = buildList<Pair<String?, String>> {
                    add(null to "الافتراضي"); add("latest" to "الأحدث")
                    if ("popular" in caps) add("popular" to "الأشهر")
                }
                ChipGroup("الترتيب", orders, f.order) { f = f.copy(order = it) }
            }
            if ("editors" in caps) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = f.editorsChoice, onCheckedChange = { f = f.copy(editorsChoice = it) })
                    Text("  اختيار المحررين فقط")
                }
            }
            ChipGroup(
                "الخلفية",
                listOf<Pair<String?, String>>(null to "الكل") + Styles.backgrounds,
                f.background
            ) { f = f.copy(background = it) }
            ChipGroup(
                "الأسلوب / النوع",
                listOf<Pair<String?, String>>(null to "الكل") + Styles.styles,
                f.style
            ) { f = f.copy(style = it) }
            ChipGroup(
                "صيغة الملف",
                listOf<Pair<String?, String>>(null to "الكل", "jpg" to "JPG", "png" to "PNG", "webp" to "WEBP", "gif" to "GIF"),
                f.format
            ) { f = f.copy(format = it) }
            Text("الخلفية والأسلوب يضيفان كلمات إنجليزية لبحثك؛ الأفضل أن تكتب بحثك بالإنجليزية.", fontSize = 11.sp)
            if ("minHeight" in caps) {
                ChipGroup(
                    "أقل ارتفاع (بكسل)",
                    listOf<Pair<String?, String>>(null to "الكل", "600" to "600", "1080" to "1080", "1440" to "1440", "2160" to "2160"),
                    if (f.minHeight > 0) f.minHeight.toString() else null
                ) { f = f.copy(minHeight = it?.toIntOrNull() ?: 0) }
            }
            ChipGroup(
                "أقل دقة (ميغابكسل)",
                listOf<Pair<String?, String>>(null to "الكل", "2" to "2+", "4" to "4+", "8" to "8+", "12" to "12+"),
                if (f.minMp > 0) f.minMp.toString() else null
            ) { f = f.copy(minMp = it?.toIntOrNull() ?: 0) }
            ChipGroup(
                "ترتيب العرض",
                listOf<Pair<String?, String>>(null to "كما وصل", "big" to "الأكبر أولًا", "small" to "الأصغر أولًا"),
                f.sortBy
            ) { f = f.copy(sortBy = it) }
            if ("sources" in caps) {
                Text("المصادر المشمولة (فارغ = الكل)", style = MaterialTheme.typography.titleMedium)
                val chosen = f.onlySources.split(",").filter { it.isNotBlank() }.toSet()
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SourceCatalog.all.forEach { info ->
                        FilterChip(
                            selected = info.id in chosen,
                            onClick = {
                                val n = if (info.id in chosen) chosen - info.id else chosen + info.id
                                f = f.copy(onlySources = n.joinToString(","))
                            },
                            label = { Text(info.name) }
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = f.commercialOnly, onCheckedChange = { f = f.copy(commercialOnly = it) })
                Text("  الاستخدام التجاري مسموح فقط")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = f.hideDownloaded, onCheckedChange = { f = f.copy(hideDownloaded = it) })
                Text("  إخفاء ما نزّلته سابقًا")
            }
            OutlinedTextField(
                value = f.exclude, onValueChange = { f = f.copy(exclude = it) }, singleLine = true,
                label = { Text("استبعاد كلمات (افصل بفاصلة)") }, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.applyFilters(f); vm.showFilters = false },
                    modifier = Modifier.weight(1f)
                ) { Text("تطبيق") }
                OutlinedButton(onClick = { f = SearchFilters() }, modifier = Modifier.weight(1f)) { Text("مسح الكل") }
            }
        }
    }
}

// ───────────────────────── لوحة التحكم (المشرف) ─────────────────────────
@Composable
fun AdminLoginDialog(vm: AppViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!vm.adminBusy) { vm.adminError = null; vm.showAdminLogin = false } },
        title = { Text("دخول المشرف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, singleLine = true,
                    label = { Text("بريد المشرف") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true,
                    label = { Text("كلمة المرور") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (vm.adminBusy) CircularProgressIndicator()
                vm.adminError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { vm.adminSignUp(email, password) }, enabled = !vm.adminBusy) {
                    Text("أول مرة؟ إنشاء حساب المشرف")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.adminLogin(email, password) }, enabled = !vm.adminBusy) { Text("دخول") }
        },
        dismissButton = {
            TextButton(onClick = { vm.adminError = null; vm.showAdminLogin = false }, enabled = !vm.adminBusy) { Text("إلغاء") }
        }
    )
}

private fun mask(k: String): String = if (k.length <= 8) "••••" else k.take(4) + "…" + k.takeLast(3)

private fun hhmm(t: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

@Composable
fun AdminScreen(vm: AppViewModel) {
    val tick = vm.adminTick
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("لوحة التحكم", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.adminSignOut() }) { Text("خروج") }
            TextButton(onClick = { vm.lockAdmin() }) { Text("قفل") }
        }
        Text("المشرف: ${AdminConfig.EMAIL}", fontSize = 12.sp)
        Text(
            "عند إضافة أكثر من مفتاح لنفس الموقع يتناوب التطبيق بينها تلقائيًا مع كل طلب. " +
                "إذا بلغ مفتاح حد الطلبات يُوقَف مؤقتًا ويُجرَّب التالي. " +
                "اختر «كل المصادر» من الإعدادات للتناوب بين المواقع أيضًا.",
            fontSize = 12.sp
        )
        SourceCatalog.all.forEach { info -> SourceCard(vm, info, tick) }
        Text(
            "المفاتيح محفوظة في مشروع Supabase الخاص بك (لا يقرؤها إلا حساب المشرف) وتُنسخ إلى هذا الهاتف للبحث. " +
                "بعض المصادر تشترط ذكر المصوّر والمصدر؛ راجع ترخيص كل صورة قبل إعادة نشرها.",
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SourceCard(vm: AppViewModel, info: SourceInfo, tick: Int) {
    val ctx = LocalContext.current
    val keys = remember(tick) { vm.pool.allKeys(info.id) }
    val siteOn = remember(tick) { vm.pool.siteEnabled(info.id) }
    var label by remember(info.id) { mutableStateOf("") }
    var keyText by remember(info.id) { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (siteOn) "مفعّل" else "معطّل", fontSize = 12.sp)
                Switch(checked = siteOn, onCheckedChange = { vm.setSiteEnabled(info.id, it) })
            }
            Text(info.note, fontSize = 12.sp)
            if (info.needsKey) {
                TextButton(onClick = { openUrl(ctx, info.keyUrl) }) { Text("احصل على مفتاح (${info.keyName})") }
                if (keys.isEmpty()) Text("لا توجد مفاتيح بعد.", fontSize = 12.sp)
                keys.forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(k.label)
                            val until = vm.pool.coolingUntil(k.id)
                            Text(
                                mask(k.key) + (if (until > System.currentTimeMillis()) " • محدود مؤقتًا حتى ${hhmm(until)}" else ""),
                                fontSize = 11.sp
                            )
                        }
                        if (!k.id.startsWith("builtin")) {
                            Switch(checked = k.enabled, onCheckedChange = { vm.setKeyEnabled(info.id, k.id, it) })
                            TextButton(onClick = { vm.removeKey(info.id, k.id) }) { Text("حذف") }
                        }
                    }
                }
                OutlinedTextField(
                    value = label, onValueChange = { label = it }, singleLine = true,
                    label = { Text("اسم الحساب (اختياري)") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keyText, onValueChange = { keyText = it }, singleLine = true,
                    label = { Text("المفتاح") }, modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { vm.addKey(info.id, label, keyText) { ok -> if (ok) { label = ""; keyText = "" } } }) {
                    Text("إضافة مفتاح")
                }
            } else {
                Text("لا يحتاج مفتاحًا ✓", fontSize = 12.sp)
            }
        }
    }
}
