package ir.matnyaab.core.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.IndexerInfo
import ir.matnyaab.core.LuceneIndexer
import ir.matnyaab.core.research.Fish
import ir.matnyaab.core.research.ReportWriter
import ir.matnyaab.core.research.ResearchStore
import ir.matnyaab.core.search.Combine
import ir.matnyaab.core.search.MatchEngine
import ir.matnyaab.core.search.Morph
import ir.matnyaab.core.search.SearchSpec
import ir.matnyaab.util.PersianUtil
import java.io.File
import java.time.LocalDateTime

/** سناریوهای پرکاربرد پژوهشی که دستیار متن‌یاب از ابتدا تا فایل نهایی انجام می‌دهد */
enum class Scenario(val title: String, val desc: String, val needsTopic: Boolean = true, val retrieves: Boolean = true) {
    FISH("فیش‌برداری موضوعی", "همهٔ بخش‌های مرتبط با موضوع را از منابع پیدا، با عنوان و کد فیش می‌کند و زیر سرفصل‌ها می‌چیند."),
    GROUNDED("کدگذاری داده‌بنیاد (گراندد تئوری)", "فیش‌ها با کدهای باز ← مفاهیم ← مقوله‌ها (کدگذاری محوری) و پیشنهاد مقولهٔ هسته، همراه با جدول کدگذاری."),
    THEMATIC("تحلیل مضمون", "مضامین پایه، سازمان‌دهنده و فراگیر با شواهد متنی هر مضمون."),
    REVIEW("پیشینه و مرور منابع", "دیدگاه هر منبع دربارهٔ موضوع به‌همراه جمع‌بندی پیشینه با ارجاع دقیق."),
    VIEWS("گردآوری و مقایسهٔ دیدگاه‌ها", "دیدگاه‌های مختلف دربارهٔ موضوع، دسته‌بندی و مقایسه، با متن اصلی و منبع هر دیدگاه."),
    ANSWER("پاسخ مستند به یک پرسش", "پاسخ تحلیلی فقط بر پایهٔ منابع شما، با ارجاع به جای دقیق هر ادعا."),
    OUTLINE("طرح کتاب یا مقاله از منابع", "فهرست فصل‌ها و بخش‌ها همراه با فیش‌های پشتیبان هر بخش؛ پروژهٔ کتاب را آماده می‌کند."),
    CHAPTER("نگارش پیش‌نویس از فیش‌های پروژه", "از فیش‌های یک پروژه (یا یک فصل)، متن پیوسته با پانویس منابع می‌نویسد.", needsTopic = false, retrieves = false),
}

data class MissionSpec(
    val scenario: Scenario,
    val topic: String,
    val goal: String = "",
    val collections: List<String> = emptyList(),
    val project: String = "",
    val chapter: String = "",
    val output: File,
    val maxDocs: Int = 30,
    val extra: String = "",
)

data class MissionResult(val file: File, val fishes: Int, val calls: Int, val sources: Int)

/** تکهٔ متن بازیابی‌شده برای مدل (با منبع دقیق) */
data class Passage(val n: Int, val path: String, val name: String, val collection: String, val start: Int, val end: Int, val text: String)

/**
 * اجرای مأموریت پژوهشی چندمرحله‌ای با ابزارهای داخلی متن‌یاب:
 * ۱) گسترش واژگان موضوع (مدل) ۲) بازیابی دقیق + مترادف + معنایی (موتور متن‌یاب) ۳) فیش‌برداری با نقل‌قول عینِ
 * بررسی‌شده (مدل + راستی‌آزمایی متن‌یاب) ۴) تحلیل ویژهٔ سناریو (مدل) ۵) نوشتن Word/Markdown با پانویس و فهرست منابع
 * ۶) ذخیرهٔ فیش‌ها در پروژه. مدل هرگز نقل‌قول نمی‌سازد: هر نقل‌قول با متن منبع تطبیق داده می‌شود.
 */
