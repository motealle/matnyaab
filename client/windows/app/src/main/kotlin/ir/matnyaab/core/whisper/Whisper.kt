package ir.matnyaab.core.whisper

import com.google.gson.JsonParser
import ir.matnyaab.core.Constants
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * بستهٔ اختیاری «گفتار به متن» (whisper.cpp + ffmpeg) و مدل‌های آن؛ همه آفلاین و روی رایانهٔ کاربر.
 * برنامهٔ پایه سبک می‌ماند: بسته و مدل جدا و با رضایت کاربر دریافت می‌شوند، با ادامهٔ دانلود و بررسی SHA-256.
 */
object Whisper {
    data class Model(val id: String, val title: String, val desc: String, val file: String, val url: String, val sha256: String, val size: Long)
    data class Release(val version: String, val url: String, val sha256: String, val size: Long, val models: List<Model>)
    data class Segment(val from: Long, val to: Long, val text: String)

    /**
     * whisper.cpp مسیرهای غیرلاتین را در ویندوز درست باز نمی‌کند؛ اگر مسیر پوشهٔ کاربر حرف فارسی داشته باشد،
     * بسته و مدل‌ها در ProgramData نگه داشته می‌شوند (فایل‌های ورودی با ffmpeg خوانده می‌شوند و مشکلی ندارند).
     */
    val dir: File by lazy {
        val d = Constants.APP_SETTINGS_DIR.resolve("whisper").toFile()
        if (d.absolutePath.all { it.code < 128 }) d
        else File(System.getenv("ProgramData") ?: "C:\\ProgramData", "Matnyaab\\whisper")
    }
    private val binDir get() = File(dir, "bin")
    val modelsDir: File get() = File(dir, "models").also { it.mkdirs() }

    val REQUIREMENTS = listOf(
        "ویندوز ۱۰ یا ۱۱ (۶۴ بیتی)؛ همه‌چیز آفلاین روی رایانهٔ شما اجرا می‌شود و صدا جایی فرستاده نمی‌شود",
        "بستهٔ اجرایی حدود ۴۵ مگابایت + یک مدل: «سریع» حدود ۱۵۰ مگابایت یا «دقیق» (پیشنهادی برای فارسی) حدود ۶۰۰ مگابایت",
        "حافظهٔ RAM: دست‌کم ۴ گیگابایت برای مدل سریع و ۸ گیگابایت برای مدل دقیق",
        "زمان: روی پردازندهٔ معمولی، هر ساعت صدا با مدل سریع حدود ۱۰ تا ۲۰ دقیقه و با مدل دقیق ۳۰ تا ۹۰ دقیقه",
        "کیفیت به وضوح صدا بستگی دارد؛ متن به‌دست‌آمده را برای نقل‌قول با صدای اصلی تطبیق دهید",
    )

    fun exe(name: String): File? = binDir.walkTopDown().maxDepth(2).firstOrNull { it.isFile && it.name.equals(name, true) }
    fun whisperExe(): File? = exe("whisper-cli.exe") ?: exe("main.exe")
    fun ffmpegExe(): File? = exe("ffmpeg.exe")
    fun installed() = whisperExe() != null
    fun models(): List<File> = modelsDir.listFiles { f -> f.isFile && f.name.endsWith(".bin") && f.length() > 10_000_000 }?.sortedBy { it.length() } ?: emptyList()

    val AUDIO = setOf("mp3", "wav", "m4a", "aac", "ogg", "oga", "opus", "flac", "wma", "amr", "mp4", "m4v", "mkv", "avi", "mov", "webm", "3gp", "mpeg", "mpg")

    private val http by lazy { OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build() }

    /** مشخصات بسته و مدل‌ها از releases.json سایت */
    fun latest(): Release? = runCatching {
        http.newCall(Request.Builder().url("${Constants.BASE_URL}/downloadFiles/releases.json?ts=${System.currentTimeMillis()}").build()).execute().use { r ->
            val o = JsonParser.parseString(r.body!!.string()).asJsonObject.getAsJsonObject("whisper") ?: return null
            fun abs(u: String) = if (u.startsWith("/")) Constants.BASE_URL + u else u
            Release(o.get("version").asString, abs(o.get("url").asString), o.get("sha256").asString, o.get("size_bytes")?.asLong ?: 0,
                o.getAsJsonArray("models")?.map { it.asJsonObject }?.map { m ->
                    Model(m.get("id").asString, m.get("title").asString, m.get("desc")?.asString ?: "", m.get("file").asString, abs(m.get("url").asString),
                        m.get("sha256")?.asString ?: "", m.get("size_bytes")?.asLong ?: 0)
                } ?: emptyList())
        }
    }.getOrNull()

