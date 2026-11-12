package ir.matnyaab.core.research

import ir.matnyaab.core.SearchHit
import ir.matnyaab.core.search.MatchEngine
import ir.matnyaab.core.search.Ranking
import ir.matnyaab.util.PersianUtil
import java.io.File

/**
 * خروجی نتایج جست‌وجو همراه با متن و بافت (Word یا Excel).
 * واحد استخراج: بند، چند بند، شمار واژه یا نویسه پیش و پس از مورد یافت‌شده. موارد نزدیک در یک بافت ادغام می‌شوند.
 */
object ResultExporter {
    enum class Unit(val title: String) { PARAGRAPH("بند کامل"), PARAGRAPHS("چند بند پیرامون"), WORDS("شمار واژه پیش و پس"), CHARS("شمار نویسه پیش و پس") }
    enum class Order(val title: String) { RELEVANCE("میزان ارتباط"), NAME("نام سند"), POSITION("ترتیب در سند") }
    enum class Group(val title: String) { DOCUMENT("بر پایهٔ سند"), PHRASE("بر پایهٔ عبارت جست‌وجو") }

    data class Options(
        val unit: Unit = Unit.PARAGRAPH,
        val before: Int = 1, val after: Int = 1,      // برای چند بند: شمار بند؛ واژه/نویسه: شمار
        val highlight: Boolean = true,
        val meta: Boolean = true,                     // نام، مسیر، صفحه، شمارهٔ بند
        val matchInfo: Boolean = true,                // نوع تطبیق، امتیاز ارتباط و معنایی
        val order: Order = Order.RELEVANCE,
        val group: Group = Group.DOCUMENT,
        val maxPerDoc: Int = 50,
    )

    /** یک برش از متن سند با موارد برجسته‌اش */
    data class Excerpt(val hit: SearchHit, val text: String, val marks: List<IntRange>, val start: Int, val page: Int, val paragraph: Int,
                       val phrases: Set<Int>, val semantic: Boolean)

