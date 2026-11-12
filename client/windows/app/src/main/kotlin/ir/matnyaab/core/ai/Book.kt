package ir.matnyaab.core.ai

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.Constants
import ir.matnyaab.core.IndexerInfo
import ir.matnyaab.core.LuceneIndexer
import ir.matnyaab.core.research.DocxWriter
import ir.matnyaab.core.research.Fish
import ir.matnyaab.core.research.ResearchStore
import ir.matnyaab.util.PersianUtil
import java.io.File

/**
 * قالب نگارش (پرامپت ساختاری) برای ساخت کتاب. کاربر می‌تواند قالب‌ها را تکثیر، ویرایش و حذف کند.
 * متغیرها: {title} {keywords} {audience} {chapter} {section} {words}
 */
data class PromptTemplate(
    val id: String,
    val title: String,
    val desc: String,
    /** سبک و لحن نگارش، برای همهٔ بخش‌ها */
    val style: String,
    /** WOVEN: متن پیوسته با پانویس؛ BRIDGE: فیش‌ها عیناً به‌صورت نقل‌قول و فقط بندهای پیونددهنده نوشته می‌شوند */
    val mode: String = "WOVEN",
    val intro: Boolean = true,
    val chapterSummary: Boolean = false,
    val questions: Boolean = false,
    val conclusion: Boolean = true,
    val builtIn: Boolean = false,
)

object PromptTemplates {
    val DEFAULTS = listOf(
        PromptTemplate("academic", "علمی-پژوهشی", "نثر رسمی و دقیق، هر ادعا با پانویس منبع؛ مناسب کتاب پژوهشی و دانشگاهی.",
            "نثر علمی، رسمی و دقیق فارسی معیار؛ استدلال‌محور؛ پرهیز از اغراق؛ اصطلاحات تخصصی با توضیح کوتاه در نخستین کاربرد.", builtIn = true),
        PromptTemplate("bridge", "پیوند فیش‌ها با بندهای میانی", "متن فیش‌ها عیناً و با منبع می‌ماند؛ هوش مصنوعی فقط بندهای پیونددهنده، درآمد و جمع‌بندی هر بخش را می‌نویسد.",
            "بندهای پیونددهنده کوتاه، روان و منطقی که فیش‌ها را به هم وصل کنند؛ بدون تکرار متن فیش‌ها و بدون افزودن اطلاعات بیرونی.", mode = "BRIDGE", builtIn = true),
        PromptTemplate("popular", "ترویجی و خوش‌خوان", "زبان ساده و گرم برای خوانندهٔ عمومی، با مثال و پرسش‌های برانگیزاننده.",
            "زبان ساده، صمیمی و روان برای خوانندهٔ عمومی؛ جمله‌های کوتاه؛ مثال‌های ملموس؛ بدون اصطلاحات دشوار.", builtIn = true),
        PromptTemplate("textbook", "درسی و آموزشی", "هر فصل با اهداف آموزشی، متن درس، خلاصه و پرسش‌های پایان فصل.",
            "نثر آموزشی گام‌به‌گام، با تعریف مفاهیم، مثال و جمع‌بندی؛ مناسب کتاب درسی.", chapterSummary = true, questions = true, builtIn = true),
        PromptTemplate("hawzawi", "حوزوی و اخلاقی-تفسیری", "نثر فاخر حوزوی؛ آیات و روایات به‌صورت نقل‌قول با منبع و شرح آن‌ها.",
            "نثر فاخر و متین حوزوی؛ آیات و روایات عیناً نقل و سپس شرح داده شوند؛ انسجام موضوعی و تربیتی.", builtIn = true),
        PromptTemplate("thesis", "فصل پایان‌نامه", "ساختار پایان‌نامه: درآمد، مبانی، تحلیل و جمع‌بندی با ارجاع دقیق.",
            "نثر علمی پایان‌نامه؛ ساختار منظم؛ ارجاع دقیق به منابع؛ تفکیک توصیف از تحلیل.", builtIn = true),
    )

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file get() = Constants.APP_SETTINGS_DIR.resolve("book-templates.json").toFile()

