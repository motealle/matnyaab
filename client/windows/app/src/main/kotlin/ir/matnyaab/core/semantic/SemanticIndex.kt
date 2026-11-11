package ir.matnyaab.core.semantic

import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.IndexerInfo
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.KnnFloatVectorField
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.KnnFloatVectorQuery
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** یک تکهٔ متن (برای بردارسازی) با جایگاهش در متن سند */
data class Chunk(val start: Int, val end: Int, val text: String)

/** نتیجهٔ معنایی برای یک سند: بیشترین شباهت و بهترین تکه */
data class SemanticHit(val path: String, val similarity: Double, val chunkStart: Int, val chunkEnd: Int, val chunks: Int)

/**
 * فهرست برداری کنار هر مجموعه (پوشهٔ «<فهرست>.semantic»): هر تکهٔ ~۲۰۰ واژه‌ای یک بردار.
 * فهرست اصلی دست نمی‌خورد؛ این پوشه هر وقت حذف شود از نو ساخته می‌شود.
 */
object SemanticIndex {
    private const val WORDS = 200
    private const val OVERLAP = 40
    private const val MAX_CHUNKS = 1500
    private val WORD = Regex("\\S+")

    fun vecDir(info: IndexerInfo): Path = Path.of(info.savePath + ".semantic")

    fun chunks(content: String): List<Chunk> {
        val words = WORD.findAll(content).map { it.range }.toList()
        if (words.isEmpty()) return emptyList()
        val out = mutableListOf<Chunk>()
        var i = 0
        while (i < words.size && out.size < MAX_CHUNKS) {
            val j = minOf(words.size, i + WORDS)
            val s = words[i].first; val e = words[j - 1].last + 1
            if (j - i >= 12 || out.isEmpty()) out.add(Chunk(s, e, content.substring(s, e)))
            if (j == words.size) break
            i = j - OVERLAP
        }
        return out
    }

    /** وضعیت آماده‌سازی: [اسناد آماده، کل اسناد] */
    fun progress(cols: List<IndexerInfo>): Pair<Int, Int> {
        var done = 0; var total = 0
        for (c in cols) {
            total += runCatching { FSDirectory.open(Path.of(c.savePath)).use { d -> DirectoryReader.open(d).use { it.numDocs() } } }.getOrDefault(0)
            done += runCatching { embeddedPaths(c).size }.getOrDefault(0)
        }
        return done to total
    }

    private fun embeddedPaths(c: IndexerInfo): Map<String, Double> {
        val dir = vecDir(c)
        if (!Files.isDirectory(dir)) return emptyMap()
        val out = HashMap<String, Double>()
        FSDirectory.open(dir).use { d ->
            if (!DirectoryReader.indexExists(d)) return emptyMap()
            DirectoryReader.open(d).use { r ->
                val sf = r.storedFields()
                val live = org.apache.lucene.index.MultiBits.getLiveDocs(r)
                for (i in 0 until r.maxDoc()) {
                    if (live != null && !live.get(i)) continue
                    val doc = sf.document(i, setOf("path", "time"))
                    out[doc.get("path")] = doc.getField("time")?.numericValue()?.toDouble() ?: 0.0
                }
            }
        }
        return out
    }

    /**
     * همگام‌سازی برداری یک مجموعه با فهرست اصلی (افزودن اسناد تازه/تغییرکرده، حذف حذف‌شده‌ها).
     * قابل توقف و ازسرگیری؛ هر چند صد تکه یک بار ذخیره می‌شود.
     */
    fun sync(c: IndexerInfo, engine: SemanticEngine, stop: AtomicBoolean, onDoc: () -> Unit) {
        val dir = vecDir(c)
        Files.createDirectories(dir)
        val have = runCatching { embeddedPaths(c) }.getOrDefault(emptyMap())
        val main = HashMap<String, Pair<Double, Int>>()
        FSDirectory.open(Path.of(c.savePath)).use { d ->
            DirectoryReader.open(d).use { r ->
                val sf = r.storedFields()
                val live = org.apache.lucene.index.MultiBits.getLiveDocs(r)
                for (i in 0 until r.maxDoc()) {
                    if (live != null && !live.get(i)) continue
                    val doc = sf.document(i, setOf("path", "time"))
                    main[doc.get("path")] = (doc.getField("time")?.numericValue()?.toDouble() ?: 0.0) to i
                }
            }
        }
        FSDirectory.open(dir).use { vd ->
            IndexWriter(vd, IndexWriterConfig().setRAMBufferSizeMB(64.0)).use { w ->
                // حذف‌شده‌ها و تغییرکرده‌ها
                for ((p, t) in have) { val m = main[p]; if (m == null || m.first != t) w.deleteDocuments(Term("path", p)) }
                w.commit()
                val todo = main.filter { (p, m) -> have[p] != m.first }
                if (todo.isEmpty()) return
                FSDirectory.open(Path.of(c.savePath)).use { d ->
                    DirectoryReader.open(d).use { r ->
                        val sf = r.storedFields()
                        var pending = 0
                        for ((path, m) in todo) {
                            if (stop.get()) break
                            val content = sf.document(m.second, setOf("content")).get("content") ?: ""
                            val cs = chunks(content)
                            if (cs.isNotEmpty()) {
                                val docs = mutableListOf<Document>()
                                for (batch in cs.chunked(8)) {
                                    if (stop.get()) break
                                    val vecs = engine.embedPassages(batch.map { it.text })
                                    batch.forEachIndexed { k, ch ->
                                        docs.add(Document().apply {
                                            add(StringField("path", path, Field.Store.YES))
                                            add(StoredField("time", m.first))
                                            add(StoredField("s", ch.start)); add(StoredField("e", ch.end))
                                            add(KnnFloatVectorField("v", vecs[k], VectorSimilarityFunction.DOT_PRODUCT))
                                        })
                                    }
                                }
                                if (stop.get()) break
                                // همهٔ تکه‌های یک سند با هم افزوده می‌شوند تا سند نیمه‌کاره نماند
                                w.addDocuments(docs)
                                pending += docs.size
                            } else {
                                w.addDocument(Document().apply {
                                    add(StringField("path", path, Field.Store.YES)); add(StoredField("time", m.first))
                                    add(StoredField("s", 0)); add(StoredField("e", 0))
                                })
                            }
                            onDoc()
                            if (pending >= 400) { w.commit(); pending = 0 }
                        }
                    }
                }
                w.commit()
            }
        }
    }

