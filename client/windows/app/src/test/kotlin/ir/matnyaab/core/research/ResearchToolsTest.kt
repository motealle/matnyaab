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
        val f = File.createTempFile("mx", ".docx")
        DocxWriter(DocxWriter.Layout.paper("وزیری")).title("کتاب آزمایشی").toc().heading(1, "فصل ۱").quote("متن نقل‌قول <با> & علائم", "منبع ۱")
            .para("بند با پانویس", "پانویس").table(listOf("الف", "ب"), listOf(listOf("۱", "۲"))).save(f)
        ZipFile(f).use { z ->
            listOf("[Content_Types].xml", "word/document.xml", "word/styles.xml", "word/footnotes.xml").forEach { assertNotNull(z.getEntry(it), it) }
            val doc = z.getInputStream(z.getEntry("word/document.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(doc.contains("&lt;با&gt; &amp;"))
            javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(z.getInputStream(z.getEntry("word/document.xml")))
        }
        val md = File.createTempFile("mx", ".md")
        ReportWriter(md).title("عنوان").quote("نقل", "منبع").save()
        assertTrue(md.readText().contains("> نقل"))
    }

    @Test fun lenientJson() {
        val j = AiClient.json("بفرمایید:\n```json\n{\"scenario\":\"FISH\",\"topic\":\"توکل\"}\n```")
        assertEquals("توکل", j!!.asJsonObject.get("topic").asString)
    }
}