    /** دانلود با قابلیت ادامه (برای اتصال‌های ناپایدار) و بررسی SHA-256 */
    fun download(url: String, sha256: String, size: Long, dest: File, progress: (Long, Long) -> Unit, cancelled: () -> Boolean) {
        dest.parentFile.mkdirs()
        val part = File(dest.path + ".part")
        var attempt = 0
        while (true) {
            try {
                val have = if (part.exists()) part.length() else 0L
                val req = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
                http.newCall(req).execute().use { r ->
                    if (r.code == 416) return@use
                    if (!r.isSuccessful) throw IllegalStateException("دریافت ناموفق بود (کد ${r.code})")
                    val append = r.code == 206 && have > 0
                    val body = r.body!!
                    val total = (if (append) have else 0L) + (body.contentLength().takeIf { it > 0 } ?: (size - have).coerceAtLeast(0))
                    RandomAccessFile(part, "rw").use { out ->
                        if (append) out.seek(have) else out.setLength(0)
                        var done = if (append) have else 0L
                        val buf = ByteArray(1 shl 16)
                        body.byteStream().use { input ->
                            while (true) {
                                if (cancelled()) throw InterruptedException("لغو شد؛ دفعهٔ بعد از همین‌جا ادامه می‌یابد.")
                                val n = input.read(buf); if (n < 0) break
                                out.write(buf, 0, n); done += n; progress(done, total)
                            }
                        }
                    }
                }
                break
            } catch (e: InterruptedException) { throw e
            } catch (e: Exception) {
                if (++attempt >= 5) throw IllegalStateException("دریافت کامل نشد (${e.message}). دوباره «دریافت» را بزنید؛ از همان‌جا ادامه می‌یابد.")
                Thread.sleep(2000L * attempt)
            }
        }
        if (sha256.isNotBlank()) {
            val md = MessageDigest.getInstance("SHA-256")
            part.inputStream().use { i -> val b = ByteArray(1 shl 16); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
            if (!md.digest().joinToString("") { "%02x".format(it) }.equals(sha256.trim(), true)) { part.delete(); throw IllegalStateException("فایل دریافتی سالم نبود؛ دوباره تلاش کنید.") }
        }
        Files.move(part.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun installPack(zip: File) {
        val stage = File(dir, "bin.installing"); stage.deleteRecursively(); stage.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val out = File(stage, e.name).canonicalFile
                if (!out.path.startsWith(stage.canonicalPath)) throw SecurityException("مسیر نامعتبر در بسته")
                if (e.isDirectory) out.mkdirs() else { out.parentFile.mkdirs(); Files.copy(z, out.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            }
        }
        if (stage.walkTopDown().none { it.name.equals("whisper-cli.exe", true) || it.name.equals("main.exe", true) }) throw IllegalStateException("بسته ناقص است.")
        binDir.deleteRecursively()
        if (!stage.renameTo(binDir)) throw IllegalStateException("نصب بسته کامل نشد")
    }

    fun uninstall() { dir.deleteRecursively() }

    // ---------------------------------------------------------------- رونویسی

    private fun run(cmd: List<String>, onLine: (String) -> Unit, cancelled: () -> Boolean): Int {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val reader = Thread { p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { onLine(it) } }.apply { isDaemon = true; start() }
        while (p.isAlive) {
            if (cancelled()) { p.descendants().forEach { it.destroyForcibly() }; p.destroyForcibly(); throw InterruptedException("لغو شد") }
            Thread.sleep(200)
        }
        reader.join(2000)
        return p.exitValue()
    }

    /**
     * فایل صوتی/تصویری ← متن با زمان‌بندی. progress: ۰..۱. language: «fa» یا «auto».
     * ابتدا با ffmpeg به WAV تک‌کاناله ۱۶ کیلوهرتز تبدیل و سپس با whisper.cpp رونویسی می‌شود.
     */
    fun transcribe(input: File, model: File, language: String = "fa", progress: (Double) -> Unit, cancelled: () -> Boolean): List<Segment> {
        val w = whisperExe() ?: throw IllegalStateException("بستهٔ گفتار به متن نصب نیست.")
        val work = File(dir, "work").also { it.mkdirs() }
        val wav = File(work, "in-${System.nanoTime()}.wav")
        val outBase = File(work, "out-${System.nanoTime()}")
        try {
            val ff = ffmpegExe()
            if (ff != null) {
                val log = StringBuilder()
                val code = run(listOf(ff.path, "-hide_banner", "-nostdin", "-y", "-i", input.path, "-vn", "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", wav.path),
                    { if (log.length < 4000) log.append(it).append('\n') }, cancelled)
                if (code != 0 || !wav.exists()) throw IllegalStateException("خواندن صدای «${input.name}» ممکن نشد (قالب پشتیبانی نمی‌شود یا فایل آسیب دیده است).")
            } else if (!input.extension.equals("wav", true)) throw IllegalStateException("برای این قالب، ffmpeg در بسته لازم است.")
            val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 8)
            val tail = ArrayDeque<String>()
            val code = run(listOf(w.path, "-m", model.path, "-f", (if (wav.exists()) wav else input).path, "-l", language, "-t", threads.toString(), "-pp", "-oj", "-of", outBase.path),
                { line ->
                    Regex("progress\\s*=\\s*(\\d+)%").find(line)?.let { progress(it.groupValues[1].toInt() / 100.0) }
                    tail.addLast(line); if (tail.size > 20) tail.removeFirst()
                }, cancelled)
            val json = File(outBase.path + ".json")
            if (code != 0 || !json.exists()) throw IllegalStateException("رونویسی «${input.name}» ناموفق بود: " + tail.takeLast(3).joinToString(" | ").take(300))
            val o = JsonParser.parseString(json.readText(Charsets.UTF_8)).asJsonObject
            return o.getAsJsonArray("transcription").map { it.asJsonObject }.map { s ->
                val off = s.getAsJsonObject("offsets")
                Segment(off.get("from").asLong, off.get("to").asLong, s.get("text").asString.trim())
            }.filter { it.text.isNotBlank() }
        } finally { wav.delete(); File(outBase.path + ".json").delete() }
    }

    fun stamp(ms: Long): String { val s = ms / 1000; return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) }
}
