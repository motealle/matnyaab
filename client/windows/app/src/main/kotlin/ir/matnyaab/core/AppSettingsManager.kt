package ir.matnyaab.core

import java.nio.file.Files
import java.util.Properties

/**
 * معادل APPSettingsManager (QSettings INI) — ذخیره در settings.ini
 * کلیدها: TIKA_MEM, LAST_VISIT, SERIAL, FONT_RATE, PREVIEW_FONT_FAMILY, PREVIEW_FONT_SIZE
 * + کلیدهای contents/<id> برای مسیر فایل‌های دانلودشده
 */
class AppSettingsManager {
    private val file = Constants.APP_SETTINGS_DIR.resolve("settings.ini")
    private val props = Properties()

    private val defaults = mapOf(
        "TIKA_MEM" to "1G",
        "LAST_VISIT" to "1400-01-01 00:00:00",
        "SERIAL" to "",
        "FONT_RATE" to "100",
        "PREVIEW_FONT_FAMILY" to Constants.PREVIEW_DEFAULT_FONT,
        "PREVIEW_FONT_SIZE" to Constants.PREVIEW_DEFAULT_FONT_SIZE.toString(),
    )

    init {
        Files.createDirectories(Constants.APP_SETTINGS_DIR)
        if (Files.exists(file)) Files.newBufferedReader(file, Charsets.UTF_8).use { props.load(it) }
        var changed = false
        defaults.forEach { (k, v) -> if (!props.containsKey(k)) { props.setProperty(k, v); changed = true } }
        if (changed) save()
    }

    fun getSettings(): Map<String, String> =
        props.stringPropertyNames().associateWith { props.getProperty(it) }

    fun get(key: String): String = props.getProperty(key) ?: defaults[key] ?: ""

    fun setValue(key: String, value: Any?) {
        props.setProperty(key, value?.toString() ?: "")
        save()
    }

    private fun save() =
        Files.newBufferedWriter(file, Charsets.UTF_8).use { props.store(it, "MATNYAAB settings") }
}
