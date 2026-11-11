package ir.matnyaab.core

/** رکورد یک فهرست بارگذاری‌شده — معادل آیتم‌های allObjects در whooshIndexer */
data class IndexerInfo(
    val indexName: String,
    val rootPath: String,
    val savePath: String,
)

/** یک نتیجه جستجو — معادل دیکشنری searchProgress */
data class SearchHit(
    val indexName: String,
    val path: String,
    val name: String,
    val score: Int,
    val size: Long,
    val time: Double,
    val content: String,
    /** تعداد موارد یافت‌شده (جست‌وجوی نسل دوم؛ ‎-1 یعنی محاسبه نشده) */
    val matches: Int = -1,
    /** تعداد موارد هر عبارت در جست‌وجوی چند عبارتی */
    val perPhrase: List<Int> = emptyList(),
    /** ارتباط ترکیبی ۰ تا ۱۰۰ (‎-1 = جست‌وجوی قدیمی) */
    val relevance: Int = -1,
    /** تراکم: تعداد موارد در هر هزار واژه */
    val density: Double = 0.0,
    val words: Int = 0,
    /** شباهت معنایی ۰ تا ۱ (‎-1 = بدون جست‌وجوی معنایی) و بازهٔ بهترین تکه */
    val semantic: Double = -1.0,
    val chunkStart: Int = -1,
    val chunkEnd: Int = -1,
)

/** اطلاعات فایل در file_list.txt */
data class FileEntry(val filename: String, val size: Long, val time: Double)