    fun excerpts(hits: List<SearchHit>, engine: MatchEngine?, o: Options): List<Excerpt> {
        val out = mutableListOf<Excerpt>()
        for (h in hits) {
            val c = h.content
            if (c.isEmpty()) continue
            val found: List<Pair<IntRange, Int>> = engine?.hits(c, o.maxPerDoc * 3) ?: emptyList()
            val ranges = if (found.isNotEmpty()) found
                else if (h.chunkStart >= 0 && h.chunkEnd > h.chunkStart) listOf((h.chunkStart until minOf(c.length, h.chunkEnd)) to -1)
                else emptyList()
            if (ranges.isEmpty()) continue
            // بافت هر مورد، سپس ادغام بافت‌های هم‌پوشان
            val windows = ranges.map { (r, p) -> window(c, r, o) to (r to p) }
            val merged = mutableListOf<Triple<IntRange, MutableList<IntRange>, MutableSet<Int>>>()
            for ((w, rp) in windows.sortedBy { it.first.first }) {
                val last = merged.lastOrNull()
                if (last != null && w.first <= last.first.last + 1) {
                    merged[merged.size - 1] = Triple(last.first.first..maxOf(last.first.last, w.last), last.second.apply { add(rp.first) }, last.third.apply { add(rp.second) })
                } else merged.add(Triple(w, mutableListOf(rp.first), mutableSetOf(rp.second)))
            }
            for ((w, marks, ph) in merged.take(o.maxPerDoc)) {
                val raw = c.substring(w.first, w.last + 1)
                // پاک‌سازی ابتدای برش با حفظ جای برجسته‌ها
                val lead = raw.length - raw.trimStart().length
                val text = raw.trim().replace('\u000C', '\n')
                val rel = marks.map { (it.first - w.first - lead).coerceAtLeast(0)..(it.last - w.first - lead).coerceAtMost(text.length - 1) }.filter { it.first <= it.last }
                out.add(Excerpt(h, text, if (ph == setOf(-1)) emptyList() else rel, w.first, page(c, marks.first().first), paragraph(c, marks.first().first),
                    ph.filter { it >= 0 }.toSet(), ph.contains(-1)))
            }
        }
        return when (o.order) {
            Order.RELEVANCE -> out // ترتیب نتایج جدول حفظ می‌شود (همان ارتباط)
            Order.NAME -> out.sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale("fa"))) { it.hit.name })
            Order.POSITION -> out.sortedWith(compareBy({ it.hit.path }, { it.start }))
        }
    }

    private fun window(c: String, r: IntRange, o: Options): IntRange {
        var a = r.first; var b = r.last + 1
        when (o.unit) {
            Unit.PARAGRAPH, Unit.PARAGRAPHS -> {
                val n = if (o.unit == Unit.PARAGRAPH) 0 else 1
                repeat(1 + o.before * n) { if (a > 0) a = (c.lastIndexOf('\n', a - 2).takeIf { it >= 0 }?.plus(1)) ?: 0 }
                repeat(1 + o.after * n) { if (b < c.length) b = c.indexOf('\n', b + 1).takeIf { it >= 0 } ?: c.length }
                // بندهای بسیار بلند (متن بدون شکست خط) را به اندازهٔ معقول محدود کن
                if (r.first - a > 1500) a = wordStart(c, r.first - 1200)
                if (b - r.last > 1500) b = wordEnd(c, r.last + 1200)
            }
            Unit.WORDS -> {
                var k = 0; while (a > 0 && k < o.before) { a--; if (c[a].isWhitespace() && a > 0 && !c[a - 1].isWhitespace()) k++ }
                k = 0; while (b < c.length && k < o.after) { if (c[b].isWhitespace() && b + 1 < c.length && !c[b + 1].isWhitespace()) k++; b++ }
            }
            Unit.CHARS -> { a = wordStart(c, maxOf(0, a - o.before)); b = wordEnd(c, minOf(c.length, b + o.after)) }
        }
        return a.coerceAtLeast(0) until b.coerceAtMost(c.length).coerceAtLeast(a + 1)
    }

    private fun wordStart(c: String, i: Int): Int { var a = i.coerceIn(0, c.length); while (a > 0 && !c[a - 1].isWhitespace() && i - a < 40) a--; return a }
    private fun wordEnd(c: String, i: Int): Int { var b = i.coerceIn(0, c.length); while (b < c.length && !c[b].isWhitespace() && b - i < 40) b++; return b }

    /** شمارهٔ صفحه (فقط وقتی متن نشانهٔ صفحه دارد؛ PDFهایی که در نسخهٔ ۳٫۱ به بعد فهرست شده‌اند) */
    fun page(c: String, at: Int): Int { if (c.indexOf('\u000C') < 0) return -1; var n = 1; for (i in 0 until at.coerceAtMost(c.length)) if (c[i] == '\u000C') n++; return n }

    /** شمارهٔ بند: شمار سطرهای غیرخالی پیش از این نقطه */
    fun paragraph(c: String, at: Int): Int {
        var n = 1; var nonEmpty = false
        for (i in 0 until at.coerceAtMost(c.length)) { val ch = c[i]; if (ch == '\n' || ch == '\u000C') { if (nonEmpty) n++; nonEmpty = false } else if (!ch.isWhitespace()) nonEmpty = true }
        return n
    }

    private fun fa(n: Int) = PersianUtil.toPersianNumbers(n)

    private fun kind(e: Excerpt) = if (e.semantic && e.phrases.isEmpty()) "مفهومی" else "لفظی"
    private fun place(e: Excerpt) = (if (e.page > 0) "صفحهٔ ${fa(e.page)} · " else "") + "بند ${fa(e.paragraph)}"

    fun saveDocx(file: File, title: String, phrases: List<String>, list: List<Excerpt>, o: Options) {
        val w = ReportWriter(file)
        w.title(title)
        w.subtitle("${fa(list.map { it.hit.path }.distinct().size)} سند · ${fa(list.size)} برش · واحد: ${o.unit.title}")
        fun one(e: Excerpt) {
            if (o.meta) w.note(listOfNotNull(place(e), if (o.matchInfo) kind(e) else null,
                if (o.matchInfo && e.hit.relevance >= 0) "ارتباط ${fa(e.hit.relevance)}" else null,
                if (o.matchInfo && e.hit.semantic >= 0) "معنایی ${fa(Ranking.semanticScore(e.hit.semantic))}٪" else null).joinToString(" · "))
            if (o.highlight) w.marked(e.text, e.marks, e.hit.name) else w.quote(e.text, e.hit.name)
        }
        when (o.group) {
            Group.DOCUMENT -> list.groupBy { it.hit.path }.forEach { (path, es) ->
                w.heading(1, File(path).nameWithoutExtension)
                if (o.meta) w.note(path + " · مجموعهٔ " + es.first().hit.indexName)
                es.forEach { one(it) }
            }
            Group.PHRASE -> {
                val keys = if (phrases.isEmpty()) listOf("") else phrases
                keys.forEachIndexed { i, p ->
                    val es = list.filter { i in it.phrases || (phrases.isEmpty()) }
                    if (es.isEmpty()) return@forEachIndexed
                    w.heading(1, if (p.isBlank()) "نتایج" else "«$p»")
                    es.forEach { e -> w.heading(3, e.hit.name); one(e) }
                }
                val sem = list.filter { it.phrases.isEmpty() && it.semantic }
                if (sem.isNotEmpty() && phrases.isNotEmpty()) { w.heading(1, "نتایج مفهومی"); sem.forEach { e -> w.heading(3, e.hit.name); one(e) } }
            }
        }
        w.save()
    }

    fun saveXlsx(file: File, phrases: List<String>, list: List<Excerpt>, o: Options) {
        val x = XlsxWriter()
        val head = mutableListOf("ردیف", "سند"); val widths = mutableListOf(6, 28)
        if (o.meta) { head += listOf("مسیر", "مجموعه", "صفحه", "بند"); widths += listOf(40, 16, 7, 7) }
        if (o.matchInfo) { head += listOf("نوع تطبیق", "عبارت", "ارتباط", "معنایی"); widths += listOf(10, 20, 8, 8) }
        head += "متن و بافت"; widths += 90
        x.header(head, widths)
        list.forEachIndexed { i, e ->
            val r = mutableListOf<XlsxWriter.Cell>(XlsxWriter.Cell.Num((i + 1).toDouble()), XlsxWriter.Cell.Text(e.hit.name))
            if (o.meta) r += listOf(XlsxWriter.Cell.Text(e.hit.path), XlsxWriter.Cell.Text(e.hit.indexName),
                if (e.page > 0) XlsxWriter.Cell.Num(e.page.toDouble()) else XlsxWriter.Cell.Text(""), XlsxWriter.Cell.Num(e.paragraph.toDouble()))
            if (o.matchInfo) r += listOf(XlsxWriter.Cell.Text(kind(e)), XlsxWriter.Cell.Text(e.phrases.mapNotNull { phrases.getOrNull(it) }.joinToString("، ")),
                if (e.hit.relevance >= 0) XlsxWriter.Cell.Num(e.hit.relevance.toDouble()) else XlsxWriter.Cell.Text(""),
                if (e.hit.semantic >= 0) XlsxWriter.Cell.Num(Ranking.semanticScore(e.hit.semantic).toDouble()) else XlsxWriter.Cell.Text(""))
            r += if (o.highlight) XlsxWriter.Cell.Rich(e.text, e.marks) else XlsxWriter.Cell.Text(e.text)
            x.row(r)
        }
        x.save(file)
    }
}
