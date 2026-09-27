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
)

/** اطلاعات فایل در file_list.txt */
data class FileEntry(val filename: String, val size: Long, val time: Double)
