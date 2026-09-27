package ir.matnyaab.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * شناسایی فهرست‌های ساخته‌شده با نسخه پایتونی (Whoosh).
 * به جای مبدل باینری پرخطا، مسیر پوشه اسناد از fileInfo.json خوانده می‌شود
 * و همان پوشه با تایید کاربر دوباره با Lucene فهرست می‌شود.
 */
object WhooshIndexImporter {

    data class OldIndex(val dir: Path, val indexName: String, val rootPath: String)

    /** فایل‌های مخصوص ووش: _MAIN_*.toc ، MAIN_*.seg و *_WRITELOCK */
    private fun isWhooshFile(name: String): Boolean =
        (name.startsWith("_") && name.endsWith(".toc")) ||
        name.endsWith(".seg") ||
        name.endsWith("_WRITELOCK")

    fun isWhooshIndex(dir: Path): Boolean = try {
        Files.list(dir).use { s -> s.anyMatch { isWhooshFile(it.fileName.toString()) } }
    } catch (e: Exception) { false }

    /** یافتن همه پوشه‌های فهرست قدیمی درون پوشه index */
    fun findOldIndexes(indexer: LuceneIndexer, root: Path): List<OldIndex> {
        if (!Files.isDirectory(root)) return emptyList()
        val result = mutableListOf<OldIndex>()
        Files.list(root).use { stream ->
            stream.filter { Files.isDirectory(it) }.forEach { dir ->
                if (!isWhooshIndex(dir)) return@forEach
                val info = indexer.readFileInfo(dir)
                result.add(
                    OldIndex(
                        dir = dir,
                        indexName = info?.get("name") ?: dir.fileName.toString(),
                        rootPath = info?.get("path") ?: "",
                    )
                )
            }
        }
        return result
    }

    /** حذف فایل‌های ووش از پوشه (fileInfo.json حفظ می‌شود تا مسیر اسناد معلوم بماند) */
    fun clearOldIndexFiles(dir: Path) {
        try {
            Files.list(dir).use { stream ->
                stream.filter { isWhooshFile(it.fileName.toString()) }.forEach { f ->
                    try { Files.deleteIfExists(f) } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) { /* مهم نیست */ }
    }
}
