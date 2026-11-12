package ir.matnyaab.core.research

import ir.matnyaab.core.IndexerInfo
import ir.matnyaab.core.LuceneIndexer
import ir.matnyaab.core.search.Morph

/** یافتهٔ منبع: سند، بازهٔ متن مطابق، درصد شباهت و برش متن اصلی */
data class SourceMatch(
    val path: String, val name: String, val collection: String,
    val similarity: Double, val start: Int, val end: Int, val excerpt: String,
)

/**
 * «این متن از کجاست؟» — یافتن منبع یک نقل‌قول یا جمله در کتابخانه، حتی با تغییرهای جزئی.
 * روش: واژه‌های متمایز (بلندتر و کمیاب‌تر) برای یافتن نامزدها؛ سپس هم‌ترازی سه‌واژه‌ای (shingle)
 * روی متن هر نامزد برای شباهت دقیق و جایگاه.
 */
object SourceFinder {
    private val WORD = Regex("[\\p{L}\\p{N}]+")
    private val STOP = setOf("و", "در", "به", "از", "که", "این", "آن", "را", "با", "برای", "است", "هم", "تا", "یا", "بر", "می", "شود", "شده", "کرد", "کند", "the", "and", "of", "to", "in", "a")

    private data class Tok(val norm: String, val start: Int, val end: Int)

    private fun tokens(s: String): List<Tok> = WORD.findAll(s).map { Tok(Morph.norm(it.value), it.range.first, it.range.last + 1) }.filter { it.norm.isNotEmpty() }.toList()

    fun find(indexer: LuceneIndexer, cols: List<IndexerInfo>, text: String, max: Int = 3): List<SourceMatch> {
        val q = tokens(text)
        if (q.size < 3) return emptyList()
        // نامزدها: تا ۸ واژهٔ بلندِ غیرعمومی، دست‌کم نیمی از آن‌ها باید باشند
        val keys = q.map { it.norm }.filter { it.length >= 3 && it !in STOP }.distinct().sortedByDescending { it.length }.take(8)
        if (keys.isEmpty()) return emptyList()
        // نامزدها با همان موتور یکسان‌ساز املا (ی/ک، همزه، اعراب)؛ BM25 اسنادِ دارای واژه‌های بیشتر را بالا می‌آورد
        val query = ir.matnyaab.core.search.MatchEngine(ir.matnyaab.core.search.SearchSpec(keys, ir.matnyaab.core.search.Combine.ANY)).candidateQuery()
        val candidates = runCatching { LuceneIndexer(indexer.settingsRef).search(query, cols, emptySet()) }.getOrDefault(emptyList())
            .sortedByDescending { it.score }.take(25)
        val qsh = shingles(q)
        return candidates.mapNotNull { h -> align(h.content, q, qsh)?.let { (sim, s, e) ->
            SourceMatch(h.path, h.name, h.indexName, sim, s, e, h.content.substring(s, e).take(1200))
        } }.filter { it.similarity >= 0.35 }.sortedByDescending { it.similarity }.take(max)
    }

    private fun shingles(t: List<Tok>): Set<String> =
        if (t.size < 3) t.map { it.norm }.toSet() else (0..t.size - 3).map { t[it].norm + " " + t[it + 1].norm + " " + t[it + 2].norm }.toSet()

    /** بهترین پنجرهٔ هم‌طول نقل‌قول در متن سند: (شباهت، آغاز، پایان) */
    private fun align(content: String, q: List<Tok>, qsh: Set<String>): Triple<Double, Int, Int>? {
        val d = tokens(content)
        if (d.size < 3) return null
        val n = q.size
        val hit = BooleanArray(d.size)
        for (i in 0..d.size - 3) if ((d[i].norm + " " + d[i + 1].norm + " " + d[i + 2].norm) in qsh) { hit[i] = true }
        // پنجرهٔ لغزان با طول n (+۲۵٪ برای جابه‌جایی‌ها)
        val w = maxOf(3, (n * 1.25).toInt())
        var best = 0; var bestI = -1; var cur = 0
        for (i in d.indices) {
            if (hit[i]) cur++
            if (i - w >= 0 && hit[i - w]) cur--
            if (cur > best) { best = cur; bestI = i }
        }
        if (bestI < 0) return null
        val sim = best.toDouble() / maxOf(1, qsh.size)
        // محدودهٔ دقیق: نخستین و آخرین تکهٔ مطابق در پنجره
        val from = maxOf(0, bestI - w + 1)
        val first = (from..bestI).first { hit[it] }
        val last = (from..bestI).last { hit[it] }
        val s = d[first].start; val e = d[minOf(d.size - 1, last + 2)].end
        return Triple(sim.coerceAtMost(1.0), s, e)
    }

    /** نسخهٔ عمومی برای آزمون */
    fun similarity(content: String, quote: String): Double {
        val q = tokens(quote); return align(content, q, shingles(q))?.first ?: 0.0
    }

    // ---------------------------------------------------------------- همانندجویی

    data class SentenceCheck(val index: Int, val sentence: String, val match: SourceMatch?)

    private val SENT = Regex("(?<=[.!؟?؛])\\s+|\\n+")

    fun sentences(text: String, minWords: Int = 8): List<String> =
        text.split(SENT).map { it.trim() }.filter { s -> WORD.findAll(s).count() >= minWords }

    /** بررسی جمله‌به‌جملهٔ یک متن در برابر کتابخانه (برای همانندجویی پیش از تحویل) */
    fun check(indexer: LuceneIndexer, cols: List<IndexerInfo>, text: String, limit: Int = 400, progress: (Int, Int) -> Unit = { _, _ -> }): List<SentenceCheck> {
        val ss = sentences(text).take(limit)
        return ss.mapIndexed { i, s ->
            progress(i + 1, ss.size)
            SentenceCheck(i, s, find(indexer, cols, s, 1).firstOrNull()?.takeIf { it.similarity >= 0.6 })
        }
    }
}
