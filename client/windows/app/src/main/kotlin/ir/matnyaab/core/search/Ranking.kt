package ir.matnyaab.core.search

import ir.matnyaab.core.SearchHit
import kotlin.math.ln

/** شیوه‌های مرتب‌سازی نتایج؛ پیش‌فرض «مرتبط‌ترین» (رتبه‌بندی ترکیبی) */
enum class SortMode(val title: String) {
    RELEVANCE("مرتبط‌ترین"),
    COUNT("بیشترین تکرار"),
    DENSITY("بیشترین تراکم"),
    NEWEST("جدیدترین فایل"),
    OLDEST("قدیمی‌ترین فایل"),
    NAME("نام فایل"),
}

/**
 * رتبه‌بندی ترکیبی متن‌یاب:
 *  ارتباط = Σ عبارت‌ها [ idf × اشباع تکرار BM25 با نرمال‌سازی طول ] × (پوشش عبارت‌ها) × (پاداش نام فایل)
 *  - idf: عبارت کمیاب‌تر وزن بیشتری دارد
 *  - BM25: تکرار زیاد سودمند است ولی بی‌نهایت نه؛ سند کوتاهِ متمرکز بر سند حجیم با همان تکرار برتری دارد
 *  - پوشش: در جست‌وجوی چندعبارتی، سندی که عبارت‌های بیشتری دارد بالاتر است
 *  - نام فایل: اگر عبارت در نام فایل هم باشد، نشانهٔ تمرکز سند بر موضوع است
 * خروجی به مقیاس ۰ تا ۱۰۰ نسبت به بهترین نتیجه.
 */
object Ranking {
    private const val K1 = 1.2
    private const val B = 0.75
    private val WORD = Regex("[\\p{L}\\p{N}]+")

    fun words(text: String): Int = WORD.findAll(text).count()

    data class Scored(val hit: SearchHit, val match: DocMatch)

    fun rank(items: List<Scored>, docCount: Int, engine: MatchEngine): List<SearchHit> {
        if (items.isEmpty()) return emptyList()
        val phrases = items.first().match.perPhrase.size
        val lens = items.map { maxOf(1, words(it.hit.content)) }
        val avg = lens.average().coerceAtLeast(1.0)
        val n = maxOf(docCount, items.size).toDouble()
        val idf = DoubleArray(phrases) { p ->
            val df = items.count { it.match.perPhrase[p] > 0 }.toDouble()
            ln(1.0 + (n - df + 0.5) / (df + 0.5))
        }
        val raw = items.mapIndexed { i, s ->
            val len = lens[i]
            var sc = 0.0
            for (p in 0 until phrases) {
                val tf = s.match.perPhrase[p].toDouble()
                if (tf > 0) sc += idf[p] * tf * (K1 + 1) / (tf + K1 * (1 - B + B * len / avg))
            }
            val found = s.match.perPhrase.count { it > 0 }
            val coverage = if (phrases <= 1) 1.0 else 0.4 + 0.6 * found / phrases
            val title = if (engine.patterns.any { it.matcher(s.hit.name).find() }) 1.25 else 1.0
            Triple(s, sc * coverage * title, len)
        }
        val max = raw.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
        return raw.map { (s, sc, len) ->
            s.hit.copy(
                matches = s.match.total, perPhrase = s.match.perPhrase.toList(),
                relevance = (sc / max * 100).toInt().coerceIn(if (sc > 0) 1 else 0, 100),
                density = s.match.total * 1000.0 / len, words = len,
            )
        }.let { sort(it, SortMode.RELEVANCE) }
    }

    /**
     * ادغام نتایج متنی و معنایی با «ادغام رتبه‌های متقابل» (RRF):
     * امتیاز = ۱/(۶۰+رتبهٔ متنی) + ۱/(۶۰+رتبهٔ معنایی). سندی که در هر دو فهرست بالاست، بالاتر می‌رود؛
     * سندی که واژه‌ها را ندارد ولی هم‌معناست هم (با علامت «مفهومی») پیدا می‌شود.
     */
    fun hybrid(lexical: List<SearchHit>, semantic: List<ir.matnyaab.core.semantic.SemanticHit>, fetch: (String) -> SearchHit?): List<SearchHit> {
        if (semantic.isEmpty()) return lexical
        val top = semantic.first().similarity
        // آستانه بر پایهٔ سنجش واقعی روی جمله‌های فارسی در CI: هم‌معنا ≈ ۰٫۸۳–۰٫۸۶، نامرتبط ≈ ۰٫۷۷–۰٫۸۱
        val sem = semantic.filter { it.similarity >= maxOf(0.80, top - 0.035) }.take(100)
        val semRank = sem.withIndex().associate { it.value.path to it.index }
        val semBy = sem.associateBy { it.path }
        val lexRank = lexical.withIndex().associate { it.value.path to it.index }
        val all = LinkedHashMap<String, SearchHit>()
        lexical.forEach { all[it.path] = it }
        sem.forEach { h -> if (h.path !in all) fetch(h.path)?.let { all[h.path] = it } }
        val k = 60.0
        val scored = all.values.map { h ->
            val sc = (lexRank[h.path]?.let { 1.0 / (k + it) } ?: 0.0) + (semRank[h.path]?.let { 1.0 / (k + it) } ?: 0.0)
            val sh = semBy[h.path]
            h.copy(semantic = sh?.similarity ?: -1.0, chunkStart = sh?.chunkStart ?: -1, chunkEnd = sh?.chunkEnd ?: -1,
                matches = h.matches.coerceAtLeast(0)) to sc
        }
        val max = scored.maxOf { it.second }
        return scored.map { (h, sc) -> h.copy(relevance = (sc / max * 100).toInt().coerceIn(1, 100)) }
            .sortedByDescending { it.relevance }
    }

    fun sort(hits: List<SearchHit>, mode: SortMode): List<SearchHit> = when (mode) {
        SortMode.RELEVANCE -> hits.sortedWith(compareByDescending<SearchHit> { it.relevance.takeIf { r -> r >= 0 } ?: it.score }.thenByDescending { it.matches }.thenByDescending { it.score })
        SortMode.COUNT -> hits.sortedWith(compareByDescending<SearchHit> { it.matches }.thenByDescending { it.relevance })
        SortMode.DENSITY -> hits.sortedWith(compareByDescending<SearchHit> { it.density }.thenByDescending { it.relevance })
        SortMode.NEWEST -> hits.sortedByDescending { it.time }
        SortMode.OLDEST -> hits.sortedBy { it.time }
        SortMode.NAME -> hits.sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale("fa"))) { it.name })
    }
}
