package ir.matnyaab.ui

import ir.matnyaab.core.Constants

/** سبک‌های مشترک — معادل استایل‌شیت‌های QSS در نسخه PyQt */
object Theme {
    val FONT = "-fx-font-family: '${Constants.GENERAL_FONT_NAME_PERSIAN}', '${Constants.GENERAL_FONT_NAME_LATIN}';"

    fun primaryButton(radius: Int = 6): String = """
        -fx-background-color: ${Constants.BUTTON_BACKGROUND_COLOR};
        -fx-text-fill: white;
        -fx-background-radius: ${radius}px;
        -fx-cursor: hand;
        $FONT
    """.trimIndent()

    fun primaryButtonHover(): String = primaryButton().replace(
        Constants.BUTTON_BACKGROUND_COLOR, Constants.BUTTON_HOVER_BACKGROUND_COLOR)

    fun awesome(size: Int): String =
        "-fx-font-family: '${Constants.AWESOME_FONT_FAMILY}'; -fx-font-size: ${size}px;"

    const val GLYPH_SEARCH = "⌕"
    const val GLYPH_SETTINGS = "⚙"
    const val GLYPH_UP = "▲"
    const val GLYPH_DOWN = "▼"
    const val GLYPH_PLUS = "+"
    const val GLYPH_CLOSE = "×"
    const val GLYPH_MINIMIZE = "−"
    const val GLYPH_MAXIMIZE = "□"
    const val GLYPH_RESTORE = "▣"
    const val GLYPH_HELP = "?"
    const val GLYPH_KEY = "⚿"
    const val GLYPH_REFRESH = "↻"
    const val GLYPH_FOLDER_OPEN = "▣"
    const val GLYPH_DOWNLOAD = "↓"
    const val GLYPH_CIRCLE = "●"
    const val GLYPH_FONT = "A"
    const val GLYPH_ZOOM_IN = "＋"
    const val GLYPH_ZOOM_OUT = "−"
    const val GLYPH_ALIGN_RIGHT = "≡"
    const val GLYPH_ALIGN_LEFT = "≡"
    const val GLYPH_ALIGN_CENTER = "≡"
}
