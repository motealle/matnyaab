package ir.matnyaab.core.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.IndexerInfo
import ir.matnyaab.core.LuceneIndexer
import ir.matnyaab.core.SearchHit
import ir.matnyaab.core.research.Fish
import ir.matnyaab.core.research.ReportWriter
import ir.matnyaab.core.research.ResearchStore
import ir.matnyaab.core.research.SourceFinder
import ir.matnyaab.core.search.Combine
import ir.matnyaab.core.search.MatchEngine
import ir.matnyaab.core.search.SearchSpec
import ir.matnyaab.util.PersianUtil
import java.io.File

/**
 * «عامل» متن‌یاب: هوش مصنوعی با ابزارهای درونی برنامه (جست‌وجو، خواندن، فیش، نوشتن فایل) کار را خودش پیش می‌برد.
 * ابزارها امن‌اند: فقط خواندن کتابخانه، افزودن فیش (با راستی‌آزمایی نقل‌قول) و نوشتن فایل تازهٔ Word/Markdown
 * (بی‌آنکه فایل موجودی بازنویسی شود). سقف گام‌ها و درخواست‌ها از پیش مشخص است.
 */
class AgentRunner(
    private val indexer: LuceneIndexer,
    private val settings: AppSettingsManager,
    private val provider: AiProvider,
    private val log: (String) -> Unit,
    private val progress: (Double) -> Unit,
    private val cancelled: () -> Boolean,
    private val defaultDir: File,
    private val maxSteps: Int = 24,
) {
    private data class Doc(val id: Int, val hit: SearchHit)
    private data class Pass(val id: Int, val doc: Doc, val start: Int, val text: String)

    private val docs = mutableListOf<Doc>()
    private val passes = mutableListOf<Pass>()
    private val written = mutableListOf<File>()
    private var fishes = 0
    private val calls0 = AiClient.calls
    private fun fa(n: Int) = PersianUtil.toPersianNumbers(n)

    private val SYSTEM = """
شما «عامل پژوهشی متن‌یاب» هستید و با ابزارهای زیر روی کتابخانهٔ اسناد کاربر کار می‌کنید. در هر نوبت فقط یک ابزار را با JSON صدا بزنید.
قالب پاسخ هر نوبت دقیقاً: {"thought":"برنامهٔ کوتاه","tool":"نام","args":{...}}
ابزارها:
- search {"query":"عبارت","mode":"phrase|words|semantic","max":10} ← فهرست سندها با شناسه D و تکهٔ نمونه
- read {"doc":شمارهٔ D,"query":"عبارت برای یافتن بخش‌های مرتبط (اختیاری)"} ← تا ۴ بخش با شناسه P
- save_fish {"passage":شمارهٔ P,"title":"عنوان","quote":"عین متن از همان بخش","topic":"سرفصل","project":"نام پروژه","codes":["کد"]} ← فیش ثبت می‌شود (نقل‌قول با متن منبع سنجیده می‌شود)
- list_fishes {"project":"نام پروژه"}
- write_file {"path":"مسیر یا نام فایل .docx یا .md (اختیاری)","title":"عنوان","blocks":[{"type":"h1|h2|h3|p|quote|bullet","text":"…","passage":شمارهٔ P برای منبع (اختیاری)}]}
- finish {"summary":"گزارش کوتاه به کاربر"}
قواعد: فقط بر پایهٔ متن‌هایی که با read خوانده‌اید بنویسید و هرگز نقل‌قول نسازید؛ برای هر نقل یا ادعا passage بدهید تا پانویس منبع ساخته شود.
اگر کاربر مسیر فایل داده، همان را در write_file بدهید. کار را با کمترین نوبت لازم انجام دهید و در پایان finish بزنید. همهٔ متن‌ها فارسی.
""".trimIndent()

    fun run(instruction: String, cols: List<IndexerInfo>): MissionResult {
        val transcript = StringBuilder("دستور کاربر: «$instruction»\nمجموعه‌های موجود: ${cols.joinToString("، ") { it.indexName }}\nپروژه‌های موجود: ${ResearchStore.projects().joinToString("، ") { it.name }}\n")
        var summary = ""
        for (step in 1..maxSteps) {
            if (cancelled()) throw InterruptedException("لغو شد")
            progress(step.toDouble() / maxSteps)
            val ctx = if (transcript.length > 45_000) transcript.substring(0, 2_000) + "\n…\n" + transcript.substring(transcript.length - 40_000) else transcript.toString()
            val r = AiClient.complete(provider, SYSTEM, ctx + "\n\nنوبت ${step} از ${maxSteps}. ابزار بعدی؟", 3500, 0.1)
            val o = AiClient.json(r) as? JsonObject
            if (o == null) { transcript.append("\n[سامانه] پاسخ JSON معتبر نبود؛ دقیقاً در قالب خواسته‌شده پاسخ دهید.\n"); continue }
            val tool = o.get("tool")?.asString ?: ""
            val args = o.getAsJsonObject("args") ?: JsonObject()
            o.get("thought")?.asString?.let { log("• $it") }
            val obs = runCatching { call(tool, args, cols) }.getOrElse { "خطا: ${it.message}" }
            if (tool == "finish") { summary = args.get("summary")?.asString ?: ""; break }
            log("   ↳ ${obs.lineSequence().firstOrNull()?.take(160) ?: ""}")
            transcript.append("\n[نوبت $step] ابزار $tool ${args.toString().take(300)}\n[نتیجه]\n${obs.take(6000)}\n")
        }
        if (written.isEmpty()) {
            // اگر عامل فایلی ننوشت، گزارش کار و فیش‌ها نوشته می‌شود تا کاربر دست‌خالی نماند
            val f = unique(File(defaultDir, "گزارش عامل متن‌یاب.docx"))
            ReportWriter(f).apply { title("گزارش عامل متن‌یاب"); para(instruction); if (summary.isNotBlank()) para(summary) }.save()
            written.add(f)
        }
        if (summary.isNotBlank()) log("\n$summary")
        progress(1.0)
        return MissionResult(written.last(), fishes, AiClient.calls - calls0, docs.map { it.hit.path }.distinct().size)
    }

    private fun unique(f: File): File {
        if (!f.exists()) return f
        var i = 2
        while (true) { val g = File(f.parentFile, f.nameWithoutExtension + " ($i)." + f.extension); if (!g.exists()) return g; i++ }
    }

    private fun i(a: JsonObject, k: String) = runCatching { a.get(k).asInt }.getOrNull()
    private fun s(a: JsonObject, k: String) = runCatching { a.get(k).asString }.getOrNull()?.trim().orEmpty()

    private fun call(tool: String, a: JsonObject, cols: List<IndexerInfo>): String = when (tool) {
        "search" -> {
            val q = s(a, "query").ifBlank { throw IllegalArgumentException("query خالی است") }
            val mode = s(a, "mode")
            val spec = when (mode) {
                "words" -> SearchSpec(MatchEngine.splitWords(q), Combine.ALL, synonyms = true)
                "semantic" -> SearchSpec(listOf(q), semantic = ir.matnyaab.core.semantic.SemanticService.usable(settings), synonyms = true)
                else -> SearchSpec(listOf(q), synonyms = true)
            }
            log("جست‌وجو: «$q»")
            val hits = LuceneIndexer(settings).searchSpec(spec, cols, emptySet()).take((i(a, "max") ?: 10).coerceIn(1, 20))
            val eng = MatchEngine(spec.copy(semantic = false))
            if (hits.isEmpty()) "سندی پیدا نشد؛ عبارت دیگر یا mode=words را بیازمایید."
            else hits.joinToString("\n") { h ->
                val d = docs.firstOrNull { it.hit.path == h.path } ?: Doc(docs.size + 1, h).also { docs.add(it) }
                val sn = eng.snippets(h.content, 1, 120).firstOrNull()?.let { it.before + it.match + it.after }
                    ?: if (h.chunkStart >= 0) h.content.substring(h.chunkStart, minOf(h.content.length, h.chunkStart + 240)) else h.content.take(200)
                "D${d.id} | ${h.name} | ${h.indexName} | موارد: ${maxOf(0, h.matches)} | «${sn.replace('\n', ' ').trim()}»"
            }
        }
        "read" -> {
            val d = docs.firstOrNull { it.id == i(a, "doc") } ?: throw IllegalArgumentException("شناسهٔ سند نامعتبر؛ ابتدا search")
            val q = s(a, "query")
            val c = d.hit.content
            val spans = if (q.isNotBlank()) MatchEngine(SearchSpec(MatchEngine.splitWords(q), Combine.ANY, synonyms = true)).snippets(c, 4, 450)
                .map { (it.start - it.before.length) to (it.before + it.match + it.after) } else emptyList()
            val list = spans.ifEmpty { listOf(0 to c.take(1400)) }
            list.joinToString("\n\n") { (st, t) ->
                val p = Pass(passes.size + 1, d, st, t.trim()); passes.add(p)
                "P${p.id} (از D${d.id}، ${d.hit.name}):\n${p.text}"
            }
        }
        "save_fish" -> {
            val p = passes.firstOrNull { it.id == i(a, "passage") } ?: throw IllegalArgumentException("شناسهٔ بخش نامعتبر؛ ابتدا read")
            val quote = s(a, "quote")
            val ok = quote.length >= 8 && (p.text.contains(quote) || SourceFinder.similarity(p.text, quote) >= 0.75)
            val text = if (ok) quote else p.text.take(700)
            val project = s(a, "project").ifBlank { ResearchStore.DEFAULT_PROJECT }
            val topic = s(a, "topic")
            val pr = ResearchStore.ensureProject(project)
            if (topic.isNotBlank() && topic !in pr.topics) ResearchStore.updateProject(pr.copy(topics = pr.topics + topic))
            val st = p.start + p.text.indexOf(text).coerceAtLeast(0)
            ResearchStore.add(Fish(project = project, topic = topic, title = s(a, "title").ifBlank { text.take(40) }, text = text,
                codes = a.getAsJsonArray("codes")?.map { it.asString } ?: emptyList(), sourcePath = p.doc.hit.path, sourceName = p.doc.hit.name,
                collection = p.doc.hit.indexName, start = st, end = st + text.length, origin = "mission",
                note = if (ok) "" else "نقل‌قول پیشنهادی با متن منبع یکسان نبود؛ کل بخش ثبت شد."))
            fishes++
            if (ok) "فیش ثبت شد (نقل‌قول راستی‌آزمایی شد)." else "فیش ثبت شد ولی نقل‌قول با منبع یکسان نبود؛ کل بخش ثبت شد."
        }
        "list_fishes" -> ResearchStore.fishes(s(a, "project").ifBlank { null }).take(80).joinToString("\n") { "• ${it.title} [${it.topic}] — ${it.sourceName}" }.ifBlank { "فیشی نیست." }
        "write_file" -> {
            var path = s(a, "path")
            if (path.isBlank()) path = (s(a, "title").ifBlank { "خروجی عامل" }).replace(Regex("[\\\\/:*?\"<>|]"), " ").take(60) + ".docx"
            var f = File(path); if (!f.isAbsolute) f = File(defaultDir, path)
            if (!f.name.endsWith(".docx") && !f.name.endsWith(".md")) f = File(f.path + ".docx")
            f = unique(f)
            val w = ReportWriter(f)
            w.title(s(a, "title").ifBlank { "خروجی عامل متن‌یاب" })
            val blocks = a.getAsJsonArray("blocks") ?: JsonArray()
            for (b in blocks) {
                val o = runCatching { b.asJsonObject }.getOrNull() ?: continue
                val t = s(o, "text"); if (t.isBlank()) continue
                val src = i(o, "passage")?.let { n -> passes.firstOrNull { it.id == n } }?.let { "${it.doc.hit.name} — ${it.doc.hit.path}" }
                when (s(o, "type")) {
                    "h1" -> w.heading(1, t); "h2" -> w.heading(2, t); "h3" -> w.heading(3, t)
                    "quote" -> w.quote(t, src); "bullet" -> w.bullet(t, src)
                    else -> w.para(t, src)
                }
            }
            w.save(); written.add(f)
            log("✓ فایل نوشته شد: ${f.absolutePath}")
            "فایل ذخیره شد: ${f.absolutePath} (${fa(blocks.size())} بخش)"
        }
        "finish" -> "پایان"
        else -> "ابزار ناشناخته «$tool». ابزارهای مجاز: search, read, save_fish, list_fishes, write_file, finish"
    }
}
