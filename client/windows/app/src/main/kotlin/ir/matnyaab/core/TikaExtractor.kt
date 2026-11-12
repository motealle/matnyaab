package ir.matnyaab.core

import org.apache.tika.Tika
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.parser.Parser
import org.apache.tika.sax.BodyContentHandler
import org.apache.tika.sax.ContentHandlerDecorator
import org.xml.sax.Attributes
import org.apache.tika.metadata.Metadata
import java.io.File

/**
 * جایگزین سرور Tika و ماژول newtika:
 * به جای اجرای tika-server-1.28.jar با JRE مجزا، از کتابخانه Tika به صورت درون‌پردازه استفاده می‌شود.
 * مقدار حافظه (TIKA_MEM) از طریق متغیر JVM در اسکریپت اجرا اعمال می‌شود.
 * خروجی معادل parser.from_file: متن + وضعیت (200 موفق، 500 خطا)
 */
object TikaExtractor {
    data class ParseResult(val content: String?, val status: Int)

    private val tika = Tika().apply { maxStringLength = -1 }

    /**
     * PDF: پایان هر صفحه با نویسهٔ \f نشانه‌گذاری می‌شود تا خروجی‌ها شمارهٔ صفحه داشته باشند.
     * در جست‌وجو اثری ندارد (فاصله به شمار می‌آید). اگر به هر دلیل نشد، همان مسیر پیشین به کار می‌رود.
     */
    private fun pdfWithPages(file: File, metadata: Metadata): String? = runCatching {
        val parser = AutoDetectParser()
        val ctx = ParseContext().apply { set(Parser::class.java, parser) }
        val body = BodyContentHandler(-1)
        val depth = ArrayDeque<Boolean>()
        val h = object : ContentHandlerDecorator(body) {
            override fun startElement(uri: String?, localName: String?, qName: String?, atts: Attributes?) {
                depth.addLast(localName == "div" && atts?.getValue("class") == "page")
                super.startElement(uri, localName, qName, atts)
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                super.endElement(uri, localName, qName)
                if (depth.removeLastOrNull() == true) { val ff = charArrayOf('\u000C'); super.characters(ff, 0, 1) }
            }
        }
        file.inputStream().use { parser.parse(it, h, metadata, ctx) }
        body.toString()
    }.getOrNull()

    fun parse(file: File): ParseResult = try {
        val metadata = Metadata()
        val text = (if (file.extension.equals("pdf", true)) pdfWithPages(file, metadata) else null)
            ?: file.inputStream().use { tika.parseToString(it, metadata, -1) }
        if (text.isNotBlank()) ParseResult(text, 200)
        else ParseResult(metadata.toString(), 200) // مطابق نسخه اصلی: در نبود متن، متادیتا ذخیره می‌شود
    } catch (e: Throwable) {
        ParseResult(null, 500) // معادل خطای سرور تیکا
    }
}
