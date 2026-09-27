package ir.matnyaab.core

import org.apache.tika.Tika
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

    fun parse(file: File): ParseResult = try {
        val metadata = Metadata()
        val text = file.inputStream().use { tika.parseToString(it, metadata, -1) }
        if (text.isNotBlank()) ParseResult(text, 200)
        else ParseResult(metadata.toString(), 200) // مطابق نسخه اصلی: در نبود متن، متادیتا ذخیره می‌شود
    } catch (e: Throwable) {
        ParseResult(null, 500) // معادل خطای سرور تیکا
    }
}
