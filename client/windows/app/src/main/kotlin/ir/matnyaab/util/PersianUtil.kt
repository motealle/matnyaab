package ir.matnyaab.util

import com.github.mfathi91.time.PersianDate
import java.time.LocalDateTime

/** معادل TranslatePersianNumbers و HumanFileSize در additionalWidget.py */
object PersianUtil {
    private const val EN = "0123456789.-"
    private const val FA = "۰١٢٣٤٥٦٧٨٩٫٫"

    fun toPersianNumbers(input: Any?): String {
        val s = input?.toString() ?: return ""
        val sb = StringBuilder()
        for (ch in s) {
            val i = EN.indexOf(ch)
            sb.append(if (i >= 0) FA[i] else ch)
        }
        return sb.toString()
    }

    private val SUFFIXES = listOf(
        "بایت", "کیلوبایت", "مگابایت", "گیگابایت", "ترابایت",
        "پتابایت", "اگزابایت", "زتابایت", "یوتابایت"
    )

    /** معادل HumanFileSize: فرمت '{0:.3g}' + پسوند فارسی */
    fun humanFileSize(size: Long): String {
        var s = size.toDouble()
        var i = 0
        while (s >= 1024 && i < SUFFIXES.size - 1) { s /= 1024.0; i++ }
        val num = java.math.BigDecimal(s).round(java.math.MathContext(3)).stripTrailingZeros().toPlainString()
        return "$num ${SUFFIXES[i]}"
    }

    /** معادل createProperSize در جدول نتایج (لاتین) */
    fun properSize(sizeInByte: Long): String {
        val units = listOf("byte", "KB", "MB", "TB")
        var power = 0
        for (p in 0..3) {
            power = p
            if ((sizeInByte / Math.pow(1024.0, p.toDouble())).toLong() < 1024) break
        }
        val num = Math.round(sizeInByte / Math.pow(1024.0, power.toDouble()) * 100.0) / 100.0
        return "$num${units[power]}"
    }

    fun toJalali(dt: LocalDateTime): String {
        val pd = PersianDate.fromGregorian(dt.toLocalDate())
        return "%04d-%02d-%02d".format(pd.year, pd.monthValue, pd.dayOfMonth)
    }

    fun nowJalaliWithTime(): String {
        val now = LocalDateTime.now()
        val t = "%02d:%02d:%02d".format(now.hour, now.minute, now.second)
        return "${toJalali(now)} $t"
    }
}
