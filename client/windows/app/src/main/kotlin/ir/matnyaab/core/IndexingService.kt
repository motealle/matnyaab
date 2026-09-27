package ir.matnyaab.core

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * معادل IndexingThread و IncrementalIndexingThread در نسخه پایتون.
 * روی ترد جداگانه اجرا می‌شود؛ کالبک‌ها روی ترد UI صدا زده نمی‌شوند (خود UI باید Platform.runLater کند).
 */
class IndexingService(
    private val indexer: LuceneIndexer,
    private val license: LicenseChecker,
) {
    interface Callbacks {
        fun onFileStart(file: File) {}
        fun onFileDone(file: File) {}
        fun onFileError(file: File, error: String) {}
        fun onProgress(done: Int, total: Int) {}
        fun onLog(message: String) {}
        /** err: 0=موفق 1=سقف اشتراک 2=لغو 3=خطا */
        fun onFinish(err: Int) {}
    }

    @Volatile var cancelled = false

    private fun isHiddenOrSystem(f: File): Boolean = try {
        f.isHidden || f.name.startsWith(".") || f.name.startsWith("~$")
    } catch (e: Exception) { false }

    private fun listFiles(root: File, subFolders: Boolean): List<File> {
        val out = mutableListOf<File>()
        fun walk(dir: File) {
            val children = dir.listFiles() ?: return
            for (c in children) {
                if (isHiddenOrSystem(c)) continue
                if (c.isDirectory) { if (subFolders) walk(c) }
                else out.add(c)
            }
        }
        walk(root)
        return out
    }

    /**
     * فهرست‌گیری کامل یک پوشه — معادل startOperation + IndexingThread.run
     * @param formats پسوندهای مجاز (خالی = همه فرمت‌ها)
     */
    fun indexDirectory(
        indexName: String,
        rootPath: String,
        formats: Set<String>,
        minSize: Long,
        maxSize: Long,
        subFolders: Boolean,
        cb: Callbacks,
    ) {
        cancelled = false
        val startMs = System.currentTimeMillis()
        val savePath = indexer.makeIndexDir(rootPath)
        var usedSpace = indexer.usedSpace()
        val subscribed = license.isSubscribed()
        val entries = mutableListOf<FileEntry>()
        try {
            indexer.openWriter(savePath).use { writer ->
                val all = listFiles(File(rootPath), subFolders).filter { f ->
                    val ext = "." + f.extension.lowercase()
                    (formats.isEmpty() || ext in formats) && f.length() in minSize..maxSize
                }
                val total = all.size
                var done = 0
                for (f in all) {
                    if (cancelled) {
                        writer.rollback()
                        indexer.deleteIndexer(savePath.toString())
                        cb.onFinish(2); return
                    }
                    // محدودیت نسخه آزمایشی ۲۵ مگابایت
                    if (!subscribed && usedSpace + f.length() > Constants.LIMITED_TRIAL_SIZE) {
                        writer.commit()
                        indexer.saveFileInfo(savePath, indexName, rootPath)
                        indexer.saveFileList(savePath, entries)
                        indexer.loadIndexer(indexName, savePath.toString(), rootPath)
                        cb.onFinish(1); return
                    }
                    cb.onFileStart(f)
                    val parsed = TikaExtractor.parse(f)
                    if (parsed.status == 500 || parsed.content == null) {
                        cb.onFileError(f, "خطای سرور تیکا")
                    } else {
                        indexer.addDocument(
                            writer, f.name, f.absolutePath, f.length(),
                            f.lastModified() / 1000.0, parsed.content
                        )
                        entries.add(FileEntry(f.absolutePath, f.length(), f.lastModified() / 1000.0))
                        usedSpace += f.length()
                        cb.onFileDone(f)
                    }
                    done++
                    cb.onProgress(done, total)
                }
                writer.commit()
            }
            indexer.saveFileInfo(savePath, indexName, rootPath)
            indexer.saveFileList(savePath, entries)
            indexer.loadIndexer(indexName, savePath.toString(), rootPath)
            val secs = (System.currentTimeMillis() - startMs) / 1000
            cb.onLog("زمان کل فهرست‌گیری %02d:%02d".format(secs / 60, secs % 60))
            cb.onFinish(0)
        } catch (e: Exception) {
            cb.onLog("خطا: ${e.message}")
            cb.onFinish(3)
        }
    }

    /**
     * به‌روزرسانی افزایشی — معادل IncrementalIndexingThread:
     * مقایسه file_list.txt قبلی با فایل‌های فعلی دیسک؛
     * فایل‌های حذف‌شده از فهرست حذف، فایل‌های جدید/تغییرکرده افزوده می‌شوند.
     * اگر پوشه اصلی حذف شده باشد، کل فهرست حذف می‌شود.
     */
    fun incrementalUpdate(info: IndexerInfo, cb: Callbacks) {
        cancelled = false
        val savePath = Path.of(info.savePath)
        try {
            val root = File(info.rootPath)
            if (!root.isDirectory) {
                indexer.deleteIndexer(info.savePath)
                cb.onLog("حذف فهرست!")
                cb.onFinish(0); return
            }
            val oldList = indexer.readFileList(savePath).associateBy { it.filename }
            val current = listFiles(root, true)
            val currentByPath = current.associateBy { it.absolutePath }

            val newFiles = current.filter { f ->
                val old = oldList[f.absolutePath]
                old == null || old.time != f.lastModified() / 1000.0 || old.size != f.length()
            }
            val missing = oldList.keys.filter { it !in currentByPath }

            if (newFiles.isEmpty() && missing.isEmpty()) {
                cb.onLog("فهرست به روز است!")
                cb.onFinish(0); return
            }

            var usedSpace = indexer.usedSpace()
            val subscribed = license.isSubscribed()
            val entries = oldList.toMutableMap()

            indexer.openWriter(savePath).use { writer ->
                for (m in missing) {
                    if (cancelled) { writer.rollback(); cb.onFinish(2); return }
                    indexer.deleteByPath(writer, m)
                    entries.remove(m)
                    cb.onLog("حذف از فهرست: $m")
                }
                for (f in newFiles) {
                    if (cancelled) { writer.rollback(); cb.onFinish(2); return }
                    if (!subscribed && usedSpace + f.length() > Constants.LIMITED_TRIAL_SIZE) {
                        writer.commit()
                        indexer.saveFileList(savePath, entries.values.toList())
                        cb.onFinish(1); return
                    }
                    cb.onFileStart(f)
                    val parsed = TikaExtractor.parse(f)
                    if (parsed.status == 500 || parsed.content == null) {
                        cb.onFileError(f, "خطای سرور تیکا")
                    } else {
                        indexer.addDocument(writer, f.name, f.absolutePath, f.length(), f.lastModified() / 1000.0, parsed.content)
                        entries[f.absolutePath] = FileEntry(f.absolutePath, f.length(), f.lastModified() / 1000.0)
                        usedSpace += f.length()
                        cb.onLog("افزودن به فهرست: ${f.absolutePath}")
                        cb.onFileDone(f)
                    }
                }
                writer.commit()
            }
            indexer.saveFileInfo(savePath, info.indexName, info.rootPath)
            indexer.saveFileList(savePath, entries.values.toList())
            cb.onLog("فهرست ذخیره شد...")
            cb.onFinish(0)
        } catch (e: Exception) {
            cb.onLog("خطا: ${e.message}")
            cb.onFinish(3)
        }
    }

    companion object {
        /** معادل AllFileFormats در scanDirectoryDialog */
        val ALL_FILE_FORMATS = listOf(
            ".doc", ".docx", ".txt", ".ppt", ".pptx", ".pdf", ".one", ".jpg", ".zip",
            ".xls", ".xlsx", ".htm", ".html", ".mp3", ".mp4", ".xhtml", ".jpeg", ".png",
            ".gif", ".tiff", ".psd", ".bmp", ".ogg", ".rtf", ".epub", ".ott", ".odt",
            ".flv", ".3gp", ".xml", ".odf", ".rar", ".7zip", ".tar",
        )
        /** معادل ImportantFormats (انتخاب متداول) */
        val IMPORTANT_FORMATS = listOf(".doc", ".docx", ".txt", ".ppt", ".pptx", ".pdf", ".jpg", ".zip", ".one")
    }
}
