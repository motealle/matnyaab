package ir.matnyaab.core.quran

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ir.matnyaab.core.IndexerInfo
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.MultiBits
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * پیمایش کتابخانه برای کشف‌الآیات، با حافظهٔ نهان کنار هر فهرست (‎<savePath>.quran.json‎):
 * هر سند فقط وقتی تغییر کرده یا ترجمهٔ تازه‌ای افزوده شده دوباره بررسی می‌شود.
 */
object Scanner {
    data class DocHits(val path: String, val name: String, val collection: String, val time: Double, val hits: List<QHit>)
    private data class Cache(val version: Int, val translations: String, val docs: MutableMap<String, Pair<Double, List<QHit>>>)

    private const val VERSION = 1
    private val gson = Gson()

    private fun cacheFile(c: IndexerInfo) = File(c.savePath.trimEnd('/', '\\') + ".quran.json")
    private fun signature() = Quran.translationFiles().joinToString(",") { it.name + ":" + it.length() }

    private fun load(c: IndexerInfo): Cache? = runCatching {
        val f = cacheFile(c); if (!f.exists()) return null
        val t = object : TypeToken<Cache>() {}.type
        gson.fromJson<Cache>(f.readText(Charsets.UTF_8), t)?.takeIf { it.version == VERSION && it.translations == signature() }
    }.getOrNull()

    /** متن سند از فهرست (برای پیش‌نمایش جای یافته) */
    fun content(cols: List<IndexerInfo>, path: String): String? {
        for (c in cols) {
            val dir = Path.of(c.savePath); if (!Files.isDirectory(dir)) continue
            val r = runCatching {
                FSDirectory.open(dir).use { d -> DirectoryReader.open(d).use { r ->
                    val s = org.apache.lucene.search.IndexSearcher(r)
                    val td = s.search(org.apache.lucene.search.TermQuery(org.apache.lucene.index.Term("path", path)), 1)
                    td.scoreDocs.firstOrNull()?.let { r.storedFields().document(it.doc).get("content") }
                } }
            }.getOrNull()
            if (r != null) return r
        }
        return null
    }

    /**
     * همهٔ یافته‌ها در مجموعه‌های انتخابی. progress(انجام‌شده، کل، نام سند).
     * نتیجه‌ها برای هر مجموعه ذخیره می‌شوند تا بار بعد فوری باشد.
     */
    fun scan(cols: List<IndexerInfo>, includeBasmala: Boolean, stop: AtomicBoolean, progress: (Int, Int, String) -> Unit): List<DocHits> {
        val out = ArrayList<DocHits>()
        val total = cols.sumOf { c -> runCatching { FSDirectory.open(Path.of(c.savePath)).use { d -> DirectoryReader.open(d).use { it.numDocs() } } }.getOrDefault(0) }
        var done = 0
        for (c in cols) {
            if (stop.get()) break
            val dir = Path.of(c.savePath); if (!Files.isDirectory(dir)) continue
            val old = load(c)
            val cache = Cache(VERSION, signature(), HashMap())
            runCatching {
                FSDirectory.open(dir).use { d -> DirectoryReader.open(d).use { r ->
                    val sf = r.storedFields(); val live = MultiBits.getLiveDocs(r)
                    for (i in 0 until r.maxDoc()) {
                        if (stop.get()) break
                        if (live != null && !live.get(i)) continue
                        val meta = sf.document(i, setOf("path", "name", "time"))
                        val path = meta.get("path") ?: continue
                        val time = meta.get("time")?.toDoubleOrNull() ?: 0.0
                        val prev = old?.docs?.get(path)
                        val hits = if (prev != null && prev.first == time) prev.second
                            else Matcher.all(sf.document(i, setOf("content")).get("content") ?: "", true)
                        cache.docs[path] = time to hits
                        val shown = if (includeBasmala) hits else hits.filterNot { it.sura == 1 && it.aya == 1 && it.kind != QKind.REFERENCE }
                        if (shown.isNotEmpty()) out.add(DocHits(path, meta.get("name") ?: File(path).name, c.indexName, time, shown))
                        done++
                        if (done % 10 == 0) progress(done, total, File(path).name)
                    }
                } }
            }
            if (!stop.get()) runCatching { cacheFile(c).writeText(gson.toJson(cache), Charsets.UTF_8) }
        }
        progress(done, total, "")
        return out
    }

    /** شمار رخداد هر آیه در کل نتیجه: کلید «سوره:آیه» ← (شمار سند، شمار رخداد) */
    fun frequency(list: List<DocHits>): List<Triple<String, Int, Int>> {
        val docs = HashMap<String, MutableSet<String>>(); val occ = HashMap<String, Int>()
        list.forEach { d -> d.hits.forEach { h -> docs.getOrPut(h.key) { HashSet() }.add(d.path); occ[h.key] = (occ[h.key] ?: 0) + 1 } }
        return occ.keys.map { Triple(it, docs[it]!!.size, occ[it]!!) }.sortedWith(compareByDescending<Triple<String, Int, Int>> { it.third }.thenBy { it.first.split(':')[0].toInt() * 1000 + it.first.split(':')[1].toInt() })
    }
}