    fun all(): List<PromptTemplate> {
        val user = runCatching { gson.fromJson<List<PromptTemplate>>(file.readText(Charsets.UTF_8), object : TypeToken<List<PromptTemplate>>() {}.type) }.getOrNull() ?: emptyList()
        return DEFAULTS.filter { d -> user.none { it.id == d.id } } + user
    }
    fun save(list: List<PromptTemplate>) { file.parentFile.mkdirs(); file.writeText(gson.toJson(list.filter { !it.builtIn || DEFAULTS.none { d -> d == it } }), Charsets.UTF_8) }
    fun upsert(t: PromptTemplate) = save(all().filter { it.id != t.id } + t.copy(builtIn = false))
    fun delete(id: String) = save(all().filter { it.id != id })
}

/** طرح کتاب: فصل‌ها و بخش‌ها (قابل ویرایش در قالب متنی ساده) */
data class Outline(val chapters: List<Chapter>) {
    data class Chapter(val title: String, val sections: List<String>)

    /** «# فصل» و «- بخش» در هر سطر */
    fun toText() = chapters.joinToString("\n") { c -> "# " + c.title + "\n" + c.sections.joinToString("\n") { "- $it" } }

    companion object {
        fun parse(text: String): Outline {
            val out = mutableListOf<Chapter>(); var cur: String? = null; val secs = mutableListOf<String>()
            fun flush() { cur?.let { out.add(Chapter(it, secs.toList())) }; secs.clear() }
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { l ->
                when {
                    l.startsWith("#") -> { flush(); cur = l.trimStart('#').trim() }
                    l.startsWith("-") || l.startsWith("•") || l.startsWith("*") -> secs.add(l.trimStart('-', '•', '*').trim())
                    cur == null -> cur = l
                    else -> secs.add(l)
                }
            }
            flush()
            return Outline(out.filter { it.title.isNotBlank() })
        }
    }
}

data class BookSpec(
    val title: String,
    val keywords: List<String>,
    val audience: String = "",
    val template: PromptTemplate,
    val layout: DocxWriter.Layout,
    val author: String = "",
    /** FROM_FISHES: از فیش‌های پروژه؛ FROM_LIBRARY: جست‌وجو در کتابخانه و فیش‌برداری خودکار */
    val source: String = "FROM_LIBRARY",
    val project: String,
    val collections: List<String> = emptyList(),
    val outline: Outline? = null,
    /** کوتاه/متوسط/بلند ← واژه‌های تقریبی هر بخش */
    val sectionWords: Int = 450,
    val docsPerSection: Int = 8,
    val extra: String = "",
    val output: File,
)

/**
 * ساخت کتاب از ابتدا تا فایل Word صفحه‌آرایی‌شده، با موتورهای داخلی متن‌یاب:
 * طرح ← (جست‌وجوی هر بخش در کتابخانه ← فیش‌برداری با نقل‌قول راستی‌آزمایی‌شده) ← نگارش با پانویس ← صفحه‌آرایی.
 * پیش‌نویس هر بخش در پوشهٔ پروژه نگه داشته می‌شود؛ اجرای دوباره پس از توقف، از همان‌جا ادامه می‌دهد.
 */
