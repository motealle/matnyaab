package ir.matnyaab.core.search

import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.RegexpQuery
import org.apache.lucene.util.automaton.RegExp
import java.util.regex.Matcher
import java.util.regex.Pattern

/** واژهٔ کامل یا بخشی از واژه */
enum class MatchType { WHOLE, PARTIAL, FAMILY }

/** شیوهٔ برخورد با فاصلهٔ میان واژه‌های یک عبارت */
enum class Spacing { EXACT, COLLAPSE, UNIFY }

/** همهٔ عبارت‌ها لازم‌اند یا دست‌کم یکی */
enum class Combine { ALL, ANY }

/**
 * مشخصات یک جست‌وجوی نسل دوم متن‌یاب.
 * هر «عبارت» یک یا چند واژه است؛ جست‌وجوی واژه، عبارت، چند واژه و چند عبارت همه با همین ساختار بیان می‌شوند.
 */
data class SearchSpec(
    val phrases: List<String>,
    val combine: Combine = Combine.ALL,
    val exclude: List<String> = emptyList(),
    val match: MatchType = MatchType.WHOLE,
    val spacing: Spacing = Spacing.UNIFY,
    val ignorePunct: Boolean = true,
    val normalizeLetters: Boolean = true,
    /** «جست‌وجوی دقیق»: همان شکل، همان ترتیب و همان فاصله‌گذاری */
    val strict: Boolean = false,
    /** هر عبارت همراه با مترادف‌هایش (از فهرست مترادف‌ها) جست‌وجو شود */
    val synonyms: Boolean = false,
    /** پرس‌وجوی منطقی (AND/OR/NOT/NEAR)؛ در این حالت phrases نادیده گرفته می‌شود */
    val expression: String? = null,
) {
    val effMatch get() = if (strict) MatchType.WHOLE else match
    val effSpacing get() = if (strict) Spacing.EXACT else spacing
    val effPunct get() = if (strict) false else ignorePunct
    val effNormalize get() = if (strict) false else normalizeLetters
    val cleanPhrases: List<String> get() = phrases.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

/** نتیجهٔ تطبیق در یک سند */
data class DocMatch(val perPhrase: IntArray, val total: Int, val ok: Boolean)

data class Snippet(val before: String, val match: String, val after: String, val start: Int, val end: Int, val phrase: Int)

/**
 * موتور تطبیق: الگوی دقیق هر عبارت را می‌سازد و روی متن ذخیره‌شدهٔ سند اجرا می‌کند.
 * لوسین فقط «نامزدها» را سریع پیدا می‌کند و داوری نهایی و شمارش با همین موتور است؛
 * بنابراین علائم چسبیده به واژه («جهاد،») و نیم‌فاصله‌ها دیگر مانع یافتن نمی‌شوند.
 */
class MatchEngine(val spec: SearchSpec) {

    companion object {
        private const val MARKS = "\\u064B-\\u065F\\u0670\\u0640"          // اعراب و کشیده
        private const val PUNCT = "\\p{P}\\p{S}\\u0640"                      // علائم نگارشی و نمادها
        private const val WORD_CH = "\\p{L}\\p{M}\\p{N}_"
        private val TOKEN = Regex("[@#a-zA-Z0-9؀-ۿ]+")
        /**
         * یکسان‌سازی املایی: ی/ي/ى، ک/ك، ه/ة/ۀ، الف‌های همزه‌دار و آ، و/ؤ، ئ (مسئولیت ↔ مسوولیت)،
         * و ارقام فارسی، عربی و لاتین. برای هر نویسهٔ پرس‌وجو، دستهٔ نویسه‌های هم‌ارز در متن.
         */
        private val VARIANTS: Map<Char, String> = buildMap {
            fun group(s: String) { s.forEach { c -> put(c, s) } }
            group("یيى"); group("کك"); group("هةۀ"); group("اأإٱآ"); group("وؤ")
            put('ی', "یيىئ"); put('ي', "یيىئ"); put('ى', "یيىئ")
            put('ئ', "ئیيىوؤأا"); put('و', "وؤئ"); put('ؤ', "ؤوئ")
            put('آ', "آا"); put('ا', "اأإٱآئ")
            for (i in 0..9) group("" + ('0' + i) + ('۰' + i) + ('٠' + i))
        }
        private const val WEAK = "اویآ"
        private val QUERY_MARKS = Regex("[ً-ٰٟـ]")

        /** جداکردن واژه‌ها برای حالت «چند واژه» (فاصله، ویرگول فارسی و لاتین) */
        fun splitWords(s: String): List<String> = s.split(Regex("[\\s,،؛;]+")).map { it.trim() }.filter { it.isNotEmpty() }

        /** جداکردن عبارت‌ها برای حالت «چند عبارت» (هر سطر یک عبارت؛ شماره‌گذاری ابتدای سطر حذف می‌شود) */
        fun splitLines(s: String): List<String> = s.lines()
            .map { it.replace(Regex("^\\s*[0-9۰-۹٠-٩]+\\s*[-:.)\\]]\\s*"), "").trim() }
            .filter { it.isNotEmpty() }
    }

    /** درخت پرس‌وجوی منطقی (در صورت وجود) — خطای نحوی به صورت BoolParseException بالا می‌رود */
    val ast: BoolNode? = spec.expression?.takeIf { it.isNotBlank() }?.let { BoolQuery.parse(it) }
    private val phrases: List<String> = ast?.leaves()?.map { it.trim() }?.distinct() ?: spec.cleanPhrases
    private val positive: Set<Int> = ast?.positiveLeaves()?.map { phrases.indexOf(it.trim()) }?.toSet() ?: phrases.indices.toSet()
    /** گزینه‌های هر عبارت (خودش + مترادف‌ها در صورت فعال بودن) */
    private val alts: List<List<String>> = phrases.map { p -> if (spec.synonyms && !spec.strict) Thesaurus.expand(p) else listOf(p) }
    val patterns: List<Pattern> = alts.map { a -> Pattern.compile(a.joinToString("|") { "(?:" + body(it) + ")" }, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE) }

    /** گزینه‌های جست‌وجوشدهٔ عبارت i (برای نمایش مترادف‌ها) */
    fun alternatives(i: Int): List<String> = alts[i]
    private val excludes: List<Pattern> = spec.exclude.map { it.trim() }.filter { it.isNotEmpty() }.map {
        Pattern.compile(body(it, forceWhole = true), Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
    }

    val phraseCount get() = phrases.size
    fun phrase(i: Int) = phrases[i]

    private fun charClass(c: Char): String {
        val q = { ch: Char -> if (ch.isLetterOrDigit()) ch.toString() else "\\" + ch }
        if (!spec.effNormalize) return if (c.isLetterOrDigit()) c.toString() else Pattern.quote(c.toString())
        val v = VARIANTS[c] ?: return if (c.isLetterOrDigit()) c.toString() else Pattern.quote(c.toString())
        return "[" + v.map(q).joinToString("") + "]"
    }

    private fun charRx(c: Char): String =
        if (c == 'ء' && spec.effNormalize) "ء?" else charClass(c)

    /** هم‌خانواده: حروف ریشه به همان ترتیب، با حروف عله/پیشوند/پسوند محدود میان و پیرامونشان */
    private fun familyRegex(word: String): String? {
        val root = Morph.root(word)
        if (root.length < 3) return null
        val m = if (spec.effNormalize) "[$MARKS]*" else ""
        val inner = root.map { charRx(it) + m }.joinToString("[$WEAK]{0,2}$m")
        return "[\\p{L}]{0,3}$inner[\\p{L}\\u200C]{0,7}"
    }

    private fun wordRegex(word: String): String {
        if (spec.effMatch == MatchType.FAMILY) familyRegex(word)?.let { return it }
        val w = if (spec.effNormalize) word.replace(QUERY_MARKS, "") else word
        val sb = StringBuilder()
        w.forEachIndexed { i, c ->
            if (c.isWhitespace()) return@forEachIndexed
            sb.append(charRx(c))
            if (spec.effNormalize && i < w.length - 1) sb.append("[").append(MARKS).append("]*")
        }
        if (spec.effNormalize) sb.append("[").append("\\u064B-\\u065F\\u0670").append("]*")
        return sb.toString()
    }

    private fun separator(): String {
        val space = when (spec.effSpacing) {
            Spacing.EXACT -> " "
            Spacing.COLLAPSE -> "\\s\\u00A0"
            Spacing.UNIFY -> "\\s\\u00A0\\u200C\\u200E\\u200F"
        }
        return when {
            spec.effPunct && spec.effSpacing == Spacing.EXACT -> "(?: |\\u0020?[$PUNCT]+\\u0020?)"
            spec.effPunct -> "(?:[$space]|[$PUNCT])+"
            spec.effSpacing == Spacing.EXACT -> " "
            else -> "[$space]+"
        }
    }

    private fun body(phrase: String, forceWhole: Boolean = false): String {
        val words = phrase.trim().split(Regex("[\\s\\u200C]+")).filter { it.isNotEmpty() }
        val body = when (spec.effSpacing) {
            // در فاصله‌گذاری دقیق، خودِ نیم‌فاصله‌های عبارت هم باید عیناً در متن باشد
            Spacing.EXACT -> phrase.trim().split(' ').filter { it.isNotEmpty() }
                .joinToString(separator()) { part -> part.split('‌').joinToString("\\u200C") { wordRegex(it) } }
            else -> words.joinToString(separator()) { wordRegex(it) }
        }
        val whole = forceWhole || spec.effMatch != MatchType.PARTIAL
        return if (whole) "(?<![$WORD_CH])$body(?![$WORD_CH])" else body
    }

    /** شمارش موارد هر عبارت و داوری نهایی دربارهٔ سند */
    fun evaluate(content: String): DocMatch {
        if (excludes.any { it.matcher(content).find() }) return DocMatch(IntArray(phrases.size), 0, false)
        val counts = IntArray(phrases.size) { i -> count(patterns[i].matcher(content)) }
        val tree = ast
        if (tree != null) {
            val ok = eval(tree, counts, content)
            // در حالت منطقی، شمارش فقط برای واژه‌های مثبت است و ستون عبارت‌ها همان ترتیب نوشتار را دارد
            return DocMatch(counts, counts.withIndex().filter { it.index in positive }.sumOf { it.value }, ok)
        }
        val ok = when (spec.combine) {
            Combine.ALL -> counts.isNotEmpty() && counts.all { it > 0 }
            Combine.ANY -> counts.any { it > 0 }
        }
        return DocMatch(counts, counts.sum(), ok)
    }

    private fun idx(leaf: BoolNode.Leaf) = phrases.indexOf(leaf.phrase.trim())

    private fun eval(n: BoolNode, counts: IntArray, content: String): Boolean = when (n) {
        is BoolNode.Leaf -> counts[idx(n)] > 0
        is BoolNode.And -> eval(n.a, counts, content) && eval(n.b, counts, content)
        is BoolNode.Or -> eval(n.a, counts, content) || eval(n.b, counts, content)
        is BoolNode.Not -> !eval(n.a, counts, content)
        is BoolNode.Near -> {
            val a = n.a; val b = n.b
            if (a is BoolNode.Leaf && b is BoolNode.Leaf) near(idx(a), idx(b), n.dist, content)
            else eval(n.a, counts, content) && eval(n.b, counts, content)
        }
    }

    private var startsFor: String? = null
    private var startsCache = IntArray(0)

    /** آیا موردی از عبارت a و موردی از b (به هر ترتیب) با حداکثر dist واژهٔ میانی هست؟ */
    private fun near(a: Int, b: Int, dist: Int, content: String): Boolean {
        if (startsFor !== content) {
            val m = Pattern.compile("[\\p{L}\\p{M}\\p{N}]+").matcher(content)
            val l = ArrayList<Int>(); while (m.find()) l.add(m.start())
            startsCache = l.toIntArray(); startsFor = content
        }
        val starts = startsCache
        fun wi(pos: Int): Int { var r = java.util.Arrays.binarySearch(starts, pos); if (r < 0) r = -r - 2; return r }
        fun spans(i: Int): List<IntArray> { val m = patterns[i].matcher(content); val l = mutableListOf<IntArray>(); while (m.find()) l.add(intArrayOf(wi(m.start()), wi(maxOf(m.start(), m.end() - 1)))); return l }
        val sa = spans(a); val sb = spans(b)
        for (x in sa) for (y in sb) {
            val gap = if (x[1] < y[0]) y[0] - x[1] - 1 else if (y[1] < x[0]) x[0] - y[1] - 1 else -1
            if (gap in 0..dist) return true
        }
        return false
    }

    private fun count(m: Matcher): Int { var n = 0; while (m.find()) n++; return n }

    /** بازه‌های برجسته‌سازی (مرتب و بدون هم‌پوشانی) */
    fun ranges(content: String): List<IntRange> {
        val all = mutableListOf<IntRange>()
        for (p in patterns.filterIndexed { i, _ -> i in positive }) { val m = p.matcher(content); while (m.find()) if (m.end() > m.start()) all.add(m.start() until m.end()) }
        all.sortWith(compareBy({ it.first }, { -it.last }))
        val out = mutableListOf<IntRange>()
        for (r in all) if (out.isEmpty() || r.first > out.last().last) out.add(r)
        return out
    }

    /** چند تکه از متن پیرامون موارد یافت‌شده (برای افزونهٔ ورد و خروجی‌ها) */
    fun snippets(content: String, max: Int = 6, radius: Int = 140): List<Snippet> {
        val out = mutableListOf<Snippet>()
        patterns.forEachIndexed { pi, p ->
            val m = p.matcher(content)
            while (m.find() && out.size < max * patterns.size) {
                val s = m.start(); val e = m.end()
                var a = maxOf(0, s - radius); var b = minOf(content.length, e + radius)
                while (a > 0 && a > s - radius - 40 && !content[a - 1].isWhitespace()) a--
                while (b < content.length && b < e + radius + 40 && !content[b].isWhitespace()) b++
                out.add(Snippet(content.substring(a, s), content.substring(s, e), content.substring(e, b), s, e, pi))
            }
        }
        return out.sortedBy { it.start }.take(max)
    }

    // ------------------------------------------------------------------ پرس‌وجوی نامزدها در لوسین

    /** معادل الگوی یک قطعهٔ واژه در فرهنگ واژگان فهرست (نحو RegExp لوسین) */
    private fun termRegexp(piece: String): String {
        val w = piece.lowercase().replace(QUERY_MARKS, "")
        val marks = "[ً-ٰٟـ]*"
        val sb = StringBuilder()
        val partial = spec.effMatch == MatchType.PARTIAL
        fun ch(c: Char) {
            val v = VARIANTS[c]
            if (c == 'ء' && spec.effNormalize) sb.append("ء?")
            else if (v != null && spec.effNormalize) sb.append('[').append(v).append(']')
            else if (c.isLetterOrDigit()) sb.append(c) else sb.append('\\').append(c)
            sb.append(marks)
        }
        val root = if (spec.effMatch == MatchType.FAMILY) Morph.root(w) else ""
        if (root.length >= 3) {
            sb.append(".{0,4}")
            root.forEachIndexed { i, c -> if (i > 0) sb.append("[$WEAK]{0,2}"); ch(c) }
            sb.append(".{0,9}")
            return sb.toString()
        }
        if (partial) sb.append(".*") else sb.append("[،؛؟٪٫٬\u0640]*")
        for (c in w) ch(c)
        sb.append(if (partial) ".*" else "[،؛؟٪٫٬\u0640]*")
        return sb.toString()
    }

    private fun phraseQuery(phrase: String): Query? {
        val list = if (spec.synonyms && !spec.strict) Thesaurus.expand(phrase) else listOf(phrase)
        if (list.size > 1) {
            val qs = list.map { singlePhraseQuery(it) }
            if (qs.any { it == null }) return null
            val b = BooleanQuery.Builder()
            qs.forEach { b.add(it!!, BooleanClause.Occur.SHOULD) }
            return b.setMinimumNumberShouldMatch(1).build()
        }
        return singlePhraseQuery(phrase)
    }

    private fun singlePhraseQuery(phrase: String): Query? {
        val pieces = TOKEN.findAll(phrase.lowercase()).map { it.value }.toList()
        if (pieces.isEmpty()) return null
        val b = BooleanQuery.Builder()
        pieces.distinct().forEach { b.add(RegexpQuery(Term("content", termRegexp(it)), RegExp.NONE), BooleanClause.Occur.MUST) }
        return b.build()
    }

    /** NOT در نامزدها لحاظ نمی‌شود (داوری دقیق بعداً انجام می‌شود)؛ null یعنی «بدون محدودیت» */
    private fun nodeQuery(n: BoolNode): Query? = when (n) {
        is BoolNode.Leaf -> phraseQuery(n.phrase)
        is BoolNode.Not -> null
        is BoolNode.And, is BoolNode.Near -> {
            val (a, b) = if (n is BoolNode.And) n.a to n.b else (n as BoolNode.Near).a to n.b
            val qa = nodeQuery(a); val qb = nodeQuery(b)
            if (qa == null) qb else if (qb == null) qa else BooleanQuery.Builder()
                .add(qa, BooleanClause.Occur.MUST).add(qb, BooleanClause.Occur.MUST).build()
        }
        is BoolNode.Or -> {
            val qa = nodeQuery(n.a); val qb = nodeQuery(n.b)
            if (qa == null || qb == null) null else BooleanQuery.Builder()
                .add(qa, BooleanClause.Occur.SHOULD).add(qb, BooleanClause.Occur.SHOULD).setMinimumNumberShouldMatch(1).build()
        }
    }

    /** پرس‌وجوی نامزدها: مجموعه‌ای بزرگ‌تر یا برابر با پاسخ نهایی */
    fun candidateQuery(): Query {
        ast?.let { return nodeQuery(it) ?: MatchAllDocsQuery() }
        val qs = phrases.mapNotNull { phraseQuery(it) }
        if (qs.isEmpty()) return MatchAllDocsQuery()
        val b = BooleanQuery.Builder()
        val occur = if (spec.combine == Combine.ALL) BooleanClause.Occur.MUST else BooleanClause.Occur.SHOULD
        qs.forEach { b.add(it, occur) }
        if (occur == BooleanClause.Occur.SHOULD) b.setMinimumNumberShouldMatch(1)
        return b.build()
    }
}
