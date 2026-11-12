package ir.matnyaab.core.quran

import java.io.File

/**
 * کشف‌الآیات: متن قرآن (Tanzil، با ذکر منبع)، نام سوره‌ها، یکسان‌سازی نگارش و تطبیق نقل کامل/بخشی،
 * ارجاع شماره‌ای («بقره، آیهٔ ۳») و ترجمهٔ فارسی (از پرونده‌های ترجمه‌ای که کاربر می‌افزاید).
 */
object Quran {
    data class Verse(val sura: Int, val aya: Int, val text: String) { val key get() = "$sura:$aya" }

    /** نام فارسی رایج سوره‌ها (به ترتیب) */
    val NAMES = listOf("فاتحه", "بقره", "آل عمران", "نساء", "مائده", "انعام", "اعراف", "انفال", "توبه", "یونس", "هود", "یوسف", "رعد", "ابراهیم",
        "حجر", "نحل", "اسراء", "کهف", "مریم", "طه", "انبیاء", "حج", "مؤمنون", "نور", "فرقان", "شعراء", "نمل", "قصص", "عنکبوت", "روم", "لقمان",
        "سجده", "احزاب", "سبأ", "فاطر", "یس", "صافات", "ص", "زمر", "غافر", "فصلت", "شوری", "زخرف", "دخان", "جاثیه", "احقاف", "محمد", "فتح",
        "حجرات", "ق", "ذاریات", "طور", "نجم", "قمر", "رحمن", "واقعه", "حدید", "مجادله", "حشر", "ممتحنه", "صف", "جمعه", "منافقون", "تغابن",
        "طلاق", "تحریم", "ملک", "قلم", "حاقه", "معارج", "نوح", "جن", "مزمل", "مدثر", "قیامت", "انسان", "مرسلات", "نبأ", "نازعات", "عبس",
        "تکویر", "انفطار", "مطففین", "انشقاق", "بروج", "طارق", "اعلی", "غاشیه", "فجر", "بلد", "شمس", "لیل", "ضحی", "شرح", "تین", "علق",
        "قدر", "بینه", "زلزال", "عادیات", "قارعه", "تکاثر", "عصر", "همزه", "فیل", "قریش", "ماعون", "کوثر", "کافرون", "نصر", "مسد", "اخلاص", "فلق", "ناس")

    /** نام‌های دیگر رایج در متون فارسی */
    private val ALIASES = mapOf(
        1 to listOf("حمد", "فاتحة الکتاب", "فاتحه الکتاب"), 9 to listOf("برائت", "براءت"), 17 to listOf("اسری", "بنی اسرائیل"),
        23 to listOf("مومنون"), 34 to listOf("سبا"), 36 to listOf("یاسین"), 40 to listOf("مؤمن", "مومن"), 41 to listOf("حم سجده", "حم السجده"),
        47 to listOf("قتال"), 55 to listOf("الرحمن"), 75 to listOf("قیامه"), 76 to listOf("دهر", "هل اتی"), 78 to listOf("نبا", "عم"),
        94 to listOf("انشراح", "الم نشرح"), 96 to listOf("اقرأ", "اقرا"), 98 to listOf("لم یکن"), 99 to listOf("زلزله"),
        111 to listOf("لهب", "تبت"), 112 to listOf("توحید"), 82 to listOf("انفطار"), 33 to listOf("احزاب"),
    )

    val verses: List<Verse> by lazy {
        val out = ArrayList<Verse>(6236)
        Quran::class.java.getResourceAsStream("/quran/quran-simple-plain.txt")!!.bufferedReader(Charsets.UTF_8).useLines { ls ->
            ls.forEach { l -> if (!l.startsWith("#") && l.isNotBlank()) { val p = l.split('|', limit = 3); out.add(Verse(p[0].toInt(), p[1].toInt(), p[2])) } }
        }
        out
    }
    val counts: IntArray by lazy { IntArray(115).also { c -> verses.forEach { c[it.sura]++ } } }
    private val byKey by lazy { verses.associateBy { it.key } }
    fun verse(sura: Int, aya: Int) = byKey["$sura:$aya"]
    fun index(v: Verse) = verses.indexOf(v)
    fun label(sura: Int, aya: Int) = "${NAMES.getOrElse(sura - 1) { "?" }}، ${ir.matnyaab.util.PersianUtil.toPersianNumbers(aya)}"

