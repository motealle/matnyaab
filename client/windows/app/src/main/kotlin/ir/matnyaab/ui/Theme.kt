package ir.matnyaab.ui

import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.Constants
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.text.Font
import javafx.stage.Stage
import java.util.WeakHashMap

/** یک پوستهٔ رنگی کامل رابط کاربری */
data class ThemePalette(
    val id: String,
    val title: String,
    val dark: Boolean,
    val accent: String,
    val accentHover: String,
    val accentSoft: String,
    val accentText: String,
    val canvas: String,
    val surface: String,
    val surface2: String,
    val titlebar: String,
    val text: String,
    val muted: String,
    val border: String,
    val hover: String,
    val selected: String,
    val success: String,
    val danger: String,
    /** رنگ پس‌زمینه و متن پیش‌نمایش (HTML) */
    val previewBg: String,
    val previewText: String,
)

/**
 * سیستم طراحی متن‌یاب ۲:
 * - رنگ‌ها به صورت «looked-up color» روی ریشهٔ هر صحنه تعریف می‌شوند (‎-mx-accent و ...)
 *   تا همهٔ استایل‌های درون‌خطی با تغییر پوسته بلافاصله و بدون راه‌اندازی دوباره عوض شوند.
 * - رنگ‌های پایهٔ Modena (‎-fx-base و ...) هم تنظیم می‌شوند تا جدول‌ها، فیلدها و درخت‌ها
 *   در پوستهٔ تیره هم خوانا باشند.
 */
object Theme {

    val PALETTES = listOf(
        ThemePalette("blue", "آبی متن‌یاب", false,
            accent = "#0F6CBD", accentHover = "#115EA3", accentSoft = "#E8F1FB", accentText = "#0B4F8A",
            canvas = "#EEF2F7", surface = "#FFFFFF", surface2 = "#F7F9FC", titlebar = "#F7F9FC",
            text = "#1B1F24", muted = "#5B6573", border = "#DCE3EC", hover = "#EEF4FB", selected = "#D6E7F8",
            success = "#107C41", danger = "#C42B1C", previewBg = "#FFFFFF", previewText = "#1B1F24"),
        ThemePalette("emerald", "زمردی", false,
            accent = "#0E7C66", accentHover = "#0B6655", accentSoft = "#E5F4F0", accentText = "#095646",
            canvas = "#EDF3F1", surface = "#FFFFFF", surface2 = "#F6FAF8", titlebar = "#F6FAF8",
            text = "#17201D", muted = "#56645F", border = "#D7E3DF", hover = "#EAF5F1", selected = "#CFEBE3",
            success = "#107C41", danger = "#C42B1C", previewBg = "#FFFFFF", previewText = "#17201D"),
        ThemePalette("paper", "کاغذی (مطالعهٔ طولانی)", false,
            accent = "#8A5A2B", accentHover = "#744A22", accentSoft = "#F3E8D8", accentText = "#5E3C1A",
            canvas = "#EFE6D6", surface = "#FBF6EC", surface2 = "#F6EEDF", titlebar = "#F6EEDF",
            text = "#2B2118", muted = "#6E5E4C", border = "#E2D4BD", hover = "#F3E9D7", selected = "#EADAC0",
            success = "#3F7A2E", danger = "#B3261E", previewBg = "#FBF6EC", previewText = "#2B2118"),
        ThemePalette("violet", "یاسی", false,
            accent = "#6B4FBB", accentHover = "#5A41A3", accentSoft = "#EFEAFB", accentText = "#4A3591",
            canvas = "#F0EEF6", surface = "#FFFFFF", surface2 = "#F8F7FC", titlebar = "#F8F7FC",
            text = "#1E1B26", muted = "#5F5A6D", border = "#E0DCEB", hover = "#F1EDFB", selected = "#E0D7F6",
            success = "#107C41", danger = "#C42B1C", previewBg = "#FFFFFF", previewText = "#1E1B26"),
        ThemePalette("night", "شب (تیره)", true,
            accent = "#2E7BD1", accentHover = "#3A8AE0", accentSoft = "#1F3347", accentText = "#9CCBF6",
            canvas = "#15181D", surface = "#1E2229", surface2 = "#232830", titlebar = "#1A1E24",
            text = "#E6E9EE", muted = "#9AA4B2", border = "#323945", hover = "#2A313B", selected = "#27405A",
            success = "#3FB36B", danger = "#E5534B", previewBg = "#1E2229", previewText = "#E6E9EE"),
    )

    @Volatile var palette: ThemePalette = PALETTES.first(); private set
    private val scenes = WeakHashMap<Scene, String>()
    private val listeners = mutableListOf<(ThemePalette) -> Unit>()
    private var settings: AppSettingsManager? = null

    fun init(s: AppSettingsManager) {
        settings = s
        palette = PALETTES.firstOrNull { it.id == s.get("THEME") } ?: PALETTES.first()
    }

    fun setPalette(id: String) {
        val p = PALETTES.firstOrNull { it.id == id } ?: return
        palette = p
        settings?.setValue("THEME", p.id)
        scenes.keys.toList().forEach { applyVars(it) }
        listeners.toList().forEach { it(p) }
    }

    fun onChange(l: (ThemePalette) -> Unit) { listeners.add(l) }

    /** فونت رابط: Segoe UI (پشتیبانی کامل فارسی در ویندوز ۱۰/۱۱) با جایگزین Tahoma */
    val FONT get() = "-fx-font-family: '${uiFont()}';"

