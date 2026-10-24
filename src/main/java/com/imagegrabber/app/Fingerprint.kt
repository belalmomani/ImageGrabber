package com.imagegrabber.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** بصمة بصرية dHash بطول 64 بت: تتحمل تغيير الحجم وإعادة الضغط وتعديل السطوع. */
object DHash {
    /** lum: 72 قيمة (9 أعمدة × 8 صفوف) صفًا بصف. */
    fun fromLum(lum: IntArray): Long {
        require(lum.size == 72)
        var h = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (lum[y * 9 + x] > lum[y * 9 + x + 1]) h = h or (1L shl bit)
                bit++
            }
        }
        return h
    }

    fun fromBitmap(src: Bitmap): Long {
        val s = Bitmap.createScaledBitmap(src, 9, 8, true)
        val lum = IntArray(72)
        for (y in 0 until 8) {
            for (x in 0 until 9) {
                val p = s.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                lum[y * 9 + x] = (r * 299 + g * 587 + b * 114) / 1000
            }
        }
        if (s !== src) s.recycle()
        return fromLum(lum)
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** بصمة صور شبه خالية من التفاصيل (خلفية سادة مثلًا) لا يُعتمد عليها في الحكم بالتطابق. */
    fun reliable(h: Long): Boolean = java.lang.Long.bitCount(h) in 10..54
}

class FpEntry(val key: String, val hash: Long, val w: Int, val h: Int)

class Groups(
    val dup: Map<String, Int>,
    val sim: Map<String, Int>,
    val dupSize: Map<Int, Int>,
    val simSize: Map<Int, Int>
)

/**
 * مكرر: نفس الصورة (بصمة شبه متطابقة + نسبة أبعاد متقاربة).
 * مشابه: صورة قريبة بصريًا وليست بالضرورة نفسها (لا تُعامل كمطابقة تلقائيًا).
 */
object DupGroups {
    const val DUP_T = 3
    const val SIM_T = 10

    fun aspectOk(a: FpEntry, b: FpEntry): Boolean {
        if (a.w <= 0 || a.h <= 0 || b.w <= 0 || b.h <= 0) return true
        val ra = a.w.toDouble() / a.h
        val rb = b.w.toDouble() / b.h
        return abs(ra - rb) / maxOf(ra, rb) <= 0.05
    }

    private fun find(p: IntArray, x: Int): Int {
        var r = x
        while (p[r] != r) {
            p[r] = p[p[r]]
            r = p[r]
        }
        return r
    }

    private fun union(p: IntArray, a: Int, b: Int) {
        val ra = find(p, a)
        val rb = find(p, b)
        if (ra != rb) p[maxOf(ra, rb)] = minOf(ra, rb)
    }

    fun group(entries: List<FpEntry>): Groups {
        val n = entries.size
        val pd = IntArray(n) { it }
        val ps = IntArray(n) { it }
        for (i in 0 until n) {
            val a = entries[i]
            if (!DHash.reliable(a.hash)) continue
            for (j in i + 1 until n) {
                val b = entries[j]
                if (!DHash.reliable(b.hash)) continue
                val d = DHash.distance(a.hash, b.hash)
                if (d <= SIM_T) union(ps, i, j)
                if (d <= DUP_T && aspectOk(a, b)) union(pd, i, j)
            }
        }
        fun build(p: IntArray): Pair<Map<String, Int>, Map<Int, Int>> {
            val counts = HashMap<Int, Int>()
            for (i in 0 until n) {
                val r = find(p, i)
                counts[r] = (counts[r] ?: 0) + 1
            }
            val of = HashMap<String, Int>()
            for (i in 0 until n) {
                val r = find(p, i)
                if ((counts[r] ?: 0) >= 2) of[entries[i].key] = r
            }
            return of to counts.filterValues { it >= 2 }
        }
        val (dup, dupSize) = build(pd)
        val (sim, simSize) = build(ps)
        return Groups(dup, sim, dupSize, simSize)
    }
}

class DedupeContext(
    val dupGroup: Map<String, Int>,
    val simGroup: Map<String, Int>,
    val dupSize: Map<Int, Int>,
    val simSize: Map<Int, Int>,
    val inLibrary: Set<String>,
    val preferred: Set<String>
) {
    fun tag(key: String, done: Boolean): String? {
        if (key in inLibrary && !done) return "في مكتبتك"
        val d = dupGroup[key]
        if (d != null) return "مكرر ×" + (dupSize[d] ?: 2)
        val s = simGroup[key]
        if (s != null) return "مشابه ×" + (simSize[s] ?: 2)
        return null
    }
}

