@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.filter

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

    LaunchedEffect(gridState) {
        snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            Triple(last >= gridState.layoutInfo.totalItemsCount - 6, vm.results.size, vm.loading)
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
            val n = vm.filters.activeCount(vm.source)
            OutlinedButton(onClick = { vm.showFilters = true }) { Text(if (n > 0) "تصفية ($n)" else "تصفية") }
            if (n > 0) TextButton(onClick = { vm.applyFilters(SearchFilters()) }) { Text("مسح الفلاتر") }
        }
        if (vm.showFilters) FilterSheet(vm)

        if (selecting) {
            val sel = vm.selectedItems()
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Text("${sel.size} محددة", modifier = Modifier.padding(horizontal = 8.dp))
                TextButton(onClick = { vm.selectAll() }) { Text("الكل") }
                TextButton(onClick = { vm.requestDownload(sel) }) { Text("تنزيل") }
                TextButton(onClick = { vm.copyLinks(sel) }) { Text("نسخ الروابط") }
                TextButton(onClick = { vm.exportItems(sel) }) { Text("CSV") }
                TextButton(onClick = { vm.clearSelection() }) { Text("إلغاء") }
            }
        } else if (vm.searched) {
            val rem = vm.remaining?.let { " • متبقٍّ من حد الطلبات: $it" } ?: ""
            Text(
                "النتائج: ${vm.total}$rem  (اضغط مطولًا للتحديد)",
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
                items(vm.results, key = { it.key }) { item ->
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
            if (vm.results.isEmpty() && !vm.loading && vm.error == null) {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (vm.searched) {
                        Text("لا توجد نتائج. جرّب كلمة أخرى (الإنجليزية غالبًا أدق في Pexels).")
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
        Text("الصور من ${if (vm.source == "pixabay") "Pixabay" else "Pexels"}", fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = vm.source == "pixabay", onClick = { vm.setSourcePref("pixabay") })
            Text("Pixabay   ")
            RadioButton(selected = vm.source == "pexels", onClick = { vm.setSourcePref("pexels") })
            Text("Pexels")
        }
        val isPix = vm.source == "pixabay"
        Text(if (isPix) "مفتاح Pixabay API" else "مفتاح Pexels API", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = if (isPix) vm.pixabayKeyInput else vm.apiKeyInput,
            onValueChange = { if (isPix) vm.pixabayKeyInput = it else vm.apiKeyInput = it },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text("الصق مفتاحك هنا") }, modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.saveKey() }) { Text("حفظ المفتاح") }
        Text("يُحفظ داخل الهاتف فقط ولا يُرفع إلى GitHub.", fontSize = 12.sp)

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
            Text("المصدر: ${if (item.source == "pixabay") "Pixabay" else "Pexels"}")
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
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, l) ->
            FilterChip(selected = selected == v, onClick = { onSelect(v) }, label = { Text(l) })
        }
    }
}

@Composable
fun FilterSheet(vm: AppViewModel) {
    var f by remember { mutableStateOf(vm.filters) }
    val pix = vm.source == "pixabay"
    val orient = buildList<Pair<String?, String>> {
        add(null to "الكل"); add("landscape" to "أفقي"); add("portrait" to "عمودي")
        if (!pix) add("square" to "مربع")
    }
    val colors = listOf<Pair<String?, String>>(
        null to "الكل", "red" to "أحمر", "orange" to "برتقالي", "yellow" to "أصفر", "green" to "أخضر",
        "turquoise" to "تركواز", "blue" to "أزرق", "violet" to "بنفسجي", "pink" to "وردي",
        "brown" to "بني", "black" to "أسود", "gray" to "رمادي", "white" to "أبيض"
    )
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
            ChipGroup("الاتجاه", orient, f.orientation) { f = f.copy(orientation = it) }
            ChipGroup("اللون", colors, f.color) { f = f.copy(color = it) }
            if (pix) {
                ChipGroup(
                    "نوع الصورة",
                    listOf(null to "الكل", "photo" to "فوتوغرافية", "illustration" to "رسم", "vector" to "فيكتور"),
                    f.imageType
                ) { f = f.copy(imageType = it) }
                ChipGroup("التصنيف", categories, f.category) { f = f.copy(category = it) }
                ChipGroup(
                    "أقل عرض (بكسل)",
                    listOf(null to "الكل", "800" to "800", "1280" to "1280", "1920" to "1920", "3000" to "3000"),
                    if (f.minWidth > 0) f.minWidth.toString() else null
                ) { f = f.copy(minWidth = it?.toIntOrNull() ?: 0) }
                ChipGroup(
                    "الترتيب",
                    listOf(null to "الافتراضي", "popular" to "الأشهر", "latest" to "الأحدث"),
                    f.order
                ) { f = f.copy(order = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = f.editorsChoice, onCheckedChange = { f = f.copy(editorsChoice = it) })
                    Text("  اختيار المحررين فقط")
                }
            } else {
                ChipGroup(
                    "الحجم",
                    listOf(null to "الكل", "large" to "كبير", "medium" to "متوسط", "small" to "صغير"),
                    f.size
                ) { f = f.copy(size = it) }
            }
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