    // ------------------------------------------------------------------ یکسان‌سازی

    private fun isMark(c: Char) = c in 'ً'..'ٟ' || c == 'ٰ' || c in 'ۖ'..'ۭ' || c == 'ـ' || c == '‌' || c == '‍' || c == '‏' || c == '‎'

    /** نسخهٔ هم‌طول متن برای ارجاع‌یابی: حروف عربی ← فارسی، ارقام ← لاتین (جای نویسه‌ها ثابت می‌ماند) */
    fun sameLength(s: String): String {
        val a = s.toCharArray()
        for (i in a.indices) a[i] = when (val c = a[i]) {
            'ي', 'ى', 'ئ' -> 'ی'; 'ك' -> 'ک'; 'ة', 'ۀ' -> 'ه'; 'أ', 'إ', 'ٱ' -> 'ا'; 'ؤ' -> 'و'
            in '۰'..'۹' -> '0' + (c - '۰'); in '٠'..'٩' -> '0' + (c - '٠')
            else -> c
        }
        return String(a)
    }

    /** اسکلت واژه برای تطبیق نقل عربی: بی‌اعراب، حروف یکسان، بی‌الف (تفاوت رسم‌الخط عثمانی و املایی را می‌پوشاند) */
    fun skeleton(word: String): String {
        val sb = StringBuilder(word.length)
        for (c in word) {
            if (isMark(c)) continue
            when (c) {
                'ي', 'ى', 'ئ', 'ی' -> sb.append('ی'); 'ك', 'ک' -> sb.append('ک'); 'ة', 'ۀ' -> sb.append('ه')
                'ا', 'أ', 'إ', 'آ', 'ٱ', 'ء' -> {}
                'ؤ' -> sb.append('و')
                else -> if (Character.isLetter(c)) sb.append(c)
            }
        }
        return sb.toString()
    }

