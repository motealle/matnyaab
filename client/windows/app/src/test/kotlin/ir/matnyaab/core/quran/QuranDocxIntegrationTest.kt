package ir.matnyaab.core.quran

import ir.matnyaab.core.TikaExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for the user-facing DOCX discovery path:
 * create a realistic Persian/Arabic Word document, extract it through Tika,
 * then run the same matcher used by QuranWindow.analyzeFile().
 */
class QuranDocxIntegrationTest {
    @Test
    fun extractsArabicVersesFromPersianDocxAndFindsThem() {
        val file = File.createTempFile("matnyaab-quran-discovery-", ".docx")
        try {
            XWPFDocument().use { doc ->
                doc.createParagraph().createRun().setText(
                    "MATNYAAB — آزمون عملی کشف‌الآیات. در این سند آیه زیر در میان متن توضیحی قرار گرفته است:"
                )
                // This excerpt is distinctive to Al-Baqarah 2:255. The shorter opening
                // phrase "الله لا إله إلا هو الحي القيوم" is shared with Aal-e-Imran 3:2.
                doc.createParagraph().createRun().setText(
                    "مَنْ ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ"
                )
                doc.createParagraph().createRun().setText(
                    "این عبارت بخشی از آیه ۲۵۵ سوره بقره است. برای کنترل دوم: قُلْ هُوَ اللَّهُ أَحَدٌ"
                )
                doc.createParagraph().createRun().setText(
                    "کنترل منفی: پژوهشگر باید پیش از نگارش، منابع را با دقت بخواند و فیش‌برداری کند."
                )
                file.outputStream().use { doc.write(it) }
            }

            val parsed = TikaExtractor.parse(file)
            assertEquals(200, parsed.status, "Tika باید سند Word را بدون خطا استخراج کند")
            val extracted = parsed.content.orEmpty()
            assertTrue(extracted.contains("يَشْفَعُ"), "متن عربی DOCX در خروجی Tika پیدا نشد: $extracted")

            // Exercise the exact production file-picker path; the poisoned index snapshot
            // must never replace freshly extracted DOCX content.
            val hits = QuranDocumentAnalyzer.analyze(
                file, includeBasmala = true, indexSnapshot = "این یک متنِ فهرست‌شدهٔ قدیمی و نامربوط است"
            ).hits
            assertTrue(
                hits.any { it.sura == 2 && it.aya == 255 && it.kind == QKind.PARTIAL },
                "قطعه متمایز آیه‌الکرسی شناسایی نشد. یافته‌ها: $hits"
            )
            assertTrue(
                hits.any { it.sura == 112 && it.aya == 1 },
                "عبارت سوره اخلاص شناسایی نشد. یافته‌ها: $hits"
            )
            assertTrue(
                hits.none { it.witness.contains("پژوهشگر باید پیش از نگارش") },
                "نثر فارسی عادی نباید به‌عنوان آیه گزارش شود."
            )
        } finally {
            file.delete()
        }
    }
}
