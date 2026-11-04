package ir.matnyaab.ui

import javafx.scene.text.Font

/**
 * قلم‌های فارسی متن‌یاب: وزیرمتن و ساحل همراه برنامه‌اند (در CI از بسته‌های OFL دریافت می‌شوند)
 * و ایران‌نازنین اگر روی ویندوز نصب باشد قابل انتخاب است.
 */
object AppFonts {
    private val bundled = mapOf(
        "Vazirmatn" to listOf("Vazirmatn-Regular.ttf", "Vazirmatn-Bold.ttf"),
        "Sahel" to listOf("Sahel.ttf", "Sahel-Bold.ttf"),
    )
    private val loaded = mutableSetOf<String>()

    fun load() {
        for ((family, files) in bundled) for (f in files) {
            try {
                javaClass.getResourceAsStream("/fonts/$f")?.use { s -> Font.loadFont(s, 13.0)?.let { loaded.add(family) } }
            } catch (e: Exception) { /* بدون قلم همراه، قلم ویندوز استفاده می‌شود */ }
        }
    }

    fun has(family: String): Boolean = family in loaded || family in systemFamilies()

    private fun systemFamilies(): List<String> = try { Font.getFamilies() } catch (e: Exception) { emptyList() }

    /** نام قلم ایران‌نازنین نصب‌شده (نام‌گذاری‌ها در بسته‌های مختلف فرق دارد) */
    fun nazanin(): String? = systemFamilies().firstOrNull { it.replace(" ", "").contains("IRNazanin", true) }
        ?: systemFamilies().firstOrNull { it.replace(" ", "").contains("Nazanin", true) }

    /** قلم‌های قابل انتخاب با برچسب فارسی */
    fun choices(): List<Pair<String, String>> = listOfNotNull(
        ("Vazirmatn" to "وزیرمتن").takeIf { has("Vazirmatn") },
        ("Sahel" to "ساحل").takeIf { has("Sahel") },
        nazanin()?.let { it to "ایران‌نازنین" },
        ("Segoe UI" to "Segoe UI").takeIf { has("Segoe UI") },
        ("Tahoma" to "تاهوما").takeIf { has("Tahoma") },
    )

    fun best(preferred: String?): String =
        preferred?.takeIf { it.isNotBlank() && has(it) } ?: listOf("Vazirmatn", "Segoe UI", "Tahoma").firstOrNull { has(it) } ?: "System"

    /** قلم پیام‌ها همیشه وزیرمتن (اگر در دسترس باشد) */
    val message: String get() = best("Vazirmatn")

    /** @font-face برای پیش‌نمایش (WebView) تا قلم‌های همراه آنجا هم کار کنند */
    fun fontFaceCss(): String = buildString {
        for ((family, files) in bundled) files.forEachIndexed { i, f ->
            val url = javaClass.getResource("/fonts/$f")?.toExternalForm() ?: return@forEachIndexed
            append("@font-face{font-family:'$family';src:url('$url');font-weight:${if (i == 0) 400 else 700};}")
        }
    }
}