    /** واژه‌های فارسی برای تطبیق ترجمه (الف حفظ می‌شود) */
    fun persianWord(word: String): String {
        val sb = StringBuilder(word.length)
        for (c in word) {
            if (isMark(c)) continue
            when (c) { 'ي', 'ى', 'ئ' -> sb.append('ی'); 'ك' -> sb.append('ک'); 'ة', 'ۀ' -> sb.append('ه'); 'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا'); 'ؤ' -> sb.append('و')
                else -> if (Character.isLetter(c)) sb.append(c) }
        }
        return sb.toString()
    }

    data class Tok(val w: String, val start: Int, val end: Int)

    /** واژه‌ها با جایشان در متن اصلی */
    fun tokens(text: String, norm: (String) -> String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0; val n = text.length
        while (i < n) {
            while (i < n && !(Character.isLetter(text[i]) || isMark(text[i]))) i++
            val s = i
            while (i < n && (Character.isLetter(text[i]) || isMark(text[i]))) i++
            if (i > s) { val w = norm(text.substring(s, i)); if (w.isNotEmpty()) out.add(Tok(w, s, i)) }
        }
        return out
    }

    // ------------------------------------------------------------------ ارجاع شماره‌ای

    private val nameIndex: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        NAMES.forEachIndexed { i, n -> m[n] = i + 1 }
        ALIASES.forEach { (s, l) -> l.forEach { m[it] = s } }
        m
    }
    private val namesAlt by lazy { nameIndex.keys.sortedByDescending { it.length }.joinToString("|") { sameLength(it).replace(" ", "\\s+") } }
    private val D = "(\\d{1,3})"
    private val refA by lazy { Regex("سوره(?:ٔ|ی|‌ی|ه)?\\s*(?:مبارکه(?:ٔ|ی)?\\s*)?(?:ال)?($namesAlt)(?![\\p{L}])[\\s،,:/\\-–(]{0,4}(?:آیه(?:ٔ|ی)?|آیات|ایه|آ\\.)?\\s*$D(?:\\s*(?:[-–]|تا|و)\\s*$D)?") }
    /** در «نام: عدد» نام‌های یک‌دوحرفی (مثل «ص» که در ارجاع کتاب‌ها «صفحه» است) کنار گذاشته می‌شوند */
    private val namesAltB by lazy { nameIndex.keys.filter { it.length > 2 && it !in setOf("حمد", "عم", "قتال") }.sortedByDescending { it.length }.joinToString("|") { sameLength(it).replace(" ", "\\s+") } }
    private val refB by lazy { Regex("(?<![\\p{L}])(?:ال)?($namesAltB)\\s*[:/]\\s*$D(?:\\s*[-–]\\s*$D)?(?!\\d)") }
    private val refC by lazy { Regex("(?:آیه(?:ٔ|ی)?|آیات)\\s*$D(?:\\s*(?:[-–]|تا|و)\\s*$D)?\\s*(?:از|در)?\\s*سوره(?:ٔ|ی|‌ی|ه)?\\s*(?:مبارکه(?:ٔ|ی)?\\s*)?(?:ال)?($namesAlt)(?![\\p{L}])") }

    data class Ref(val sura: Int, val from: Int, val to: Int, val start: Int, val end: Int)

    fun references(text: String): List<Ref> {
        val t = sameLength(text)
        val out = mutableListOf<Ref>()
        fun add(name: String, a: String?, b: String?, r: IntRange) {
            val s = nameIndex.entries.firstOrNull { sameLength(it.key).replace(Regex("\\s+"), " ") == name.replace(Regex("\\s+"), " ") }?.value ?: return
            val f = a?.toIntOrNull() ?: return
            val to = b?.toIntOrNull()?.takeIf { it >= f } ?: f
            if (f < 1 || to > counts[s] || to - f > 40) return
            if (out.none { it.start <= r.last && r.first <= it.end }) out.add(Ref(s, f, to, r.first, r.last + 1))
        }
        refA.findAll(t).forEach { m -> add(m.groupValues[1], m.groupValues[2], m.groupValues[3].ifEmpty { null }, m.range) }
        refC.findAll(t).forEach { m -> add(m.groupValues[3], m.groupValues[1], m.groupValues[2].ifEmpty { null }, m.range) }
        refB.findAll(t).forEach { m -> add(m.groupValues[1], m.groupValues[2], m.groupValues[3].ifEmpty { null }, m.range) }
        return out.sortedBy { it.start }
    }

    // ------------------------------------------------------------------ ترجمه‌های افزوده‌شده (قالب Tanzil: سوره|آیه|متن)

    fun translationsDir(): File = File(ir.matnyaab.core.Constants.APP_SETTINGS_DIR, "quran").also { it.mkdirs() }
    fun translationFiles(): List<File> = translationsDir().listFiles { f -> f.isFile && f.extension.lowercase() == "txt" }?.sortedBy { it.name } ?: emptyList()

    /** ترجمهٔ کاربر را بررسی و کپی می‌کند؛ پیام خطا یا null */
    fun importTranslation(src: File): String? {
        val lines = src.readLines(Charsets.UTF_8).filter { it.isNotBlank() && !it.startsWith("#") }
        val ok = lines.count { l -> l.split('|', limit = 3).let { it.size == 3 && it[0].toIntOrNull() != null && it[1].toIntOrNull() != null } }
        if (ok < 6000) return "این پرونده قالب ترجمهٔ Tanzil (سوره|آیه|متن) با همهٔ آیات را ندارد (${ok} سطر معتبر)."
        src.copyTo(File(translationsDir(), src.name), overwrite = true)
        Matcher.reset()
        return null
    }

    fun loadTranslation(f: File): Map<String, String> = f.readLines(Charsets.UTF_8).mapNotNull { l ->
        if (l.startsWith("#")) null else l.split('|', limit = 3).takeIf { it.size == 3 }?.let { "${it[0].trim()}:${it[1].trim()}" to it[2] }
    }.toMap()
}
