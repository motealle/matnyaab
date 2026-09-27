package ir.matnyaab.core

import com.google.gson.JsonParser
import ir.matnyaab.util.PersianUtil
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * سرویس‌های شبکه — معادل SendStatisticsThread، NewsThread، CheckUpdateThread و DownloadThread در نسخه پایتون
 */
private val httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

/** معادل SendStatisticsThread: ارسال آمار بدون مسدود کردن UI؛ خطاها نادیده گرفته می‌شوند */
object StatisticsReporter {
    fun report(action: String, systemId: String, hasSubscription: Boolean) {
        Thread {
            try {
                val body = FormBody.Builder()
                    .add("user_system_id", systemId)
                    .add("has_subscription", if (hasSubscription) "1" else "0")
                    .add("action", action)
                    .add("app_version", Constants.APP_VERSION)
                    .build()
                val req = Request.Builder().url(Constants.APP_STATISTICS_URL).post(body).build()
                httpClient.newCall(req).execute().use { /* نتیجه مهم نیست */ }
            } catch (e: Exception) { /* عدم دسترسی به سرور */ }
        }.apply { isDaemon = true }.start()
    }
}

/** معادل NewsThread: دریافت دوره‌ای اخبار برای نوار متحرک پایین پنجره */
class NewsLoader(private val callback: (List<Pair<String, String>>) -> Unit) {
    @Volatile private var stopped = false
    private var thread: Thread? = null

    fun start() {
        thread = Thread {
            while (!stopped) {
                try {
                    val req = Request.Builder().url(Constants.APP_NEWS_URL).get().build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val arr = JsonParser.parseString(resp.body?.string() ?: "[]").asJsonArray
                            val items = arr.map { el ->
                                val o = el.asJsonObject
                                Pair(o.get("title").asString, o.get("content").asString)
                            }
                            if (items.isNotEmpty() && !stopped) callback(items)
                        }
                    }
                } catch (e: Exception) { /* عدم دسترسی به سرور */ }
                // هر ۱۰ دقیقه دوباره بررسی می‌شود
                for (i in 0 until 600) { if (stopped) break; Thread.sleep(1000) }
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() { stopped = true; thread?.interrupt() }
}

/** معادل CheckUpdateThread: خواندن version.txt و اعلام نسخه جدید */
class UpdateChecker(private val callback: (String) -> Unit) {
    @Volatile private var stopped = false
    private var thread: Thread? = null

    fun start() {
        thread = Thread {
            try {
                val req = Request.Builder().url(Constants.APP_UPDATE_VERSION_URL).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val remote = (resp.body?.string() ?: "").trim()
                        val remoteNum = remote.toDoubleOrNull()
                        val current = Constants.APP_VERSION.toDoubleOrNull() ?: 0.0
                        if (remoteNum != null && remoteNum > current && !stopped) callback(remote)
                    }
                }
            } catch (e: Exception) { /* عدم دسترسی به سرور */ }
        }.apply { isDaemon = true; start() }
    }

    fun stop() { stopped = true }
}

/**
 * معادل DownloadThread: دانلود فایل با گزارش درصد پیشرفت.
 * کد پایان: 0=موفق 1=خطا 2=لغو توسط کاربر
 */
class Downloader(
    private val url: String,
    private val outFile: File,
    private val onProgress: (Int) -> Unit,
    private val onFinish: (Int) -> Unit,
) {
    @Volatile private var cancelled = false
    private var thread: Thread? = null

    fun start() {
        thread = Thread {
            var code = 0
            try {
                val req = Request.Builder().url(url).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) { code = 1 }
                    else {
                        val body = resp.body ?: throw IllegalStateException("empty body")
                        val total = body.contentLength()
                        val parent = outFile.absoluteFile.parentFile ?: File(".").absoluteFile
                        parent.mkdirs()
                        val tempFile = File.createTempFile("mny-", ".part", parent)
                        try {
                            var done = 0L
                            body.byteStream().use { input ->
                                tempFile.outputStream().use { output ->
                                    val buf = ByteArray(64 * 1024)
                                    var read: Int
                                    var lastPercent = -1
                                    while (input.read(buf).also { read = it } != -1) {
                                        if (cancelled) { code = 2; break }
                                        output.write(buf, 0, read)
                                        done += read
                                        if (total > 0) {
                                            val p = ((done * 100) / total).toInt()
                                            if (p != lastPercent) { lastPercent = p; onProgress(p) }
                                        }
                                    }
                                }
                            }
                            if (code == 0 && total >= 0 && done != total) code = 1
                            if (code == 0 && cancelled) code = 2
                            if (code == 0) {
                                try {
                                    Files.move(
                                        tempFile.toPath(),
                                        outFile.toPath(),
                                        StandardCopyOption.REPLACE_EXISTING,
                                        StandardCopyOption.ATOMIC_MOVE,
                                    )
                                } catch (_: AtomicMoveNotSupportedException) {
                                    Files.move(
                                        tempFile.toPath(),
                                        outFile.toPath(),
                                        StandardCopyOption.REPLACE_EXISTING,
                                    )
                                }
                            }
                        } finally {
                            if (tempFile.exists()) tempFile.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                code = if (cancelled) 2 else 1
            }
            onFinish(code)
        }.apply { isDaemon = true; start() }
    }

    fun stop() { cancelled = true }
}

/** معادل GetContentsThread: لیست بسته‌های محتوایی و دانلود تصویر جلد */
object ContentService {
    data class ContentInfo(
        val id: Int,
        val title: String,
        val filesize: Long,
        /** تاریخ افزودن به جلالی برای مقایسه با LAST_VISIT */
        val dateAdded: String,
        val packageFile: String,
    )

    private val serverFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** تبدیل تاریخ میلادی سرور به جلالی (همان فرمت LAST_VISIT) */
    private fun toJalali(dateStr: String): String = try {
        val dt = LocalDateTime.parse(dateStr.trim(), serverFormat)
        val t = "%02d:%02d:%02d".format(dt.hour, dt.minute, dt.second)
        "${PersianUtil.toJalali(dt)} $t"
    } catch (e: Exception) { dateStr }

    fun fetchContents(): List<ContentInfo> {
        val req = Request.Builder().url(Constants.GET_CONTENTS_URL).get().build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = JsonParser.parseString(resp.body?.string() ?: "[]").asJsonArray
            return arr.map { el ->
                val o = el.asJsonObject
                ContentInfo(
                    id = o.get("content_id").asInt,
                    title = o.get("title").asString,
                    filesize = o.get("filesize").asLong,
                    dateAdded = toJalali(o.get("date_added").asString),
                    packageFile = o.get("package_file").asString,
                )
            }
        }
    }

    fun downloadCover(id: Int, outFile: File) {
        try {
            val req = Request.Builder().url(Constants.DOWNLOAD_CONTENT_IMG_URL + id).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    outFile.parentFile?.mkdirs()
                    resp.body?.byteStream()?.use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        } catch (e: Exception) { /* جلد نمایش داده نمی‌شود */ }
    }
}