class MissionRunner(
    private val indexer: LuceneIndexer,
    private val settings: AppSettingsManager,
    private val provider: AiProvider,
    private val log: (String) -> Unit,
    private val progress: (Double) -> Unit,
    private val cancelled: () -> Boolean,
) {
    private val calls0 = AiClient.calls

    private fun check() { if (cancelled()) throw InterruptedException("مأموریت لغو شد") }

    internal fun ask(system: String, user: String, max: Int = 3000): String { check(); return AiClient.complete(provider, system, user, max) }

    internal val SYS = "شما دستیار پژوهشی دقیق و امین هستید که فقط بر پایهٔ متن‌های داده‌شده کار می‌کنید. " +
        "هرگز اطلاعات یا نقل‌قول از خودتان نسازید. پاسخ را دقیقاً در قالب JSON خواسته‌شده و به فارسی بدهید."

    fun estimate(spec: MissionSpec): Int = when {
        !spec.scenario.retrieves -> 2 + ResearchStore.fishes(spec.project.ifBlank { null }).size / 25
        else -> 3 + (minOf(spec.maxDocs * 3, 90) + 5) / 6
    }

    fun run(spec: MissionSpec, cols: List<IndexerInfo>): MissionResult {
        val project = spec.project.ifBlank { spec.topic.take(60).ifBlank { ResearchStore.DEFAULT_PROJECT } }
        val fishes: List<Fish>
        var terms = emptyList<String>()
        var passages = emptyList<Passage>()
        if (spec.scenario.retrieves) {
            // ۱) گسترش واژگان
            log("۱/۵ یافتن واژه‌ها و عبارت‌های هم‌معنای «${spec.topic}»…"); progress(0.03)
            terms = expand(spec)
            log("   عبارت‌های جست‌وجو: " + (listOf(spec.topic) + terms).joinToString("، "))
            // ۲) بازیابی
            log("۲/۵ جست‌وجو در منابع…"); progress(0.10)
            passages = retrieve(spec, terms, cols)
            if (passages.isEmpty()) throw IllegalStateException("در منابع انتخاب‌شده متنی دربارهٔ این موضوع پیدا نشد. عبارت را ساده‌تر کنید یا منابع بیشتری انتخاب کنید.")
            log("   ${PersianUtil.toPersianNumbers(passages.map { it.path }.distinct().size)} سند و ${PersianUtil.toPersianNumbers(passages.size)} بخش مرتبط پیدا شد.")
            // ۳) فیش‌برداری
            fishes = extract(spec, passages, project)
            log("   ${PersianUtil.toPersianNumbers(fishes.size)} فیش با نقل‌قول راستی‌آزمایی‌شده ساخته شد.")
        } else {
            fishes = ResearchStore.fishes(project).filter { spec.chapter.isBlank() || it.topic == spec.chapter }
            if (fishes.isEmpty()) throw IllegalStateException("پروژهٔ «$project» فیشی${if (spec.chapter.isNotBlank()) " در فصل «${spec.chapter}»" else ""} ندارد.")
            log("۱/۳ ${PersianUtil.toPersianNumbers(fishes.size)} فیش از پروژه خوانده شد.")
        }
        if (fishes.isEmpty()) throw IllegalStateException("بخش‌های پیداشده با موضوع مرتبط تشخیص داده نشدند. عبارت موضوع را دقیق‌تر بنویسید.")
        // ۴ و ۵) تحلیل و نوشتن
        log("${if (spec.scenario.retrieves) "۴/۵" else "۲/۳"} تحلیل «${spec.scenario.title}»…"); progress(0.75)
        val w = ReportWriter(spec.output)
        writeHeader(w, spec, terms, fishes)
        val cited = when (spec.scenario) {
            Scenario.FISH -> fishReport(w, spec, fishes)
            Scenario.GROUNDED -> grounded(w, spec, fishes)
            Scenario.THEMATIC -> thematic(w, spec, fishes)
            Scenario.REVIEW -> review(w, spec, fishes)
            Scenario.VIEWS -> views(w, spec, fishes)
            Scenario.ANSWER -> answer(w, spec, fishes)
            Scenario.OUTLINE -> outline(w, spec, fishes, project)
            Scenario.CHAPTER -> chapter(w, spec, fishes)
        }
        appendix(w, cited)
        log("${if (spec.scenario.retrieves) "۵/۵" else "۳/۳"} ذخیرهٔ فایل و فیش‌ها…"); progress(0.95)
        w.save()
        if (spec.scenario.retrieves) ResearchStore.addAll(cited)
        progress(1.0)
        return MissionResult(spec.output, fishes.size, AiClient.calls - calls0, fishes.map { it.sourcePath }.distinct().size)
    }

    // ---------------------------------------------------------------- مراحل

    internal fun expand(spec: MissionSpec): List<String> {
        val r = ask(SYS, "موضوع پژوهش: «${spec.topic}»${if (spec.goal.isNotBlank()) "\nهدف: ${spec.goal}" else ""}\n" +
            "تا ۱۰ واژه یا عبارت کوتاه فارسی (و در صورت لزوم عربی) بنویس که در متون همین موضوع را با تعبیرهای دیگر بیان می‌کنند " +
            "(مترادف، اصطلاح تخصصی، تعبیر رایج در متون دینی و علمی). هر عبارت حداکثر ۴ واژه.\nقالب: {\"terms\":[\"...\"]}", 600)
        return runCatching { AiClient.json(r)!!.asJsonObject.getAsJsonArray("terms").map { it.asString.trim() } }.getOrDefault(emptyList())
            .filter { it.isNotBlank() && it.length < 60 }.distinct().take(10)
    }

    internal fun retrieve(spec: MissionSpec, terms: List<String>, all: List<IndexerInfo>): List<Passage> {
        val cols = if (spec.collections.isEmpty()) all else all.filter { it.indexName in spec.collections }
        val phrases = (listOf(spec.topic) + terms).distinct()
        val ss = SearchSpec(phrases, Combine.ANY, synonyms = true, semantic = ir.matnyaab.core.semantic.SemanticService.usable(settings))
        val hits = LuceneIndexer(settings).searchSpec(ss, cols, emptySet()).take(spec.maxDocs)
        val engine = MatchEngine(ss.copy(semantic = false))
        val out = mutableListOf<Passage>()
        for (h in hits) {
            val snips = if (h.matches == 0 && h.chunkStart >= 0) listOf(Triple(h.chunkStart, h.chunkEnd, h.content.substring(h.chunkStart, h.chunkEnd)))
                else engine.snippets(h.content, max = 3, radius = 380).map { Triple(it.start - it.before.length, it.end + it.after.length, it.before + it.match + it.after) }
            // ادغام تکه‌های هم‌پوشان یک سند
            var lastEnd = -1
            for ((s, e, t) in snips.sortedBy { it.first }) {
                if (s < lastEnd) continue
                out.add(Passage(out.size + 1, h.path, h.name, h.indexName, s, e, t.trim()))
                lastEnd = e
            }
            if (out.size >= 90) break
        }
        return out
    }

    internal fun extract(spec: MissionSpec, passages: List<Passage>, project: String): List<Fish> {
        val out = mutableListOf<Fish>()
        val batches = passages.chunked(6)
        batches.forEachIndexed { bi, batch ->
            log("۳/۵ فیش‌برداری: بخش ${PersianUtil.toPersianNumbers(bi + 1)} از ${PersianUtil.toPersianNumbers(batches.size)}…")
            progress(0.15 + 0.58 * bi / batches.size)
            val listing = batch.joinToString("\n\n") { "[${it.n}] (منبع: ${it.name})\n${it.text}" }
            val r = ask(SYS, "موضوع: «${spec.topic}»${if (spec.goal.isNotBlank()) "\nهدف پژوهش: ${spec.goal}" else ""}\n" +
                (if (spec.scenario == Scenario.GROUNDED) "روش: کدگذاری باز نظریهٔ داده‌بنیاد؛ برای هر فیش ۱ تا ۳ کد باز مفهومی کوتاه بنویس.\n"
                 else if (spec.scenario == Scenario.THEMATIC) "روش: تحلیل مضمون؛ برای هر فیش ۱ تا ۳ کد/مضمون پایه بنویس.\n" else "") +
                (if (spec.extra.isNotBlank()) "دستور تکمیلی کاربر: ${spec.extra}\n" else "") +
                "برای هر بخش زیر که واقعاً به موضوع مربوط است یک فیش بساز. «quote» باید عیناً (کلمه‌به‌کلمه) بخشی پیوسته از همان متن باشد " +
                "که مستقیم به موضوع می‌پردازد (۱ تا ۵ جمله). عنوان فیش کوتاه و گویا باشد.\n" +
                "قالب: [{\"n\":شماره,\"relevant\":true,\"title\":\"...\",\"quote\":\"...\",\"codes\":[\"...\"]}]\n\n$listing", 3500)
            val arr = (AiClient.json(r) as? JsonArray) ?: (AiClient.json(r) as? JsonObject)?.entrySet()?.firstOrNull { it.value.isJsonArray }?.value?.asJsonArray
            if (arr == null) { log("   (پاسخ این بخش قابل خواندن نبود؛ رد شد)"); return@forEachIndexed }
            for (e in arr) {
                val o = runCatching { e.asJsonObject }.getOrNull() ?: continue
                if (o.get("relevant")?.asBoolean == false) continue
                val p = batch.firstOrNull { it.n == o.get("n")?.asInt } ?: continue
                val quote = o.get("quote")?.asString?.trim().orEmpty()
                val located = locate(p.text, quote)
                val text = located ?: p.text.take(700)
                val absStart = if (located != null) p.start + p.text.indexOf(located).coerceAtLeast(0) else p.start
                out.add(Fish(project = project, title = o.get("title")?.asString?.trim().orEmpty().ifBlank { text.take(40) }, text = text,
                    codes = o.getAsJsonArray("codes")?.map { it.asString.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                    sourcePath = p.path, sourceName = p.name, collection = p.collection, start = absStart, end = absStart + text.length,
                    origin = "mission", note = if (located == null) "نقل‌قول پیشنهادی مدل با متن منبع یکسان نبود؛ کل بخش منبع ثبت شد." else ""))
            }
        }
        return out
    }

    /** راستی‌آزمایی: نقل‌قول مدل باید در متن منبع باشد (با یکسان‌سازی املا)؛ متن عینِ منبع برگردانده می‌شود */
    private fun locate(source: String, quote: String): String? {
        if (quote.length < 8) return null
        if (source.contains(quote)) return quote
        val sim = ir.matnyaab.core.research.SourceFinder.similarity(source, quote)
        if (sim < 0.7) return null
        val tokens = Regex("[\\p{L}\\p{N}]+").findAll(source).toList()
        val qs = Regex("[\\p{L}\\p{N}]+").findAll(quote).map { Morph.norm(it.value) }.toList()
        if (qs.isEmpty()) return null
        val first = tokens.indexOfFirst { Morph.norm(it.value) == qs.first() }
        val last = tokens.indexOfLast { Morph.norm(it.value) == qs.last() }
        if (first < 0 || last < first) return null
        return source.substring(tokens[first].range.first, tokens[last].range.last + 1)
    }

    // ---------------------------------------------------------------- نوشتن

    internal fun cite(f: Fish) = f.sourceName + (if (f.collection.isNotBlank()) " — مجموعهٔ ${f.collection}" else "") + " — " + f.sourcePath

    private fun writeHeader(w: ReportWriter, spec: MissionSpec, terms: List<String>, fishes: List<Fish>) {
        w.title(if (spec.scenario == Scenario.CHAPTER) spec.chapter.ifBlank { spec.project } else spec.topic)
        w.subtitle(spec.scenario.title)
        if (spec.goal.isNotBlank()) w.meta("هدف", spec.goal)
        if (terms.isNotEmpty()) w.meta("عبارت‌های جست‌وجوشده", (listOf(spec.topic) + terms).joinToString("، "))
        w.meta("منابع", if (spec.collections.isEmpty()) "همهٔ مجموعه‌ها" else spec.collections.joinToString("، "))
        w.meta("تعداد", "${PersianUtil.toPersianNumbers(fishes.size)} فیش از ${PersianUtil.toPersianNumbers(fishes.map { it.sourcePath }.distinct().size)} سند")
        w.meta("تاریخ و ابزار", PersianUtil.toJalali(LocalDateTime.now()) + " — متن‌یاب با ${provider.Title} (${provider.Model})")
        w.note("نقل‌قول‌ها عیناً از منابع است و متن‌یاب هر نقل‌قول را با متن اصلی تطبیق داده است؛ تحلیل‌ها پیشنهاد هوش مصنوعی‌اند و بازبینی پژوهشگر لازم است.")
    }

    private fun listFish(fishes: List<Fish>) = fishes.mapIndexed { i, f -> "[${i + 1}] ${f.title}" + (if (f.codes.isNotEmpty()) " | کدها: ${f.codes.joinToString("، ")}" else "") }.joinToString("\n")

    private fun ids(o: JsonObject, key: String = "ids") = o.getAsJsonArray(key)?.mapNotNull { runCatching { it.asInt }.getOrNull() } ?: emptyList()

    private fun quoteFish(w: ReportWriter, f: Fish) { w.heading(3, f.title); w.quote(f.text, cite(f)); if (f.codes.isNotEmpty()) w.note("کدها: " + f.codes.joinToString("، ")) }

    private fun grouped(w: ReportWriter, fishes: List<Fish>, groups: List<Pair<String, List<Int>>>): List<Fish> {
        val used = HashSet<Int>()
        val out = mutableListOf<Fish>()
        for ((title, idx) in groups) {
            val list = idx.filter { it in 1..fishes.size && used.add(it) }
            if (list.isEmpty()) continue
            w.heading(2, title)
            list.forEach { val f = fishes[it - 1].copy(topic = title); quoteFish(w, f); out.add(f) }
        }
        val rest = fishes.indices.map { it + 1 }.filter { it !in used }
        if (rest.isNotEmpty()) { w.heading(2, "سایر فیش‌ها"); rest.forEach { val f = fishes[it - 1].copy(topic = "سایر"); quoteFish(w, f); out.add(f) } }
        return out
    }

    private fun fishReport(w: ReportWriter, spec: MissionSpec, fishes: List<Fish>): List<Fish> {
        val r = ask(SYS, "این فیش‌ها دربارهٔ «${spec.topic}» است. آن‌ها را در ۳ تا ۸ سرفصل منطقی گروه‌بندی کن (هر فیش فقط یک بار).\n" +
            "قالب: {\"sections\":[{\"title\":\"...\",\"ids\":[1,2]}]}\n\n${listFish(fishes)}", 1500)
        val groups = runCatching { AiClient.json(r)!!.asJsonObject.getAsJsonArray("sections").map { it.asJsonObject.let { o -> o.get("title").asString to ids(o) } } }.getOrDefault(emptyList())
        w.heading(1, "فیش‌ها بر پایهٔ سرفصل")
        return grouped(w, fishes, groups)
    }

    private fun grounded(w: ReportWriter, spec: MissionSpec, fishes: List<Fish>): List<Fish> {
        val r = ask(SYS, "روش: نظریهٔ داده‌بنیاد (گراندد تئوری). موضوع: «${spec.topic}».\nکدهای باز زیر را به مفاهیم و مفاهیم را به مقوله‌ها (کدگذاری محوری) برسان و مقولهٔ هسته را پیشنهاد کن. " +
            "برای هر مفهوم شمارهٔ فیش‌های پشتیبان را بیاور. یک یادداشت تحلیلی (memo) کوتاه هم بنویس.\n" +
            "قالب: {\"core\":\"...\",\"memo\":\"...\",\"categories\":[{\"title\":\"...\",\"concepts\":[{\"title\":\"...\",\"ids\":[1]}]}]}\n\n${listFish(fishes)}", 3500)
        val o = AiClient.json(r) as? JsonObject
        val cats = o?.getAsJsonArray("categories")?.map { it.asJsonObject } ?: emptyList()
        w.heading(1, "جدول کدگذاری باز")
        w.table(listOf("ش", "عنوان فیش", "کدهای باز", "منبع"), fishes.mapIndexed { i, f -> listOf(PersianUtil.toPersianNumbers(i + 1), f.title, f.codes.joinToString("، "), f.sourceName) })
        w.heading(1, "کدگذاری محوری: مفاهیم و مقوله‌ها")
        w.table(listOf("مقوله", "مفهوم", "فیش‌ها"), cats.flatMap { c -> (c.getAsJsonArray("concepts")?.map { it.asJsonObject } ?: emptyList()).map { k ->
            listOf(c.get("title")?.asString ?: "", k.get("title")?.asString ?: "", ids(k).joinToString("، ") { PersianUtil.toPersianNumbers(it) }) } })
        o?.get("core")?.asString?.let { w.heading(2, "مقولهٔ هسته (پیشنهادی)"); w.para(it) }
        o?.get("memo")?.asString?.let { w.heading(2, "یادداشت تحلیلی"); w.para(it) }
        w.heading(1, "شواهد هر مقوله")
        return grouped(w, fishes, cats.map { c -> (c.get("title")?.asString ?: "مقوله") to (c.getAsJsonArray("concepts")?.flatMap { ids(it.asJsonObject) } ?: emptyList()) })
    }

    private fun thematic(w: ReportWriter, spec: MissionSpec, fishes: List<Fish>): List<Fish> {
        val r = ask(SYS, "روش: تحلیل مضمون (شبکهٔ مضامین). موضوع: «${spec.topic}».\nاز کدهای فیش‌ها، مضامین پایه، سازمان‌دهنده و فراگیر را استخراج کن و فیش‌های هر مضمون پایه را بیاور.\n" +
            "قالب: {\"global\":\"...\",\"organizing\":[{\"title\":\"...\",\"basic\":[{\"title\":\"...\",\"ids\":[1]}]}],\"summary\":\"...\"}\n\n${listFish(fishes)}", 3000)
        val o = AiClient.json(r) as? JsonObject
        val org = o?.getAsJsonArray("organizing")?.map { it.asJsonObject } ?: emptyList()
        o?.get("global")?.asString?.let { w.heading(1, "مضمون فراگیر"); w.para(it) }
        w.heading(1, "شبکهٔ مضامین")
        w.table(listOf("مضمون سازمان‌دهنده", "مضمون پایه", "فیش‌ها"), org.flatMap { g -> (g.getAsJsonArray("basic")?.map { it.asJsonObject } ?: emptyList()).map { b ->
            listOf(g.get("title")?.asString ?: "", b.get("title")?.asString ?: "", ids(b).joinToString("، ") { PersianUtil.toPersianNumbers(it) }) } })
        o?.get("summary")?.asString?.let { w.heading(2, "جمع‌بندی"); w.para(it) }
        w.heading(1, "شواهد مضامین")
        return grouped(w, fishes, org.flatMap { g -> (g.getAsJsonArray("basic")?.map { it.asJsonObject } ?: emptyList()).map { b -> ((g.get("title")?.asString ?: "") + " ← " + (b.get("title")?.asString ?: "")) to ids(b) } })
    }

    /** متن با ارجاع [n] ← پاراگراف با پانویس منبع فیش‌ها */
    private fun citedText(w: ReportWriter, text: String, fishes: List<Fish>) {
        for (para in text.split(Regex("\\n\\s*\\n|\\n")).map { it.trim() }.filter { it.isNotEmpty() }) {
            val refs = Regex("\\[(\\d+)(?:\\s*[،,]\\s*(\\d+))*]").findAll(para).flatMap { m -> Regex("\\d+").findAll(m.value).map { it.value.toInt() } }.toList()
            val clean = para.replace(Regex("\\s*\\[(\\d+)(?:\\s*[،,]\\s*\\d+)*]"), "")
            val src = refs.filter { it in 1..fishes.size }.distinct().joinToString(" ؛ ") { cite(fishes[it - 1]) }
            w.para(clean, src.ifBlank { null })
        }
    }

    private fun review(w: ReportWriter, spec: MissionSpec, fishes: List<Fish>): List<Fish> {
        val r = ask(SYS, "بر پایهٔ فیش‌های زیر، «پیشینهٔ پژوهش» دربارهٔ «${spec.topic}» بنویس: ابتدا برای هر منبع یک بند کوتاه دربارهٔ دیدگاه/یافتهٔ آن، " +
            "سپس جمع‌بندی (همسویی‌ها، اختلاف‌ها و خلأ پژوهشی). پس از هر ادعا شمارهٔ فیش را به شکل [n] بیاور. فقط متن ساده، بدون JSON.\n\n" +
            fishes.mapIndexed { i, f -> "[${i + 1}] منبع: ${f.sourceName}\n${f.text}" }.joinToString("\n\n").take(60_000), 4000)
        w.heading(1, "پیشینه و مرور منابع")
        citedText(w, r, fishes)
        w.heading(1, "فیش‌های پشتیبان")
        fishes.forEach { quoteFish(w, it) }
        return fishes
    }

    private fun views(w: ReportWriter, spec: MissionSpec, fishes: List<Fish>): List<Fish> {
        val r = ask(SYS, "دیدگاه‌های مطرح‌شده دربارهٔ «${spec.topic}» را از فیش‌های زیر استخراج و دسته‌بندی کن؛ دیدگاه‌های مشابه را کنار هم بگذار و در پایان مقایسه کن.\n" +
            "قالب: {\"views\":[{\"title\":\"...\",\"summary\":\"...\",\"ids\":[1]}],\"comparison\":\"...\"}\n\n" +
            fishes.mapIndexed { i, f -> "[${i + 1}] (${f.sourceName}) ${f.title}: ${f.text.take(500)}" }.joinToString("\n"), 3000)
        val o = AiClient.json(r) as? JsonObject
        val vs = o?.getAsJsonArray("views")?.map { it.asJsonObject } ?: emptyList()
        w.heading(1, "دیدگاه‌ها")
        val used = mutableListOf<Fish>()
        vs.forEach { v ->
            w.heading(2, v.get("title")?.asString ?: "دیدگاه")
            v.get("summary")?.asString?.let { w.para(it) }
            ids(v).filter { it in 1..fishes.size }.forEach { val f = fishes[it - 1].copy(topic = v.get("title")?.asString ?: ""); quoteFish(w, f); used.add(f) }
        }
        o?.get("comparison")?.asString?.let { w.heading(1, "مقایسه و جمع‌بندی"); w.para(it) }
        return (used + fishes.filter { f -> used.none { it.id == f.id } }).distinctBy { it.id }
    }

    private fun answer(w: ReportWriter, spec: MissionSpec, fishes: List<Fish>): List<Fish> {
        val q = spec.goal.ifBlank { spec.topic }
        val r = ask(SYS, "پرسش: «$q»\nفقط بر پایهٔ فیش‌های زیر پاسخ تحلیلی و منسجم بنویس. پس از هر ادعا شمارهٔ فیش را به شکل [n] بیاور. " +
            "اگر منابع برای بخشی از پرسش کافی نیستند، صریح بگو. فقط متن ساده.\n\n" +
            fishes.mapIndexed { i, f -> "[${i + 1}] (${f.sourceName})\n${f.text}" }.joinToString("\n\n").take(60_000), 3500)
        w.heading(1, "پاسخ")
        citedText(w, r, fishes)
        w.heading(1, "شواهد")
        fishes.forEach { quoteFish(w, it) }
        return fishes
    }

    private fun outline(w: ReportWriter, spec: MissionSpec, fishes: List<Fish>, project: String): List<Fish> {
        val r = ask(SYS, "برای یک ${if (spec.goal.contains("مقاله")) "مقاله" else "کتاب"} دربارهٔ «${spec.topic}»${if (spec.goal.isNotBlank()) " (هدف: ${spec.goal})" else ""} " +
            "از فیش‌های زیر یک طرح منطقی بساز: ۳ تا ۱۰ فصل، هر فصل ۲ تا ۶ بخش، و برای هر بخش فیش‌های پشتیبان. هر فیش فقط در یک بخش.\n" +
            "قالب: {\"chapters\":[{\"title\":\"...\",\"sections\":[{\"title\":\"...\",\"ids\":[1]}]}]}\n\n${listFish(fishes)}", 3500)
        val chs = (AiClient.json(r) as? JsonObject)?.getAsJsonArray("chapters")?.map { it.asJsonObject } ?: emptyList()
        w.toc()
        val out = mutableListOf<Fish>()
        val used = HashSet<Int>()
        chs.forEachIndexed { ci, c ->
            val ct = c.get("title")?.asString ?: "فصل ${ci + 1}"
            w.heading(1, "فصل ${PersianUtil.toPersianNumbers(ci + 1)}: $ct")
            (c.getAsJsonArray("sections")?.map { it.asJsonObject } ?: emptyList()).forEach { s ->
                w.heading(2, s.get("title")?.asString ?: "")
                ids(s).filter { it in 1..fishes.size && used.add(it) }.forEach { val f = fishes[it - 1].copy(topic = ct); quoteFish(w, f); out.add(f) }
            }
        }
        val rest = fishes.indices.filter { (it + 1) !in used }.map { fishes[it].copy(topic = "سایر") }
        if (rest.isNotEmpty()) { w.heading(1, "سایر فیش‌ها"); rest.forEach { quoteFish(w, it) } }
        val p = ResearchStore.ensureProject(project, spec.goal)
        ResearchStore.updateProject(p.copy(topics = (chs.map { it.get("title")?.asString ?: "" }.filter { it.isNotBlank() } + p.topics).distinct()))
        return out + rest
    }

    private fun chapter(w: ReportWriter, spec: MissionSpec, fishes: List<Fish>): List<Fish> {
        val groups = fishes.groupBy { it.topic.ifBlank { "بدون فصل" } }
        val proj = ResearchStore.project(spec.project)
        val order = (proj?.topics ?: emptyList()).filter { it in groups } + groups.keys.filter { it !in (proj?.topics ?: emptyList()) }
        if (spec.chapter.isBlank()) w.toc()
        var done = 0
        for (t in order) {
            val fs = groups[t] ?: continue
            log("   نگارش «$t» (${PersianUtil.toPersianNumbers(fs.size)} فیش)…"); progress(0.2 + 0.7 * done++ / order.size)
            val r = ask(SYS, "بر پایهٔ فیش‌های زیر، متن پیوسته و روان فصل/بخش «$t» را برای ${spec.goal.ifBlank { "یک کتاب پژوهشی" }} بنویس. " +
                "لحن علمی و خوش‌خوان، بدون افزودن اطلاعات خارج از فیش‌ها. پس از هر ادعا یا نقل، شمارهٔ فیش را به شکل [n] بیاور. " +
                (if (spec.extra.isNotBlank()) "دستور کاربر: ${spec.extra}. " else "") + "فقط متن ساده با بندهای جدا.\n\n" +
                fs.mapIndexed { i, f -> "[${i + 1}] ${f.title} (${f.sourceName})\n${f.text}${if (f.note.isNotBlank()) "\nیادداشت پژوهشگر: ${f.note}" else ""}" }.joinToString("\n\n").take(60_000), 4000)
            w.heading(1, t)
            citedText(w, r, fs)
        }
        return fishes
    }

    private fun appendix(w: ReportWriter, fishes: List<Fish>) {
        val src = (fishes.ifEmpty { emptyList() }).map { it.sourceName to it.sourcePath }.distinct().sortedBy { it.first }
        if (src.isEmpty()) return
        w.heading(1, "فهرست منابع")
        src.forEach { (n, p) -> w.bullet("$n — $p") }
    }

    companion object {
        /** تبدیل دستور آزاد کاربر به مأموریت قابل اجرا (برای بازبینی کاربر پیش از اجرا) */
        fun route(provider: AiProvider, instruction: String, collections: List<String>, projects: List<String>): JsonObject? {
            val r = AiClient.complete(provider, "شما برنامه‌ریز دقیق مأموریت‌های پژوهشی هستید. فقط JSON بدهید.",
                "دستور کاربر: «$instruction»\n\nسناریوهای ممکن:\n" + Scenario.values().joinToString("\n") { "- ${it.name}: ${it.title} — ${it.desc}" } +
                    "\n\nمجموعه‌های موجود: ${collections.joinToString("، ")}\nپروژه‌های موجود: ${projects.joinToString("، ")}\n" +
                    "قالب: {\"scenario\":\"FISH\",\"topic\":\"...\",\"goal\":\"...\",\"collections\":[\"...\"],\"project\":\"...\",\"chapter\":\"\",\"output\":\"مسیر کامل فایل اگر کاربر گفته، وگرنه خالی\",\"format\":\"docx یا md\",\"extra\":\"دستور تکمیلی\"}", 800, 0.0)
            return AiClient.json(r) as? JsonObject
        }
    }
}