object Dedupe {
    fun build(
        items: List<ImageItem>, hashes: Map<String, Long>, library: List<FingerprintEntity>,
        preferred: Set<String>, limit: Int
    ): DedupeContext {
        val entries = items.take(limit).mapNotNull { it ->
            hashes[it.key]?.let { h -> FpEntry(it.key, h, it.width, it.height) }
        }
        val g = DupGroups.group(entries)
        val lib = library.filter { DHash.reliable(it.phash) }
        val inLib = HashSet<String>()
        if (lib.isNotEmpty()) {
            for (e in entries) {
                if (!DHash.reliable(e.hash)) continue
                if (lib.any { l -> l.imageKey != e.key && DHash.distance(e.hash, l.phash) <= DupGroups.DUP_T }) {
                    inLib.add(e.key)
                }
            }
        }
        return DedupeContext(g.dup, g.sim, g.dupSize, g.simSize, inLib, preferred)
    }

    /** يطبّق وضع المكررات على القائمة (بعد بقية الفلاتر). */
    fun apply(list: List<ImageItem>, mode: String, d: DedupeContext, doneKeys: Set<String>): List<ImageItem> {
        fun rank(i: ImageItem): Long = i.width.toLong() * i.height
        fun pickRep(ms: List<ImageItem>): ImageItem =
            ms.firstOrNull { it.key in d.preferred }
                ?: ms.firstOrNull { it.key in doneKeys }
                ?: ms.maxByOrNull { rank(it) }
                ?: ms.first()

        val lib = { i: ImageItem -> i.key in d.inLibrary && i.key !in doneKeys }
        return when (mode) {
            "hide" -> {
                val base = list.filter { !lib(it) }
                val reps = HashSet<String>()
                base.filter { it.key in d.dupGroup }.groupBy { d.dupGroup[it.key] }
                    .forEach { (_, ms) -> reps.add(pickRep(ms).key) }
                base.filter { it.key !in d.dupGroup || it.key in reps }
            }
            "one" -> {
                val base = list.filter { !lib(it) }
                val reps = HashSet<String>()
                base.filter { it.key in d.simGroup }.groupBy { d.simGroup[it.key] }
                    .forEach { (_, ms) -> reps.add(pickRep(ms).key) }
                base.filter { it.key !in d.simGroup || it.key in reps }
            }
            "review" -> list.filter { it.key in d.dupGroup || lib(it) }.sortedWith(
                compareBy<ImageItem>({ d.dupGroup[it.key] ?: Int.MAX_VALUE }, { it.key !in d.preferred }, { -rank(it) })
            )
            else -> list
        }
    }
}

object Fingerprinter {
    /** بصمة من الصورة المصغّرة (غالبًا موجودة في ذاكرة Coil المؤقتة، فلا تنزيل إضافي). */
    suspend fun hashThumb(ctx: Context, url: String): Long? {
        return try {
            val req = ImageRequest.Builder(ctx).data(url).size(128).allowHardware(false).build()
            val res = ctx.imageLoader.execute(req)
            val bmp = ((res as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            if (bmp == null) null else DHash.fromBitmap(bmp)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** بصمة من الملف المحفوظ بفك ترميز مصغَّر (دون تحميل الصورة كاملة في الذاكرة). */
    fun hashFile(ctx: Context, uri: Uri): Long? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) {
            null
        } else {
            var sample = 1
            while (minOf(w, h) / (sample * 2) >= 128) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (bmp == null) {
                null
            } else {
                val hash = DHash.fromBitmap(bmp)
                bmp.recycle()
                hash
            }
        }
    } catch (e: Exception) {
        null
    }

    /** بعد حفظ الملف: SHA-256 للتطابق الثنائي + بصمة بصرية من الملف نفسه. */
    suspend fun afterDownload(ctx: Context, item: ImageEntity, r: DownloadResult) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(ctx).fingerprints()
        val fromFile = hashFile(ctx, Uri.parse(r.uri))
        val known = dao.getByKeys(listOf(item.imageKey)).firstOrNull()?.phash
        val hash = fromFile ?: known ?: 0L
        val sha = r.sha256
        val dupOf = if (sha != null) dao.findBySha(sha, item.imageKey)?.imageKey else null
        dao.upsert(listOf(FingerprintEntity(item.imageKey, hash, sha, true, System.currentTimeMillis(), dupOf)))
    }
}