    private var cachedFont: String? = null
    fun uiFont(): String = cachedFont ?: AppFonts.best(settings?.get("UI_FONT")).also { cachedFont = it }
    private fun legacyUiFont(): String = run {
        val fams = try { Font.getFamilies() } catch (e: Exception) { emptyList<String>() }
        (listOf("Segoe UI", Constants.GENERAL_FONT_NAME_PERSIAN).firstOrNull { it in fams } ?: "System").also { cachedFont = it }
    }

    private fun rootVars(p: ThemePalette): String = """
        -mx-accent: ${p.accent}; -mx-accent-hover: ${p.accentHover}; -mx-accent-soft: ${p.accentSoft};
        -mx-accent-text: ${p.accentText}; -mx-canvas: ${p.canvas}; -mx-surface: ${p.surface};
        -mx-surface-2: ${p.surface2}; -mx-titlebar: ${p.titlebar}; -mx-text: ${p.text}; -mx-muted: ${p.muted};
        -mx-border: ${p.border}; -mx-hover: ${p.hover}; -mx-selected: ${p.selected};
        -mx-success: ${p.success}; -mx-danger: ${p.danger};
        -fx-base: ${if (p.dark) p.surface2 else "#ECEFF3"}; -fx-background: ${p.canvas};
        -fx-control-inner-background: ${p.surface}; -fx-control-inner-background-alt: ${p.surface2};
        -fx-accent: ${p.accent}; -fx-focus-color: ${p.accent}; -fx-faint-focus-color: ${p.accent}22;
        -fx-selection-bar: ${p.selected}; -fx-selection-bar-non-focused: ${p.selected};
        -fx-selection-bar-text: ${p.text}; -fx-text-background-color: ${p.text};
        -fx-text-base-color: ${p.text}; -fx-mark-color: ${p.text}; -fx-box-border: ${p.border};
        -fx-text-box-border: ${p.border}; -fx-outer-border: ${p.border};
        -fx-font-family: '${uiFont()}'; -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px;
    """.trimIndent().replace("\n", " ")

    private fun applyVars(scene: Scene) {
        val root = scene.root ?: return
        val orig = scenes[scene] ?: ""
        root.style = rootVars(palette) + " " + orig
    }

    /** نصب پوسته روی یک صحنه (و آیکون روی پنجره) — برای هر پنجرهٔ برنامه صدا زده می‌شود */
    fun install(scene: Scene, stage: Stage? = null) {
        if (!scenes.containsKey(scene)) scenes[scene] = scene.root?.style ?: ""
        javaClass.getResource("/css/app.css")?.toExternalForm()?.let {
            if (it !in scene.stylesheets) scene.stylesheets.add(it)
        }
        applyVars(scene)
        stage?.let { applyIcons(it) }
    }

    fun applyIcons(stage: Stage) {
        if (stage.icons.isNotEmpty()) return
        listOf(16, 32, 48, 64, 128, 256).forEach { size ->
            javaClass.getResourceAsStream("/image/icon-$size.png")?.use { stage.icons.add(Image(it)) }
        }
    }

    fun iconImage(size: Double): Image? =
        javaClass.getResourceAsStream("/image/icon-64.png")?.use { Image(it, size, size, true, true) }

    // ------------------------------------------------------------------ دکمه‌ها
    fun primaryButton(radius: Int = 8): String = """
        -fx-background-color: ${Constants.BUTTON_BACKGROUND_COLOR};
        -fx-text-fill: white;
        -fx-background-radius: ${radius}px;
        -fx-cursor: hand;
        -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px;
        $FONT
    """.trimIndent()

    fun primaryButtonHover(): String = primaryButton().replace(
        Constants.BUTTON_BACKGROUND_COLOR + ";", Constants.BUTTON_HOVER_BACKGROUND_COLOR + ";")

    /** دکمهٔ ثانویه/افزودن: زمینهٔ کم‌رنگ، قاب خط‌چین و متن هم‌رنگ پوسته */
    fun addButton(hover: Boolean = false): String = """
        -fx-background-color: ${if (hover) "-mx-selected" else "-mx-accent-soft"};
        -fx-text-fill: -mx-accent-text;
        -fx-border-color: -mx-accent;
        -fx-border-style: dashed;
        -fx-border-width: 1.4px;
        -fx-border-radius: 8px;
        -fx-background-radius: 8px;
        -fx-cursor: hand;
        -fx-font-weight: bold;
        -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px;
        $FONT
    """.trimIndent()

    fun successButton(): String = """
        -fx-background-color: -mx-success; -fx-text-fill: white; -fx-background-radius: 8px;
        -fx-cursor: hand; -fx-font-weight: bold; -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px; $FONT
    """.trimIndent()

    fun dangerButton(): String = """
        -fx-background-color: -mx-danger; -fx-text-fill: white; -fx-background-radius: 8px;
        -fx-cursor: hand; -fx-font-weight: bold; -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px; $FONT
    """.trimIndent()

    fun awesome(size: Int): String =
        "-fx-font-family: '${Constants.AWESOME_FONT_FAMILY}'; -fx-font-size: ${size}px;"

    /** آیکون‌های بومی ویندوز ۱۰/۱۱ برای دکمه‌های نوار عنوان (در صورت نبودن، نویسهٔ جایگزین) */
    val windowGlyphFont: String? by lazy {
        val fams = try { Font.getFamilies() } catch (e: Exception) { emptyList<String>() }
        listOf("Segoe Fluent Icons", "Segoe MDL2 Assets").firstOrNull { it in fams }
    }

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

    // نویسه‌های Segoe MDL2 / Fluent Icons
    const val MDL_MINIMIZE = ""
    const val MDL_MAXIMIZE = ""
    const val MDL_RESTORE = ""
    const val MDL_CLOSE = ""
}
