package ir.matnyaab.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** پس از حذف پارسرهای غیرضروری، فرمت‌های اصلی اسناد باید همچنان خوانده شوند */
class TikaFormatsTest {
    private fun tmp(ext: String) = File.createTempFile("mx-fmt", ext).apply { deleteOnExit() }

    private fun assertParsed(f: File, needle: String) {
        val r = TikaExtractor.parse(f)
        println("FMT ${f.extension}: status=${r.status} content=${r.content?.take(120)?.replace("\n", " ")}")
        assertTrue(r.status == 200 && r.content?.contains(needle) == true, "${f.extension} not parsed: ${r.content?.take(200)}")
    }

    @Test fun docx() {
        val f = tmp(".docx")
        org.apache.poi.xwpf.usermodel.XWPFDocument().use { d ->
            d.createParagraph().createRun().setText("سلام متن‌یاب docxword")
            f.outputStream().use { d.write(it) }
        }
        assertParsed(f, "docxword")
    }

    @Test fun xlsx() {
        val f = tmp(".xlsx")
        org.apache.poi.xssf.usermodel.XSSFWorkbook().use { w ->
            w.createSheet("a").createRow(0).createCell(0).setCellValue("xlsxcell")
            f.outputStream().use { w.write(it) }
        }
        assertParsed(f, "xlsxcell")
    }

    @Test fun pptx() {
        val f = tmp(".pptx")
        org.apache.poi.xslf.usermodel.XMLSlideShow().use { p ->
            p.createSlide().createTextBox().setText("pptxslide")
            f.outputStream().use { p.write(it) }
        }
        assertParsed(f, "pptxslide")
    }

    @Test fun pdf() {
        val f = tmp(".pdf")
        org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
            val page = org.apache.pdfbox.pdmodel.PDPage()
            doc.addPage(page)
            org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 14f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("pdftext")
                cs.endText()
            }
            doc.save(f)
        }
        assertParsed(f, "pdftext")
    }

    @Test fun rtf() = assertParsed(tmp(".rtf").apply { writeText("{\\rtf1\\ansi\\deff0 {\\fonttbl{\\f0 Arial;}}\\f0 rtfword\\par}") }, "rtfword")
    @Test fun html() = assertParsed(tmp(".html").apply { writeText("<html><head><meta charset=\"utf-8\"></head><body><p>htmlword</p></body></html>", Charsets.UTF_8) }, "htmlword")
    @Test fun txt() = assertParsed(tmp(".txt").apply { writeText("This is plain text with txtword inside it for detection.", Charsets.UTF_8) }, "txtword")
}
