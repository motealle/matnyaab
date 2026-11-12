package ir.matnyaab.core.quran

/** نوع یافته؛ برای جلوگیری از خطا از هم جدا نگه داشته می‌شوند */
enum class QKind(val title: String) { FULL("نقل کامل"), PARTIAL("نقل بخشی"), REFERENCE("ارجاع با شماره"), TRANSLATION("ترجمه"), ALLUSION("اشارهٔ مفهومی") }

/** یک یافته در یک سند: آیه، نوع، اطمینان (۰..۱۰۰)، جای دقیق و متن شاهد */
data class QHit(val sura: Int, val aya: Int, val kind: QKind, val confidence: Int, val start: Int, val end: Int, val witness: String,
                val alternatives: Int = 0, val ayaTo: Int = aya) {
    val key get() = "$sura:$aya"
}

/**
 * تطبیق نقل عربی و ترجمه با شاخص سه‌واژه‌ای. هر دنبالهٔ پیوسته از سه‌واژه‌های مشترک (با تحمل یک واژهٔ ناهمسان)
 * یک «اجرا» است؛ پوشش آن نسبت به طول آیه نوع (کامل/بخشی) و اطمینان را تعیین می‌کند.
 */
object Matcher {
    private class Index(val n: Int, val words: List<List<String>>, val keys: List<String>) {
        val grams = HashMap<String, MutableList<Long>>()   // پیوند سه/چهارواژه ← (شمارهٔ آیه << 20) | جای واژه
        val freq = HashMap<String, Int>()                  // در چند آیهٔ مختلف آمده
        init {
            words.forEachIndexed { vi, w ->
                val seen = HashSet<String>()
                for (j in 0..w.size - n) {
                    val g = w.subList(j, j + n).joinToString(" ")
                    grams.getOrPut(g) { ArrayList(2) }.add((vi.toLong() shl 20) or j.toLong())
                    if (seen.add(g)) freq[g] = (freq[g] ?: 0) + 1
                }
            }
        }
    }

    /** واژه‌های عربی برای تطبیق: «و»ی جدا یا چسبیده نادیده گرفته می‌شود («و لم» = «ولم») */
    private fun arTokens(text: String): List<Quran.Tok> = Quran.tokens(text, Quran::skeleton).mapNotNull { t ->
        val w = if (t.w.length > 1 && t.w[0] == 'و') t.w.substring(1) else t.w
        if (w == "و" || w.isEmpty()) null else t.copy(w = w)
    }

    @Volatile private var arabic: Index? = null
    @Volatile private var trans: List<Pair<String, Index>>? = null

    fun reset() { trans = null }

    private fun arabicIndex(): Index = arabic ?: synchronized(this) {
        arabic ?: Index(3, Quran.verses.map { v -> arTokens(v.text).map { it.w } }, Quran.verses.map { it.key }).also { arabic = it }
    }

    private fun translationIndexes(): List<Pair<String, Index>> = trans ?: synchronized(this) {
        trans ?: Quran.translationFiles().mapNotNull { f ->
            runCatching {
                val m = Quran.loadTranslation(f)
                val keys = Quran.verses.map { it.key }
                f.nameWithoutExtension to Index(4, keys.map { k -> Quran.tokens(m[k] ?: "", Quran::persianWord).map { it.w } }, keys)
            }.getOrNull()
        }.also { trans = it }
    }

    private class Run(val v: Int, val docFrom: Int, var docTo: Int, val vFrom: Int, var vTo: Int, var hits: Int)

    /** اجراهای پیوسته در یک متن برای یک شاخص */
    private fun runs(toks: List<Quran.Tok>, ix: Index, maxGap: Int = 2): List<Run> {
        val done = ArrayList<Run>()
        val active = HashMap<Int, Run>()
        for (i in 0..toks.size - ix.n) {
            val sb = StringBuilder()
            for (k in 0 until ix.n) { if (k > 0) sb.append(' '); sb.append(toks[i + k].w) }
            val cands = ix.grams[sb.toString()] ?: continue
            if (cands.size > 60) continue // سه‌واژهٔ بسیار رایج (مثل «ان الله علی») به‌تنهایی نشانه نیست
            for (c in cands) {
                val v = (c shr 20).toInt(); val j = (c and 0xFFFFF).toInt()
                val r = active[v]
                if (r != null && i - r.docTo in 1..maxGap && j - r.vTo in 1..maxGap) { r.docTo = i; r.vTo = j; r.hits++ }
                else { if (r != null) done.add(r); active[v] = Run(v, i, i, j, j, 1) }
            }
            if (i % 64 == 0) active.values.removeIf { r -> if (i - r.docTo > maxGap) { done.add(r); true } else false }
        }
        done.addAll(active.values)
        return done
    }

