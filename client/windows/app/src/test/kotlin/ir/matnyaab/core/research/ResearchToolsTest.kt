package ir.matnyaab.core.research

import ir.matnyaab.core.ai.AiClient
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ResearchToolsTest {
    private val src = "انسان در مسیر خودسازی باید پیوسته با هوای نفس مبارزه کند و این مبارزه را جهاد اکبر نامیده‌اند. " +
        "پیامبر پس از بازگشت از جنگ فرمود شما از جهاد اصغر بازگشتید و جهاد اکبر بر شما باقی است."

    @Test fun similarityFindsEditedQuote() {
        val q = "انسان در مسیر خودسازی باید همواره با هوای نفس مبارزه کند و این مبارزه را جهاد اکبر نامیده اند"
        assertTrue(SourceFinder.similarity(src, q) > 0.6)
        assertTrue(SourceFinder.similarity(src, "قیمت نفت در بازارهای جهانی امروز کاهش یافت و سرمایه‌گذاران نگران شدند") < 0.2)
    }

    @Test fun sentencesSplit() {
        val s = SourceFinder.sentences(src)
        assertEquals(2, s.size)
    }

    @Test fun docxIsValid() {
        val f = File.createTempFile("mxt", ".docx")
        DocxWriter(DocxWriter.Layout.paper("وزیری")).title("کتاب آزمایشی").toc().heading(1, "فصل ۱").quote("متن نقل‌قول <با> & علائم", "منبع ۱")
            .para("بند با پانویس", "پانویس").table(listOf("الف", "ب"), listOf(listOf("۱", "۲"))).save(f)
        ZipFile(f).use { z ->
            listOf("[Content_Types].xml", "word/document.xml", "word/styles.xml", "word/footnotes.xml").forEach { assertNotNull(z.getEntry(it), it) }
            val doc = z.getInputStream(z.getEntry("word/document.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(doc.contains("&lt;با&gt; &amp;"))
            javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(z.getInputStream(z.getEntry("word/document.xml")))
        }
        val md = File.createTempFile("mxt", ".md")
        ReportWriter(md).title("عنوان").quote("نقل", "منبع").save()
        assertTrue(md.readText().contains("> نقل"))
    }

    @Test fun lenientJson() {
        val j = AiClient.json("بفرمایید:\n```json\n{\"scenario\":\"FISH\",\"topic\":\"توکل\"}\n```")
        assertEquals("توکل", j!!.asJsonObject.get("topic").asString)
    }
}

class ExportTest {
    @Test fun contextWindowsAndXlsx() {
        val content = "بند اول دربارهٔ موضوعی دیگر است.\nدر این بند از تهذیب نفس سخن رفته و پیرامونش چند واژهٔ دیگر هست.\n\u000Cبند سوم در صفحهٔ دوم تهذیب نفس را دوباره می‌آورد."
        val hit = ir.matnyaab.core.SearchHit("col", "/x/a.pdf", "a.pdf", 1, 10L, 0.0, content, matches = 2)
        val eng = ir.matnyaab.core.search.MatchEngine(ir.matnyaab.core.search.SearchSpec(listOf("تهذیب نفس")))
        val ex = ResultExporter.excerpts(listOf(hit), eng, ResultExporter.Options(ResultExporter.Unit.PARAGRAPH))
        assertEquals(2, ex.size)
        assertTrue(ex[0].text.startsWith("در این بند"))
        assertEquals("تهذیب نفس", ex[0].text.substring(ex[0].marks[0].first, ex[0].marks[0].last + 1))
        assertEquals(1, ex[0].page); assertEquals(2, ex[1].page); assertEquals(2, ex[0].paragraph)
        val w = ResultExporter.excerpts(listOf(hit), eng, ResultExporter.Options(ResultExporter.Unit.WORDS, 2, 2))
        assertTrue(w[0].text.split(' ').size <= 7, w[0].text)
        val f = File.createTempFile("mxt", ".xlsx"); ResultExporter.saveXlsx(f, listOf("تهذیب نفس"), ex, ResultExporter.Options())
        ZipFile(f).use { z -> javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(z.getInputStream(z.getEntry("xl/worksheets/sheet1.xml"))) }
        val d = File.createTempFile("mxt", ".docx"); ResultExporter.saveDocx(d, "t", listOf("تهذیب نفس"), ex, ResultExporter.Options())
        ZipFile(d).use { z -> javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(z.getInputStream(z.getEntry("word/document.xml"))) }
    }
}