class BookRunner(
    private val indexer: LuceneIndexer,
    private val settings: AppSettingsManager,
    private val provider: AiProvider,
    private val log: (String) -> Unit,
    private val progress: (Double) -> Unit,
    private val cancelled: () -> Boolean,
) {
    private val m = MissionRunner(indexer, settings, provider, log, progress, cancelled)
    private val calls0 = AiClient.calls
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private fun fa(n: Int) = PersianUtil.toPersianNumbers(n)

    private fun draftFile(spec: BookSpec) = Constants.APP_SETTINGS_DIR.resolve("books").resolve(spec.project.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".draft.json").toFile().also { it.parentFile.mkdirs() }
    private fun loadDrafts(spec: BookSpec): MutableMap<String, String> = runCatching {
        gson.fromJson<MutableMap<String, String>>(draftFile(spec).readText(Charsets.UTF_8), object : TypeToken<MutableMap<String, String>>() {}.type)
    }.getOrNull() ?: HashMap()
    private fun saveDrafts(spec: BookSpec, d: Map<String, String>) = runCatching { draftFile(spec).writeText(gson.toJson(d), Charsets.UTF_8) }

    private fun fill(s: String, spec: BookSpec, chapter: String = "", section: String = "") = s.replace("{title}", spec.title).replace("{keywords}", spec.keywords.joinToString("، "))
        .replace("{audience}", spec.audience).replace("{chapter}", chapter).replace("{section}", section).replace("{words}", spec.sectionWords.toString())

    /** برآورد شمار درخواست‌ها برای نمایش پیش از اجرا */
    fun estimate(spec: BookSpec, outline: Outline): Int {
        val secs = outline.chapters.sumOf { maxOf(1, it.sections.size) }
        val perSec = if (spec.source == "FROM_LIBRARY") 1 + (spec.docsPerSection * 2 + 5) / 6 + 1 else 1
        return secs * perSec + (if (spec.template.intro) 1 else 0) + (if (spec.template.conclusion) 1 else 0) +
            outline.chapters.size * ((if (spec.template.chapterSummary) 1 else 0) + (if (spec.template.questions) 1 else 0))
    }

    /** پیشنهاد طرح کتاب از عنوان و کلیدواژه‌ها (و فیش‌های موجود، اگر باشند) */
    fun proposeOutline(spec: BookSpec, chapters: Int = 6): Outline {
        val fishes = ResearchStore.fishes(spec.project)
        val ctx = if (spec.source == "FROM_FISHES" && fishes.isNotEmpty())
            "\nفیش‌های موجود (عنوان و سرفصل):\n" + fishes.take(200).joinToString("\n") { "- ${it.title}${if (it.topic.isNotBlank()) " [${it.topic}]" else ""}" } else ""
        val r = m.ask(m.SYS, "برای کتابی با عنوان «${spec.title}»" + (if (spec.keywords.isNotEmpty()) " و کلیدواژه‌های ${spec.keywords.joinToString("، ")}" else "") +
            (if (spec.audience.isNotBlank()) " برای مخاطب «${spec.audience}»" else "") + " طرح منطقی بنویس: حدود $chapters فصل، هر فصل ۲ تا ۵ بخش، عنوان‌ها کوتاه و گویا. " +
            "سبک کتاب: ${spec.template.title}. ${spec.extra}$ctx\nقالب: {\"chapters\":[{\"title\":\"...\",\"sections\":[\"...\"]}]}", 2000)
        val o = AiClient.json(r) as? JsonObject ?: throw IllegalStateException("طرح کتاب ساخته نشد؛ دوباره تلاش کنید یا طرح را دستی بنویسید.")
        return Outline(o.getAsJsonArray("chapters").map { c -> c.asJsonObject.let { Outline.Chapter(it.get("title").asString, it.getAsJsonArray("sections")?.map { s -> s.asString } ?: emptyList()) } })
    }

    fun run(spec: BookSpec, cols: List<IndexerInfo>): MissionResult {
        val outline = spec.outline ?: proposeOutline(spec).also { log("طرح کتاب:\n" + it.toText()) }
        if (outline.chapters.isEmpty()) throw IllegalStateException("طرح کتاب خالی است.")
        val p = ResearchStore.ensureProject(spec.project, spec.title)
        ResearchStore.updateProject(p.copy(topics = (outline.chapters.map { it.title } + p.topics).distinct()))
        val drafts = loadDrafts(spec)
        val all = mutableListOf<Fish>()
        val units = outline.chapters.flatMap { c -> c.sections.ifEmpty { listOf("") }.map { c to it } }
        val sys = m.SYS.replace("پاسخ را دقیقاً در قالب JSON خواسته‌شده و به فارسی بدهید.", "به فارسی بنویسید.") +
            " سبک نگارش: ${fill(spec.template.style, spec)}"
        var done = 0
        val sectionText = LinkedHashMap<Pair<String, String>, Pair<String, List<Fish>>>()
        for ((ch, sec) in units) {
            val key = ch.title + " / " + sec
            val label = if (sec.isBlank()) ch.title else "${ch.title} ← $sec"
            progress(0.05 + 0.85 * done / units.size)
            // فیش‌های این بخش
            val fishes: List<Fish> = if (spec.source == "FROM_FISHES") {
                val fs = ResearchStore.fishes(spec.project)
                fs.filter { it.topic == ch.title || it.topic == sec || (sec.isNotBlank() && it.tags.contains(sec)) }.let { own ->
                    if (sec.isBlank() || own.size <= 12) own else own.filter { it.tags.contains(sec) || it.title.contains(sec) }.ifEmpty { own } }
            } else {
                val prior = ResearchStore.fishes(spec.project).filter { it.topic == ch.title && it.tags.contains(sec.ifBlank { ch.title }) }
                if (prior.isNotEmpty()) prior else {
                    log("جست‌وجوی منابع برای «$label»…")
                    val ms = MissionSpec(Scenario.FISH, (if (sec.isBlank()) ch.title else sec) + " " + spec.title, "بخشی از کتاب «${spec.title}» — فصل «${ch.title}»",
                        spec.collections, spec.project, ch.title, spec.output, spec.docsPerSection, spec.extra)
                    val terms = (spec.keywords.take(3) + m.expand(ms)).distinct().take(8)
                    val pass = m.retrieve(ms, terms, cols).take(spec.docsPerSection * 2)
                    val fs = if (pass.isEmpty()) emptyList() else m.extract(ms, pass, spec.project)
                        .map { it.copy(topic = ch.title, tags = listOf(sec.ifBlank { ch.title })) }
                    if (fs.isNotEmpty()) ResearchStore.addAll(fs)
                    log("   ${fa(fs.size)} فیش راستی‌آزمایی‌شده")
                    fs
                }
            }
            all.addAll(fishes)
            val text = drafts[key] ?: run {
                log("نگارش «$label»…")
                val body = if (fishes.isEmpty()) "برای این بخش فیشی در منابع پیدا نشد؛ فقط یک بند کوتاه درآمدی و پیشنهادی بنویس و صریح بگو که منبع کافی در کتابخانه نبود."
                    else fishes.mapIndexed { i, f -> "[${i + 1}] ${f.title} (${f.sourceName})\n${f.text}${if (f.note.isNotBlank()) "\nیادداشت پژوهشگر: ${f.note}" else ""}" }.joinToString("\n\n").take(50_000)
                val instr = if (spec.template.mode == "BRIDGE")
                    "برای بخش «$label» از کتاب «${spec.title}»، فیش‌های زیر را به همین ترتیب منطقی بچین و میانشان بندهای پیونددهنده بنویس. " +
                        "خروجی را دقیقاً این‌گونه بنویس: هر بند پیونددهنده در یک سطر، و برای جای هر فیش فقط یک سطر جدا به شکل {{n}}. " +
                        "با یک بند درآمد شروع و با یک بند جمع‌بندی کوتاه تمام کن. متن فیش‌ها را تکرار نکن."
                    else "متن پیوسته و روان بخش «$label» از کتاب «${spec.title}» را حدود ${spec.sectionWords} واژه بنویس" +
                        (if (spec.audience.isNotBlank()) " برای مخاطب «${spec.audience}»" else "") + ". فقط بر پایهٔ فیش‌های زیر؛ پس از هر ادعا یا نقل شمارهٔ فیش را به شکل [n] بیاور. " +
                        "نقل‌قول‌های مهم را عیناً داخل «» بیاور. بندها را با سطر خالی جدا کن."
                m.ask(sys, instr + (if (spec.extra.isNotBlank()) "\nدستور کاربر: ${spec.extra}" else "") + "\n\n" + body, 4000).trim()
                    .also { drafts[key] = it; saveDrafts(spec, drafts) }
            }
            sectionText[ch.title to sec] = text to fishes
            done++
        }
        // بخش‌های جانبی
        val intro = if (spec.template.intro) drafts["__intro"] ?: m.ask(sys, "پیشگفتار کتاب «${spec.title}» را در ۲ تا ۴ بند بنویس: ضرورت موضوع، مخاطب و ساختار کتاب.\nطرح:\n${outline.toText()}", 1500).also { drafts["__intro"] = it; saveDrafts(spec, drafts) } else null
        val concl = if (spec.template.conclusion) drafts["__concl"] ?: m.ask(sys, "جمع‌بندی پایانی کتاب «${spec.title}» را در ۲ تا ۴ بند بنویس، بر پایهٔ این خلاصهٔ فصل‌ها:\n" +
            sectionText.entries.joinToString("\n") { (k, v) -> "- ${k.first} / ${k.second}: ${v.first.take(300)}" }.take(20_000), 1500).also { drafts["__concl"] = it; saveDrafts(spec, drafts) } else null

        log("صفحه‌آرایی فایل Word…"); progress(0.94)
        val w = DocxWriter(spec.layout)
        w.title(spec.title)
        if (spec.author.isNotBlank()) w.subtitle(spec.author)
        if (spec.keywords.isNotEmpty()) w.subtitle(spec.keywords.joinToString(" · "))
        w.pageBreak(); w.toc(); w.pageBreak()
        intro?.let { w.heading(1, "پیشگفتار"); paras(it).forEach { p -> w.para(p) }; w.pageBreak() }
        outline.chapters.forEachIndexed { ci, ch ->
            w.heading(1, "فصل ${fa(ci + 1)}: ${ch.title}")
            for (sec in ch.sections.ifEmpty { listOf("") }) {
                val (text, fishes) = sectionText[ch.title to sec] ?: continue
                if (sec.isNotBlank()) w.heading(2, sec)
                write(w, text, fishes, spec.template.mode == "BRIDGE")
            }
            if (spec.template.chapterSummary || spec.template.questions) {
                val k = "__sum/" + ch.title
                val s = drafts[k] ?: m.ask(sys, (if (spec.template.chapterSummary) "خلاصهٔ فصل «${ch.title}» را در چند بند کوتاه بنویس. " else "") +
                    (if (spec.template.questions) "سپس با عنوان «پرسش‌ها» ۵ پرسش مفهومی پایان فصل بنویس، هر پرسش در یک سطر. " else "") +
                    "\nمتن فصل:\n" + ch.sections.ifEmpty { listOf("") }.joinToString("\n") { sectionText[ch.title to it]?.first ?: "" }.take(25_000), 1500).also { drafts[k] = it; saveDrafts(spec, drafts) }
                w.heading(2, if (spec.template.chapterSummary) "خلاصهٔ فصل" else "پرسش‌ها")
                paras(s).forEach { p -> if (p.trim() == "پرسش‌ها") w.heading(3, p.trim()) else w.para(p) }
            }
            w.pageBreak()
        }
        concl?.let { w.heading(1, "سخن پایانی"); paras(it).forEach { p -> w.para(p) } }
        val src = all.map { it.sourceName to it.sourcePath }.distinct().sortedBy { it.first }
        if (src.isNotEmpty()) { w.pageBreak(); w.heading(1, "فهرست منابع"); src.forEach { (n, _) -> w.para(n) } }
        w.save(spec.output)
        progress(1.0)
        return MissionResult(spec.output, all.size, AiClient.calls - calls0, all.map { it.sourcePath }.distinct().size)
    }

    private fun paras(t: String) = t.split(Regex("\\n\\s*\\n|\\n")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun write(w: DocxWriter, text: String, fishes: List<Fish>, bridge: Boolean) {
        val used = HashSet<Int>()
        for (p in paras(text)) {
            val only = Regex("^\\{\\{\\s*(\\d+)\\s*}}$").find(p)
            if (bridge && only != null) {
                val n = only.groupValues[1].toInt()
                if (n in 1..fishes.size && used.add(n)) { val f = fishes[n - 1]; w.quote(f.text, m.cite(f)) }
                continue
            }
            val refs = Regex("\\[(\\d+)(?:\\s*[،,]\\s*(\\d+))*]|\\{\\{\\s*(\\d+)\\s*}}").findAll(p).flatMap { mm -> Regex("\\d+").findAll(mm.value).map { it.value.toInt() } }.toList()
            val clean = p.replace(Regex("\\s*\\[(\\d+)(?:\\s*[،,]\\s*\\d+)*]|\\s*\\{\\{\\s*\\d+\\s*}}"), "").trim()
            val src = refs.filter { it in 1..fishes.size }.distinct().onEach { used.add(it) }.joinToString(" ؛ ") { m.cite(fishes[it - 1]) }
            if (clean.isNotEmpty()) w.para(clean, src.ifBlank { null })
        }
        // در حالت پیوند، فیش‌هایی که مدل جا انداخته هم در پایان بخش می‌آیند تا هیچ فیشی گم نشود
        if (bridge) fishes.forEachIndexed { i, f -> if ((i + 1) !in used) w.quote(f.text, m.cite(f)) }
    }
}
