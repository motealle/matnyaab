package ir.matnyaab.core.quran

import ir.matnyaab.core.TikaExtractor
import java.io.File
import java.io.IOException

/**
 * One source of truth for the file-picker's Quran analysis.
 * Always read the selected on-disk document: an indexed copy can be stale,
 * truncated or even blank. Only use an index snapshot if the file no longer exists.
 */
object QuranDocumentAnalyzer {
    data class Result(val hits: List<QHit>, val usedIndexSnapshot: Boolean)

    fun analyze(file: File, includeBasmala: Boolean = false, indexSnapshot: String? = null): Result {
        val text: String
        val usedSnapshot: Boolean
        if (file.isFile && file.canRead()) {
            val parsed = TikaExtractor.parse(file)
            if (parsed.status != 200 || parsed.content.isNullOrBlank()) {
                throw IOException("متن سند «${file.name}» استخراج نشد؛ فرمت، رمزگذاری یا سلامت فایل را بررسی کنید.")
            }
            text = parsed.content
            usedSnapshot = false
        } else if (!indexSnapshot.isNullOrBlank()) {
            text = indexSnapshot
            usedSnapshot = true
        } else {
            throw IOException("سند «${file.name}» در دسترس نیست یا اجازهٔ خواندن ندارد.")
        }
        return Result(Matcher.all(text, includeBasmala), usedSnapshot)
    }
}
