package com.imagegrabber.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

class DownloadException(message: String) : IOException(message)

object Downloader {
    private const val MAX_BYTES = 30L * 1024 * 1024 // سقف حجم الملف الواحد

    /** يُنزّل صورة واحدة إلى المجلد المختار. يعيد (uri، الحجم). */
    fun download(
        ctx: Context, item: ImageEntity, settings: SettingsStore, shouldStop: () -> Boolean
    ): Pair<String, Long> {
        val rootUri = settings.folderUri ?: throw DownloadException("لم يُختر مجلد الحفظ")
        val root = DocumentFile.fromTreeUri(ctx, Uri.parse(rootUri))
            ?: throw DownloadException("مجلد الحفظ غير صالح")
        if (!root.canWrite()) throw DownloadException("لا توجد صلاحية كتابة على المجلد، اخترْه مجددًا")
        val dir = if (settings.subfolders) subDir(root, item.searchQuery) else root

        val url = if (settings.quality == "ORIGINAL") item.originalUrl else item.largeUrl
        val req = Request.Builder().url(url).build()
        return Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw DownloadException("فشل التنزيل (${resp.code})")
            val body = resp.body ?: throw DownloadException("استجابة فارغة")
            val ct = body.contentType()
            val mime = if (ct != null) "${ct.type}/${ct.subtype}".lowercase() else ""
            val ext = when (mime) {
                "image/jpeg", "image/jpg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> throw DownloadException("نوع الملف غير مدعوم")
            }
            val len = body.contentLength()
            if (len > MAX_BYTES) throw DownloadException("حجم الملف كبير جدًا")

            val fileName = "${item.source}_${item.sourceId}.$ext"
            val existing = dir.findFile(fileName)
            if (existing != null && existing.length() > 0) {
                return@use existing.uri.toString() to existing.length()
            }
            val outMime = if (ext == "jpg") "image/jpeg" else "image/$ext"
            val file = dir.createFile(outMime, fileName)
                ?: throw DownloadException("تعذّر إنشاء الملف")
            var total = 0L
            try {
                val out = ctx.contentResolver.openOutputStream(file.uri)
                    ?: throw DownloadException("تعذّر فتح الملف للكتابة")
                out.use { os ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            if (shouldStop()) throw CancellationException("stopped")
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_BYTES) throw DownloadException("حجم الملف كبير جدًا")
                            os.write(buf, 0, n)
                        }
                    }
                }
                if (len > 0 && total != len) throw DownloadException("الملف غير مكتمل")
            } catch (e: Throwable) {
                file.delete()
                throw e
            }
            file.uri.toString() to total
        }
    }

    private fun subDir(root: DocumentFile, query: String): DocumentFile {
        val name = query.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60).ifBlank { "صور" }
        val found = root.findFile(name)
        if (found != null && found.isDirectory) return found
        return root.createDirectory(name) ?: throw DownloadException("تعذّر إنشاء المجلد الفرعي")
    }
}

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = info("جارٍ تجهيز التنزيل…")

    override suspend fun doWork(): Result {
        val dao = AppDatabase.get(applicationContext).images()
        val settings = SettingsStore(applicationContext)
        try {
            setForeground(info("جارٍ تنزيل الصور…"))
        } catch (e: Exception) {
            // الإشعار غير ضروري لنجاح التنزيل
        }
        // استرجاع ما انقطع أثناء التنزيل سابقًا
        dao.moveAll(Status.DOWNLOADING, Status.QUEUED, System.currentTimeMillis())

        while (true) {
            currentCoroutineContext().ensureActive()
            val item = dao.nextQueued() ?: break
            dao.setStatus(item.imageKey, Status.DOWNLOADING, null, System.currentTimeMillis())
            var attempt = 0
            while (true) {
                try {
                    val (uri, size) = withContext(Dispatchers.IO) {
                        Downloader.download(applicationContext, item, settings) { isStopped }
                    }
                    dao.setDone(item.imageKey, uri, size, System.currentTimeMillis())
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DownloadException) {
                    dao.setStatus(item.imageKey, Status.FAILED, e.message, System.currentTimeMillis())
                    break
                } catch (e: IOException) {
                    // خطأ شبكة عابر: إعادة محاولة تلقائية قبل تسجيل الفشل
                    attempt++
                    if (attempt < 3) {
                        delay(2000L * attempt)
                    } else {
                        dao.setStatus(item.imageKey, Status.FAILED, "انقطع الاتصال أو تعذّرت الكتابة", System.currentTimeMillis())
                        break
                    }
                } catch (e: Exception) {
                    dao.setStatus(item.imageKey, Status.FAILED, "فشل غير متوقع", System.currentTimeMillis())
                    break
                }
            }
        }
        return Result.success()
    }

    private fun info(text: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("downloads", "التنزيلات", NotificationManager.IMPORTANCE_LOW)
        )
        val n = NotificationCompat.Builder(applicationContext, "downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("صيّاد الصور")
            .setContentText(text)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1, n)
        }
    }
}