    /** جست‌وجوی برداری در یک مجموعه؛ سندها با بیشترین شباهت تکه‌هایشان */
    fun search(c: IndexerInfo, query: FloatArray, k: Int = 300): List<SemanticHit> {
        val dir = vecDir(c)
        if (!Files.isDirectory(dir)) return emptyList()
        return runCatching {
            FSDirectory.open(dir).use { d ->
                DirectoryReader.open(d).use { r ->
                    val s = IndexSearcher(r)
                    val top = s.search(KnnFloatVectorQuery("v", query, k), k)
                    val sf = r.storedFields()
                    val best = LinkedHashMap<String, SemanticHit>()
                    val counts = HashMap<String, Int>()
                    for (sd in top.scoreDocs) {
                        val doc = sf.document(sd.doc, setOf("path", "s", "e"))
                        val p = doc.get("path")
                        val sim = 2.0 * sd.score - 1.0   // DOT_PRODUCT روی بردارهای واحد: score = (1 + cos) / 2
                        counts[p] = (counts[p] ?: 0) + 1
                        if (best[p] == null || best[p]!!.similarity < sim)
                            best[p] = SemanticHit(p, sim, doc.getField("s").numericValue().toInt(), doc.getField("e").numericValue().toInt(), 0)
                    }
                    best.values.map { it.copy(chunks = counts[it.path] ?: 1) }
                }
            }
        }.getOrDefault(emptyList())
    }

    /** پاک‌کردن پوشه‌های برداری مجموعه‌هایی که دیگر وجود ندارند */
    fun cleanupOrphans(indexDir: Path) {
        runCatching {
            Files.list(indexDir).use { s ->
                s.filter { it.fileName.toString().endsWith(".semantic") }.forEach { v ->
                    val main = indexDir.resolve(v.fileName.toString().removeSuffix(".semantic"))
                    if (!Files.isDirectory(main)) v.toFile().deleteRecursively()
                }
            }
        }
    }

    fun deleteAll(cols: List<IndexerInfo>) = cols.forEach { vecDir(it).toFile().deleteRecursively() }
}

/**
 * سرویس پس‌زمینهٔ جست‌وجوی معنایی: موتور را بارگذاری و بردارها را آماده می‌کند.
 * فقط وقتی پرچم FF_SEMANTIC روشن و بسته نصب باشد کار می‌کند.
 */
object SemanticService {
    @Volatile var status: String = ""; private set
    @Volatile var ready = false; private set
    @Volatile var done = 0; private set
    @Volatile var total = 0; private set
    private val stop = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    var listener: (() -> Unit)? = null

    fun active(settings: AppSettingsManager) = SemanticPack.enabled(settings) && SemanticPack.installed()

    /** آماده برای جست‌وجو؟ (موتور بارگذاری‌شده و دست‌کم بخشی از اسناد بردار دارند) */
    fun usable(settings: AppSettingsManager) = active(settings) && ready && done > 0

    @Synchronized
    fun start(settings: AppSettingsManager, cols: () -> List<IndexerInfo>) {
        if (!active(settings) || worker?.isAlive == true) return
        stop.set(false)
        worker = Thread {
            try {
                status = "بارگذاری مدل معنایی…"; listener?.invoke()
                val engine = SemanticPack.engine()
                ready = true
                val list = cols()
                ir.matnyaab.core.Constants.INDEX_DIR.let { SemanticIndex.cleanupOrphans(it) }
                val (d, t) = SemanticIndex.progress(list); done = d; total = t
                status = "آماده‌سازی معنایی"; listener?.invoke()
                for (c in list) {
                    if (stop.get()) break
                    try { SemanticIndex.sync(c, engine, stop) { done++; if (done % 5 == 0) listener?.invoke() } }
                    catch (e: org.apache.lucene.store.LockObtainFailedException) { System.err.println("semantic: ${c.indexName} busy, later") }
                }
                val (d2, t2) = SemanticIndex.progress(cols()); done = d2; total = t2
                status = if (stop.get()) "متوقف شد" else "آماده"
            } catch (e: Throwable) {
                status = "خطا: " + (e.message ?: e.javaClass.simpleName)
                System.err.println("semantic: $e")
            } finally { listener?.invoke() }
        }.apply { isDaemon = true; priority = Thread.MIN_PRIORITY; name = "matnyaab-semantic" }
        worker!!.start()
    }

    fun stop() { stop.set(true) }

    /** آزادسازی کامل (پس از خاموش‌کردن یا حذف بسته) */
    fun shutdown() { stop(); worker?.join(5000); SemanticPack.unload(); ready = false; done = 0; total = 0; status = "" }

    fun percent(): Int = if (total <= 0) 0 else (done * 100 / total).coerceIn(0, 100)

    /** جست‌وجوی معنایی در مجموعه‌های انتخاب‌شده */
    fun search(query: String, cols: List<IndexerInfo>): List<SemanticHit> {
        val q = SemanticPack.engine().embedQuery(query)
        return cols.flatMap { SemanticIndex.search(it, q) }.sortedByDescending { it.similarity }
    }

}