    /**
     * یافته‌های نقل عربی و ترجمه در یک متن. اجراهای هم‌پوشان به آیه‌ای نسبت داده می‌شوند که بیشترین پوشش را دارد؛
     * شمار آیه‌های دیگرِ هم‌متن در alternatives می‌آید.
     */
    fun find(text: String, includeBasmala: Boolean = false, withTranslations: Boolean = true): List<QHit> {
        val out = ArrayList<QHit>()
        val ar = arabicIndex()
        val toks = arTokens(text)
        collect(text, toks, runs(toks, ar), ar, false, includeBasmala, out)
        if (withTranslations) for ((_, ix) in translationIndexes()) {
            val pt = Quran.tokens(text, Quran::persianWord)
            collect(text, pt, runs(pt, ix), ix, true, true, out)
        }
        return out.sortedBy { it.start }
    }

    private fun collect(text: String, toks: List<Quran.Tok>, rs: List<Run>, ix: Index, translation: Boolean, includeBasmala: Boolean, out: MutableList<QHit>) {
        // کم‌ترین طول: عربی دست‌کم ۴ واژه (دو سه‌واژهٔ پیوسته) مگر آیهٔ کامل کوتاه؛ ترجمه دست‌کم ۸ واژه یا نیمی از آیه
        data class Cand(val r: Run, val span: Int, val cover: Double)
        val cands = rs.mapNotNull { r ->
            val len = ix.words[r.v].size
            val span = r.docTo - r.docFrom + ix.n
            val vSpan = r.vTo - r.vFrom + ix.n
            val cover = vSpan.toDouble() / len.coerceAtLeast(1)
            val ok = if (translation) (span >= 8 || cover >= 0.5) && span >= ix.n + 1
                     else span >= 4 || (cover >= 0.99 && len == ix.n)
            if (ok) Cand(r, span, cover) else null
        }.sortedWith(compareByDescending<Cand> { it.span }.thenByDescending { it.cover })
        val taken = ArrayList<IntRange>()
        for (c in cands) {
            val a = c.r.docFrom; val b = c.r.docTo + ix.n - 1
            if (taken.any { a <= it.last && it.first <= b }) continue
            // آیه‌های دیگری که همین متن را دارند (ابهام)
            val alt = cands.count { o -> o !== c && o.r.docFrom == c.r.docFrom && o.r.docTo == c.r.docTo && o.r.v != c.r.v }
            val key = ix.keys[c.r.v].split(':'); val s = key[0].toInt(); val ay = key[1].toInt()
            if (!includeBasmala && !translation && s == 1 && ay == 1) { taken.add(a..b); continue }
            if (!includeBasmala && !translation && isBasmalaOnly(toks, a, b)) { taken.add(a..b); continue }
            taken.add(a..b)
            val start = toks[a].start; val end = toks[b.coerceAtMost(toks.size - 1)].end
            val kind = when { translation -> QKind.TRANSLATION; c.cover >= 0.9 -> QKind.FULL; else -> QKind.PARTIAL }
            val base = when (kind) {
                QKind.FULL -> 97.0
                QKind.PARTIAL -> 55.0 + minOf(40.0, c.span * 4.0)
                else -> 40.0 + c.cover * 55.0
            }
            val conf = (base / Math.sqrt(1.0 + alt)).toInt().coerceIn(5, 99)
            out.add(QHit(s, ay, kind, conf, start, end, text.substring(start, end).take(600), alt))
        }
    }

    private val BASMALA = listOf("بسم", "الله", "الرحمن", "الرحیم").map { Quran.skeleton(it) }
    private fun isBasmalaOnly(toks: List<Quran.Tok>, a: Int, b: Int): Boolean {
        val w = (a..b.coerceAtMost(toks.size - 1)).map { toks[it].w }
        return w.size <= 4 && w.all { it in BASMALA }
    }

    /** یافته‌های ارجاع شماره‌ای */
    fun references(text: String): List<QHit> = Quran.references(text).flatMap { r ->
        (r.from..r.to).map { a -> QHit(r.sura, a, QKind.REFERENCE, 95, r.start, r.end, text.substring(r.start, r.end), ayaTo = r.to) }
    }

    /** همهٔ یافته‌ها در یک سند */
    fun all(text: String, includeBasmala: Boolean = false): List<QHit> = (find(text, includeBasmala) + references(text)).sortedBy { it.start }
}
