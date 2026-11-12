package ir.matnyaab.core

import java.nio.file.Path
import java.nio.file.Paths

/** معادل constants.py — تمام ثابت‌های برنامه */
object Constants {
    const val APP_NAME = "MATNYAAB"
    const val APP_NAME_PERSIAN = "متن‌یاب"
    const val APP_VERSION = "3.1"
    /** نام لاتین نمایشی (پوشه تنظیمات کاربر همچنان APP_NAME است تا داده‌های قبلی حفظ شوند) */
    const val APP_NAME_LATIN = "Matnyaab"
    const val APP_VERSION_PERSIAN = "۳٫۱"
    const val SUPPORT_URL = "https://www.matnyaab.ir/subscribe"
    const val APP_ARCH = "x64"

    const val BASE_URL = "https://www.matnyaab.ir"
    const val APP_UPDATE_DOWNLOAD_URL = "$BASE_URL/update/MATNYAAB_${APP_ARCH}_setup.exe"
    const val APP_UPDATE_VERSION_URL = "$BASE_URL/update/version.txt"
    const val APP_STATISTICS_URL = "$BASE_URL/statistics/"
    const val APP_NEWS_URL = "$BASE_URL/news/"
    const val GET_CONTENTS_URL = "$BASE_URL/get_contents/?password=compat"
    const val DOWNLOAD_CONTENT_URL = "$BASE_URL/download_content/?id="
    const val DOWNLOAD_CONTENT_IMG_URL = "$BASE_URL/download_content_img/?id="

    // رنگ‌های رابط به «رنگ‌های جست‌وجوشونده» CSS اشاره می‌کنند و از پوستهٔ فعال (ThemePalette) مقدار می‌گیرند
    const val BUTTON_BACKGROUND_COLOR = "-mx-accent"
    const val BUTTON_HOVER_BACKGROUND_COLOR = "-mx-accent-hover"
    const val BORDER_COLOR = "-mx-border"
    const val OPTION_BACKGROUND_COLOR = "-mx-surface"
    const val TITLE_BAR_COLOR = "-mx-titlebar"
    const val TITLE_BAR_TEXT_COLOR = "-mx-text"
    const val CANVAS_COLOR = "-mx-canvas"
    const val SURFACE_COLOR = "-mx-surface-2"
    const val MUTED_TEXT_COLOR = "-mx-muted"
    const val LOCAL_SEARCH_HIGHLIGHT_COLOR = "#b1f0b4"
    const val GLOBAL_SEARCH_HIGHLIGHT_COLOR = "#fff0a3"
    const val SELECTED_SEARCH_HIGHLIGHT_COLOR = "#9ce6e2"
    const val DISABLED_COLOR = "#4d4a45"
    const val UPDATE_BUTTON_COLOR = "#FDD017"
    const val UPDATE_BUTTON_HOVER_COLOR = "#f6fc3f"
    const val UPDATE_BUTTON_PRESSED_COLOR = "#caa612"
    const val PREVIEW_ERROR_COLOR = "#FEBB7F"
    const val TABLE_HOVER_COLOR = "-mx-hover"
    const val TABLE_SELECTED_COLOR = "-mx-selected"

    const val WINDOWS_RADIUS = 11.0
    const val LEFT_FRAME_STRETCH_FACTOR = 3
    const val MIDDLE_FRAME_STRETCH_FACTOR = 2
    const val RIGHT_FRAME_STRETCH_FACTOR = 2
    const val INITIAL_ALIGN = "right"

    // فونت‌ها
    const val GENERAL_FONT_NAME_PERSIAN = "Tahoma"
    const val GENERAL_FONT_NAME_LATIN = "Tahoma"
    const val PREVIEW_DEFAULT_FONT = "Tahoma"
    const val PREVIEW_DEFAULT_FONT_SIZE = 14
    const val AWESOME_FONT_FAMILY = "Segoe UI Symbol"

    var FONT_CHANGE_RATE = 1.2
    val FONT_SIZE_NORMAL: Int get() = (12.5 * FONT_CHANGE_RATE).toInt()
    val FONT_SIZE_SMALL: Int get() = (10.5 * FONT_CHANGE_RATE).toInt()
    val FONT_SIZE_LARGE: Int get() = (15 * FONT_CHANGE_RATE).toInt()
    val OPTION_HEIGHT: Double get() = 34 * FONT_CHANGE_RATE
    val OPTION_BUTTONS_HEIGHT: Double get() = 0.8 * OPTION_HEIGHT
    val FISH_BARDARI_BUTTON: Double get() = 40 * FONT_CHANGE_RATE
    const val MAIN_WINDOW_TITLE_BAR = 42.0

    const val LICENSE_WINDOW_WIDTH = 500.0
    const val LICENSE_WINDOW_HEIGHT = 532.0
    const val FEHRESTGIRI_WINDOW_WIDTH = 560.0
    const val FEHRESTGIRI_WINDOW_HEIGHT = 520.0

    /** پوشه تنظیمات کاربر (معادل ConfigLocation\MATNYAAB در ویندوز، و ~/.config/MATNYAAB در لینوکس) */
    val APP_SETTINGS_DIR: Path by lazy {
        System.getProperty("matnyaab.home")?.let { return@lazy Paths.get(it) }
        val os = System.getProperty("os.name").lowercase()
        val base = if (os.contains("win"))
            Paths.get(System.getenv("APPDATA") ?: System.getProperty("user.home"))
        else Paths.get(System.getProperty("user.home"), ".config")
        base.resolve(APP_NAME)
    }
    val INDEX_DIR: Path get() = APP_SETTINGS_DIR.resolve("index")
    val CONTENTS_DIR: Path get() = APP_SETTINGS_DIR.resolve("contents")
}
