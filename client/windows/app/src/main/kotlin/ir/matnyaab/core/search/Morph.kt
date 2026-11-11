package ir.matnyaab.core.search

import ir.matnyaab.core.Constants
import java.nio.file.Files

/** ریشه‌یابی سبک برای «جست‌وجوی هم‌خانواده» (عربی‌تبار سه‌حرفی: علم ← عالم، علوم، تعلیم، معلم…) */
object Morph {
    private val SUFFIXES = listOf("هایی", "های", "ها", "یان", "یون", "ات", "ان", "ین", "ون", "یت", "یه", "ی", "ه")
    private val PREFIXES = listOf("است", "مست", "مت", "من", "م", "ت")

    fun norm(w: String): String = w.trim()
        .replace(Regex("[ً-ٰٟـ‌\\s]"), "")
        .map { c ->
            when (c) {
                'ي', 'ى', 'ئ' -> 'ی'; 'ك' -> 'ک'; 'أ', 'إ', 'آ', 'ٱ' -> 'ا'; 'ة', 'ۀ' -> 'ه'; 'ؤ' -> 'و'; else -> c
            }
        }.joinToString("")

    fun root(word: String): String {
        var w = norm(word)
        if (w.length <= 3) return w
        repeat(2) {
            SUFFIXES.firstOrNull { w.endsWith(it) && w.length - it.length >= 3 }?.let { w = w.dropLast(it.length) }
        }
        PREFIXES.firstOrNull { w.startsWith(it) && w.length - it.length >= 3 }?.let { w = w.drop(it.length) }
        // حذف حروف عله (ا، و، ی) از میانه تا رسیدن به سه حرف اصلی
        var i = w.length - 1
        while (w.length > 3 && i >= 1) {
            if (w[i] in "اوی") w = w.removeRange(i, i + 1)
            i--
        }
        return w
    }
}

/**
 * فهرست مترادف‌ها: فهرست توکار (پژوهش‌های اسلامی، اخلاق و علوم انسانی) + فهرست شخصی کاربر
 * در پوشهٔ تنظیمات (synonyms.txt). هر سطر یک گروه: «خودسازی = تهذیب نفس، تزکیه نفس».
 */
object Thesaurus {
    @Volatile private var groups: List<List<String>>? = null

    val userFile get() = Constants.APP_SETTINGS_DIR.resolve("synonyms.txt")

    private fun parse(text: String): List<List<String>> = text.lines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .map { line -> line.split(Regex("[=،,|؛;]")).map { it.trim() }.filter { it.isNotEmpty() } }
        .filter { it.size >= 2 }

    fun load(): List<List<String>> {
        groups?.let { return it }
        val builtIn = runCatching {
            Thesaurus::class.java.getResourceAsStream("/search/synonyms.txt")!!.bufferedReader(Charsets.UTF_8).readText()
        }.getOrDefault("")
        val user = runCatching { if (Files.exists(userFile)) Files.readString(userFile) else "" }.getOrDefault("")
        return (parse(builtIn) + parse(user)).also { groups = it }
    }

    fun reload() { groups = null }

    /** عبارت + همهٔ مترادف‌هایش (نخستین مورد خود عبارت است) */
    fun expand(phrase: String): List<String> {
        val key = Morph.norm(phrase)
        val out = linkedSetOf(phrase.trim())
        for (g in load()) if (g.any { Morph.norm(it) == key }) out.addAll(g)
        return out.toList()
    }

    /** ساخت فایل شخصی با راهنما (اگر نیست) و برگرداندن مسیرش */
    fun ensureUserFile(): java.io.File {
        val f = userFile.toFile()
        if (!f.exists()) {
            f.parentFile.mkdirs()
            f.writeText(
                "# فهرست مترادف‌های شخصی متن‌یاب — هر سطر یک گروه\n" +
                    "# نمونه: خودسازی = تهذیب نفس، تزکیه نفس، اصلاح نفس\n" +
                    "# پس از ذخیره، جست‌وجوی بعدی از فهرست تازه استفاده می‌کند.\n\n", Charsets.UTF_8)
        }
        return f
    }
}
