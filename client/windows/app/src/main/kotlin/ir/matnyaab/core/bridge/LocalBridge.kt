package ir.matnyaab.core.bridge

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.Constants
import ir.matnyaab.core.FileEntry
import ir.matnyaab.core.IndexingService
import ir.matnyaab.core.LuceneIndexer
import ir.matnyaab.core.TikaExtractor
import ir.matnyaab.core.WhooshIndexImporter
import ir.matnyaab.core.search.Combine
import ir.matnyaab.core.search.MatchEngine
import ir.matnyaab.core.search.MatchType
import ir.matnyaab.core.search.SearchSpec
import ir.matnyaab.core.search.Spacing
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * پل محلی متن‌یاب ↔ افزونهٔ ورد.
 * - فقط روی 127.0.0.1 و با توکن تصادفی (در bridge.json پوشهٔ تنظیمات کاربر) پاسخ می‌دهد.
 * - هم در برنامهٔ باز (gui) و هم در حالت پنهان «--bridge» (headless) اجرا می‌شود؛
 *   در هر لحظه فقط یک نمونه فعال است و برنامهٔ باز، نمونهٔ پنهان را مؤدبانه خاموش می‌کند.
 */
class LocalBridge(
    private val settings: AppSettingsManager,
    private val indexer: LuceneIndexer,
    private val mode: String,
    private val host: Host? = null,
) {
    interface Host {
        fun show(query: String?)
        fun collectionsChanged()
        fun addFolder(path: String): Boolean
    }

    companion object {
        const val API = 1
        val FILE: Path get() = Constants.APP_SETTINGS_DIR.resolve("bridge.json")
        private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
        val NOTES_DIR: File get() = File(System.getProperty("user.home"), "Documents" + File.separator + "Matnyaab Notes")
        const val NOTES_NAME = "یادداشت‌های پژوهشی (ورد)"

        /** اگر پل دیگری زنده است، آدرس و توکنش را برمی‌گرداند */
        fun existing(): Pair<Int, String>? = runCatching {
            val o = JsonParser.parseString(Files.readString(FILE)).asJsonObject
            o.get("port").asInt to o.get("token").asString
        }.getOrNull()

        /** خاموش‌کردن نمونهٔ پنهانِ قبلی (اگر هست) پیش از راه‌اندازی پل برنامهٔ باز */
        fun stopHeadless() {
            val (port, token) = existing() ?: return
            runCatching {
                val c = java.net.URL("http://127.0.0.1:$port/v1/shutdown").openConnection() as java.net.HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 800; c.readTimeout = 1500
                c.setRequestProperty("X-Matnyaab-Token", token); c.doOutput = true
                c.outputStream.use { it.write("{}".toByteArray()) }
                c.responseCode
            }
        }

        /** بارگذاری همهٔ مجموعه‌های فهرست‌شده از دیسک (حالت پنهان) */
        fun loadCollections(indexer: LuceneIndexer) {
            indexer.allObjects.clear()
            Files.createDirectories(Constants.INDEX_DIR)
            Files.list(Constants.INDEX_DIR).use { s ->
                s.filter { Files.isDirectory(it) && !WhooshIndexImporter.isWhooshIndex(it) }.forEach { dir ->
                    val info = indexer.readFileInfo(dir) ?: return@forEach
                    val n = info["name"]; val p = info["path"]
                    if (n != null && p != null) indexer.loadIndexer(n, dir.toString(), p)
                }
            }
        }
    }

    private var server: HttpServer? = null
    private val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val lastHit = AtomicLong(System.currentTimeMillis())
    @Volatile var onShutdown: (() -> Unit)? = null

    fun start(): Int {
        val s = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 16)
        s.executor = Executors.newFixedThreadPool(3) { r -> Thread(r, "matnyaab-bridge").apply { isDaemon = true } }
        s.createContext("/") { ex -> handle(ex) }
        s.start()
        server = s
        val port = s.address.port
        val o = JsonObject().apply {
            addProperty("api", API); addProperty("port", port); addProperty("token", token)
            addProperty("mode", mode); addProperty("version", Constants.APP_VERSION)
            addProperty("pid", ProcessHandle.current().pid())
        }
        Files.createDirectories(Constants.APP_SETTINGS_DIR)
        val tmp = FILE.resolveSibling("bridge.json.tmp")
        Files.writeString(tmp, o.toString())
        Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        return port
    }

    fun stop() {
        server?.stop(0); server = null
        runCatching { if (existing()?.second == token) Files.deleteIfExists(FILE) }
    }

    fun idleMillis() = System.currentTimeMillis() - lastHit.get()

    // ------------------------------------------------------------------ HTTP

    private fun handle(ex: HttpExchange) {
        try {
            lastHit.set(System.currentTimeMillis())
            if (ex.requestHeaders.getFirst("X-Matnyaab-Token") != token) return send(ex, 401, err("توکن نامعتبر"))
            val body = if (ex.requestMethod == "POST") ex.requestBody.readBytes().toString(Charsets.UTF_8) else ""
            val req = runCatching { JsonParser.parseString(body.ifBlank { "{}" }).asJsonObject }.getOrDefault(JsonObject())
            val res: Any = when (ex.requestURI.path) {
                "/v1/ping" -> ping()
                "/v1/search" -> search(req)
                "/v1/excerpt" -> excerpt(req)
                "/v1/note" -> note(req)
                "/v1/add-folder" -> addFolder(req)
                "/v1/show" -> { host?.show(req.str("query")); mapOf("ok" to (host != null)) }
                "/v1/shutdown" -> { Thread { Thread.sleep(150); stop(); onShutdown?.invoke() }.start(); mapOf("ok" to true) }
                else -> return send(ex, 404, err("not found"))
            }
            send(ex, 200, gson.toJson(res))
        } catch (e: ir.matnyaab.core.search.BoolParseException) {
            send(ex, 400, err(e.message ?: "عبارت نامعتبر"))
        } catch (e: Exception) {
            send(ex, 500, err(e.message ?: e.javaClass.simpleName))
        }
    }

    private fun err(m: String) = gson.toJson(mapOf("ok" to false, "error" to m))

    private fun send(ex: HttpExchange, code: Int, json: String) {
        val b = json.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        ex.sendResponseHeaders(code, b.size.toLong())
        ex.responseBody.use { it.write(b) }
    }

    private fun JsonObject.str(k: String) = get(k)?.takeIf { !it.isJsonNull }?.asString
    private fun JsonObject.bool(k: String, d: Boolean) = get(k)?.takeIf { !it.isJsonNull }?.asBoolean ?: d

    // ------------------------------------------------------------------ عملیات

    private fun ping(): Map<String, Any?> {
        val p = ir.matnyaab.ui.Theme.PALETTES.firstOrNull { it.id == settings.get("THEME") } ?: ir.matnyaab.ui.Theme.PALETTES.first()
        return mapOf(
            "ok" to true, "api" to API, "mode" to mode, "version" to Constants.APP_VERSION,
            "collections" to indexer.allObjects.map { mapOf("name" to it.indexName, "root" to it.rootPath) },
            "theme" to mapOf("id" to p.id, "dark" to p.dark, "accent" to p.accent, "accentSoft" to p.accentSoft,
                "accentText" to p.accentText, "canvas" to p.canvas, "surface" to p.surface, "surface2" to p.surface2,
                "text" to p.text, "muted" to p.muted, "border" to p.border, "hover" to p.hover, "danger" to p.danger, "success" to p.success),
            "font" to settings.get("UI_FONT"),
        )
    }

    private fun spec(r: JsonObject): SearchSpec {
        val phrases = r.getAsJsonArray("phrases")?.map { it.asString } ?: listOfNotNull(r.str("query"))
        return SearchSpec(
            phrases = phrases,
            combine = if (r.str("combine") == "any") Combine.ANY else Combine.ALL,
            exclude = r.getAsJsonArray("exclude")?.map { it.asString } ?: emptyList(),
            match = when (r.str("match")) { "partial" -> MatchType.PARTIAL; "family" -> MatchType.FAMILY; else -> MatchType.WHOLE },
            synonyms = r.bool("synonyms", false),
            spacing = when (r.str("spacing")) { "exact" -> Spacing.EXACT; "collapse" -> Spacing.COLLAPSE; else -> Spacing.UNIFY },
            ignorePunct = r.bool("ignorePunct", true), normalizeLetters = r.bool("normalizeLetters", true),
            strict = r.bool("strict", false), expression = r.str("expression")?.takeIf { it.isNotBlank() },
        )
    }

    private fun search(r: JsonObject): Map<String, Any?> {
        val spec = spec(r)
        val engine = MatchEngine(spec)
        val limit = r.get("limit")?.asInt ?: 100
        val wanted = r.getAsJsonArray("collections")?.map { it.asString }?.toSet()
        val cols = indexer.allObjects.filter { wanted == null || it.indexName in wanted }
        // نمونهٔ مستقل از LuceneIndexer تا وضعیت جست‌وجوی برنامهٔ باز به‌هم نریزد
        val local = LuceneIndexer(settings)
        val hits = local.searchSpec(spec, cols, emptySet())
        return mapOf(
            "ok" to true,
            "phrases" to (0 until engine.phraseCount).map { engine.phrase(it) },
            "totalFiles" to hits.size, "totalMatches" to hits.sumOf { it.matches },
            "results" to hits.take(limit).map { h ->
                mapOf("path" to h.path, "name" to h.name, "collection" to h.indexName, "matches" to h.matches,
                    "perPhrase" to h.perPhrase, "size" to h.size, "time" to h.time,
                    "snippets" to engine.snippets(h.content, max = r.get("snippets")?.asInt ?: 4, radius = 160).map {
                        mapOf("before" to it.before, "match" to it.match, "after" to it.after, "start" to it.start, "end" to it.end, "phrase" to it.phrase)
                    })
            },
        )
    }

    /** متن پیرامون یک بازه از سند فهرست‌شده (برای «درج بند کامل») */
    private fun excerpt(r: JsonObject): Map<String, Any?> {
        val path = r.str("path") ?: return mapOf("ok" to false)
        val start = r.get("start")?.asInt ?: 0; val end = r.get("end")?.asInt ?: start
        val content = indexer.allObjects.asSequence().mapNotNull { storedContent(it.savePath, path) }.firstOrNull()
            ?: return mapOf("ok" to false, "error" to "این فایل در فهرست‌ها پیدا نشد.")
        // گسترش تا مرز بند (سطر خالی یا پایان سطر)
        var a = start.coerceIn(0, content.length); var b = end.coerceIn(a, content.length)
        while (a > 0 && content[a - 1] != '\n') a--
        while (b < content.length && content[b] != '\n') b++
        return mapOf("ok" to true, "text" to content.substring(a, b).trim(), "start" to a, "end" to b)
    }

    private fun storedContent(savePath: String, path: String): String? = runCatching {
        org.apache.lucene.store.FSDirectory.open(Path.of(savePath)).use { d ->
            org.apache.lucene.index.DirectoryReader.open(d).use { reader ->
                val s = org.apache.lucene.search.IndexSearcher(reader)
                val top = s.search(org.apache.lucene.search.TermQuery(org.apache.lucene.index.Term("path", path)), 1)
                top.scoreDocs.firstOrNull()?.let { s.storedFields().document(it.doc).get("content") }
            }
        }
    }.getOrNull()

    /** ذخیرهٔ یادداشت از ورد در «یادداشت‌های پژوهشی» و افزودن فوری به فهرست متن‌یاب */
    @Synchronized
    private fun note(r: JsonObject): Map<String, Any?> {
        val text = r.str("text")?.trim().orEmpty()
        if (text.isEmpty()) return mapOf("ok" to false, "error" to "متنی برای ذخیره نیست.")
        val title = (r.str("title") ?: text.take(40)).replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), " ").trim().take(60).ifBlank { "یادداشت" }
        NOTES_DIR.mkdirs()
        val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss"))
        val f = File(NOTES_DIR, "$stamp $title.txt")
        val source = r.str("source")?.let { "\n\nمنبع: $it" } ?: ""
        f.writeText("$title\n\n$text$source\n", Charsets.UTF_8)

        var info = indexer.allObjects.firstOrNull { File(it.rootPath).canonicalPath == NOTES_DIR.canonicalPath }
        val dir: Path = info?.let { Path.of(it.savePath) } ?: indexer.makeIndexDir(NOTES_DIR.absolutePath).also {
            indexer.saveFileInfo(it, NOTES_NAME, NOTES_DIR.absolutePath)
        }
        indexer.openWriter(dir).use { w ->
            indexer.addDocument(w, f.name, f.absolutePath, f.length(), f.lastModified() / 1000.0, TikaExtractor.parse(f).content ?: f.readText())
            w.commit()
        }
        indexer.saveFileList(dir, indexer.readFileList(dir) + FileEntry(f.absolutePath, f.length(), f.lastModified() / 1000.0))
        if (info == null) { indexer.loadIndexer(NOTES_NAME, dir.toString(), NOTES_DIR.absolutePath) }
        host?.collectionsChanged()
        return mapOf("ok" to true, "file" to f.absolutePath, "collection" to NOTES_NAME)
    }

    private fun addFolder(r: JsonObject): Map<String, Any?> {
        val path = r.str("path") ?: return mapOf("ok" to false)
        val dir = File(path)
        if (!dir.isDirectory) return mapOf("ok" to false, "error" to "پوشه پیدا نشد.")
        if (indexer.allObjects.any { File(it.rootPath).canonicalPath == dir.canonicalPath })
            return mapOf("ok" to true, "already" to true)
        if (host?.addFolder(path) == true) return mapOf("ok" to true, "interactive" to true)
        Thread {
            IndexingService(indexer).indexDirectory(dir.name, dir.absolutePath, emptySet(), 1, Long.MAX_VALUE, true,
                object : IndexingService.Callbacks {})
            host?.collectionsChanged()
        }.apply { isDaemon = false }.start()
        return mapOf("ok" to true, "started" to true)
    }
}
