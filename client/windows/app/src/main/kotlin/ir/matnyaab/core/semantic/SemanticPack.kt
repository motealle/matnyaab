package ir.matnyaab.core.semantic

import com.google.gson.JsonParser
import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * رابط موتور معنایی. پیاده‌سازی در «بستهٔ جست‌وجوی معنایی» است که جداگانه دانلود می‌شود
 * (مدل آفلاین + ONNX Runtime) و با ClassLoader جدا بارگذاری می‌شود؛ نسخهٔ پایه سبک می‌ماند.
 */
interface SemanticEngine : AutoCloseable {
    val id: String
    val dim: Int
    fun embedQuery(text: String): FloatArray
    fun embedPassages(texts: List<String>): List<FloatArray>
}

/** نصب، بارگذاری و حذف بستهٔ جست‌وجوی معنایی (کاملاً اختیاری) */
object SemanticPack {
    const val API = 1
    private const val ENGINE_CLASS = "ir.matnyaab.semantic.E5Engine"

    val dir: File get() = Constants.APP_SETTINGS_DIR.resolve("semantic").toFile()
    private val manifestFile get() = File(dir, "pack.json")

    data class Release(val version: String, val url: String, val sha256: String, val sizeBytes: Long)
    data class Requirements(val downloadMb: Int, val diskMbPer1000Files: Int)

    /** پیشنهادهای سخت‌افزاری که پیش از فعال‌سازی به کاربر گفته می‌شود */
    val REQUIREMENTS = listOf(
        "ویندوز ۱۰ یا ۱۱ (۶۴ بیتی)",
        "حدود ۱۵۰ مگابایت دانلود یک‌باره (مدل آفلاین؛ متن اسناد از رایانهٔ شما بیرون نمی‌رود)",
        "حافظهٔ RAM: دست‌کم ۴ گیگابایت (۸ گیگابایت پیشنهادی)",
        "پردازنده: ۴ هسته یا بیشتر پیشنهاد می‌شود؛ روی رایانه‌های ضعیف‌تر هم کار می‌کند ولی آماده‌سازی کندتر است",
        "فضای دیسک: حدود ۳۰ مگابایت برای هر ۱۰۰۰ سند (بسته به حجم متن‌ها)",
        "آماده‌سازی نخستین بار (یک‌باره، در پس‌زمینه): از چند دقیقه تا چند ساعت برای کتابخانه‌های بزرگ؛ در این مدت برنامه را عادی به کار ببرید",
    )

    fun installed(): Boolean = manifestFile.exists() && File(dir, "lib").isDirectory
    fun installedVersion(): String = runCatching { JsonParser.parseString(manifestFile.readText()).asJsonObject.get("version").asString }.getOrDefault("")

    fun enabled(settings: AppSettingsManager) = settings.get("FF_SEMANTIC") == "1"

    private val http by lazy { OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build() }

    /** مشخصات بستهٔ تازه از releases.json سایت */
    fun latest(): Release? = runCatching {
        http.newCall(Request.Builder().url("${Constants.BASE_URL}/downloadFiles/releases.json?ts=${System.currentTimeMillis()}").build()).execute().use { r ->
            val o = JsonParser.parseString(r.body!!.string()).asJsonObject.getAsJsonObject("semantic") ?: return null
            var url = o.get("url").asString
            if (url.startsWith("/")) url = Constants.BASE_URL + url
            Release(o.get("version").asString, url, o.get("sha256").asString, o.get("size_bytes")?.asLong ?: 0)
        }
    }.getOrNull()

    /** دانلود با پیشرفت، بررسی SHA-256 و نصب اتمی */
    fun download(rel: Release, progress: (Long, Long) -> Unit, cancelled: () -> Boolean = { false }) {
        val tmp = File.createTempFile("matnyaab-semantic", ".zip")
        try {
            http.newCall(Request.Builder().url(rel.url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("دریافت ناموفق بود (کد ${r.code})")
                val body = r.body!!
                val total = body.contentLength().takeIf { it > 0 } ?: rel.sizeBytes
                val md = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(1 shl 16); var done = 0L
                        while (true) {
                            if (cancelled()) throw InterruptedException("لغو شد")
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); md.update(buf, 0, n); done += n
                            progress(done, total)
                        }
                    }
                }
                val hash = md.digest().joinToString("") { "%02x".format(it) }
                if (rel.sha256.isNotBlank() && !hash.equals(rel.sha256.trim(), true)) throw IllegalStateException("فایل دریافتی سالم نبود؛ دوباره تلاش کنید.")
            }
            installFrom(tmp)
        } finally { tmp.delete() }
    }

    /** نصب از فایل zip (برای آزمون و نصب دستی) */
    fun installFrom(zip: File) {
        val stage = File(dir.parentFile, "semantic.installing")
        stage.deleteRecursively(); stage.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val out = File(stage, e.name).canonicalFile
                if (!out.path.startsWith(stage.canonicalPath)) throw SecurityException("مسیر نامعتبر در بسته")
                if (e.isDirectory) out.mkdirs() else { out.parentFile.mkdirs(); Files.copy(z, out.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            }
        }
        val m = JsonParser.parseString(File(stage, "pack.json").readText()).asJsonObject
        if ((m.get("api")?.asInt ?: 0) != API) throw IllegalStateException("این بسته با این نسخهٔ متن‌یاب سازگار نیست؛ متن‌یاب را به‌روز کنید.")
        unload()
        val old = File(dir.parentFile, "semantic.old")
        old.deleteRecursively()
        if (dir.exists() && !dir.renameTo(old)) dir.deleteRecursively()
        if (!stage.renameTo(dir)) throw IllegalStateException("نصب بسته کامل نشد")
        old.deleteRecursively()
    }

    fun uninstall() {
        unload()
        dir.deleteRecursively()
    }

    // ---------------------------------------------------------------- بارگذاری

    @Volatile private var engine: SemanticEngine? = null
    private var loader: URLClassLoader? = null

    @Synchronized
    fun engine(): SemanticEngine {
        engine?.let { return it }
        if (!installed()) throw IllegalStateException("بستهٔ جست‌وجوی معنایی نصب نیست.")
        val jars = File(dir, "lib").listFiles { f -> f.name.endsWith(".jar") }!!.map { it.toURI().toURL() }.toTypedArray()
        val cl = URLClassLoader(jars, SemanticPack::class.java.classLoader)
        val e = cl.loadClass(ENGINE_CLASS).getConstructor(File::class.java).newInstance(dir) as SemanticEngine
        loader = cl; engine = e
        return e
    }

    @Synchronized
    fun unload() {
        runCatching { engine?.close() }
        engine = null
        runCatching { loader?.close() }
        loader = null
    }

    /** آزمون خودکار در CI: نصب از zip، بارگذاری، و سنجش شباهت چند جملهٔ فارسی */
    fun selfTest(zip: File): Map<String, Any> {
        installFrom(zip)
        val e = engine()
        fun cos(a: FloatArray, b: FloatArray) = a.indices.sumOf { (a[it] * b[it]).toDouble() }
        val q = e.embedQuery("خودسازی")
        val p = e.embedPassages(listOf("تزکیه نفس و مبارزه با هواهای نفسانی راه رسیدن به کمال است.", "قیمت دلار و نفت در بازار امروز بالا رفت."))
        return mapOf("ok" to (cos(q, p[0]) > cos(q, p[1])), "related" to cos(q, p[0]), "unrelated" to cos(q, p[1]), "dim" to e.dim, "id" to e.id)
    }
}
