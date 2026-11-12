package ir.matnyaab.ui

import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.Constants
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.Window

/**
 * معادل AppSettingsDialog — تنظیمات:
 * حافظه موتور استخراج متن (TIKA_MEM) + اسلایدر اندازه فونت برنامه (۵۰ تا ۱۵۰ درصد)
 */
class SettingsDialog(owner: Window?, private val settings: AppSettingsManager) {
    private val stage = Stage(StageStyle.UNDECORATED)
    private var saved = false

    init {
        stage.initModality(Modality.APPLICATION_MODAL)
        owner?.let { stage.initOwner(it) }

        val cmbMem = ComboBox(FXCollections.observableArrayList("256M", "512M", "1G", "2G", "4G", "8G", "16G")).apply {
            selectionModel.select(settings.get("TIKA_MEM"))
        }
        val slider = Slider(50.0, 150.0, settings.get("FONT_RATE").toDoubleOrNull() ?: 100.0).apply {
            isShowTickMarks = true; isShowTickLabels = true; majorTickUnit = 25.0
        }
        val original = Theme.palette.id
        val cmbTheme = ComboBox(FXCollections.observableArrayList(Theme.PALETTES.map { it.title })).apply {
            selectionModel.select(Theme.PALETTES.indexOfFirst { it.id == original }.coerceAtLeast(0))
            maxWidth = Double.MAX_VALUE
            // پیش‌نمایش زنده: با انتخاب، همهٔ پنجره‌ها بلافاصله رنگ می‌گیرند
            setOnAction { Theme.setPalette(Theme.PALETTES[selectionModel.selectedIndex].id) }
        }
        val fonts = AppFonts.choices()
        val cmbFont = ComboBox(FXCollections.observableArrayList(fonts.map { it.second })).apply {
            selectionModel.select(fonts.indexOfFirst { it.first == Theme.uiFont() }.coerceAtLeast(0))
            maxWidth = Double.MAX_VALUE
        }
        val feats = listOf("FF_RESEARCH" to "فیش‌ها، پروژه‌ها و ساخت کتاب", "FF_SOURCE" to "منبع‌یاب و همانندجویی", "FF_QURAN" to "کشف‌الآیات", "FF_AI" to "دستیار هوشمند (با کلید هوش مصنوعی خودتان)", "FF_WHISPER" to "تبدیل گفتار به متن (بستهٔ اختیاری Whisper)")
            .map { (k, t) -> k to CheckBox(t).apply { isSelected = settings.get(k) != "0"; style = Theme.FONT } }
        val btnSave = Button("ذخیره").apply {
            style = Theme.primaryButton(); prefWidth = 120.0; prefHeight = Constants.OPTION_BUTTONS_HEIGHT
            setOnMouseEntered { style = Theme.primaryButtonHover() }
            setOnMouseExited { style = Theme.primaryButton() }
            setOnAction {
                saved = true
                val featChanged = feats.any { (k, c) -> (settings.get(k) != "0") != c.isSelected }
                feats.forEach { (k, c) -> settings.setValue(k, if (c.isSelected) "1" else "0") }
                if (featChanged) CustomMessageBox.show(stage, "تنظیمات", "نوار ابزار از اجرای بعدی برنامه با قابلیت‌های انتخابی شما نمایش داده می‌شود.")
                settings.setValue("TIKA_MEM", cmbMem.value)
                val oldFont = settings.get("UI_FONT")
                fonts.getOrNull(cmbFont.selectionModel.selectedIndex)?.let { settings.setValue("UI_FONT", it.first) }
                val oldRate = settings.get("FONT_RATE")
                settings.setValue("FONT_RATE", slider.value.toInt())
                if (oldRate != slider.value.toInt().toString() || oldFont != settings.get("UI_FONT"))
                    CustomMessageBox.show(stage, "تنظیمات", "پوسته همین حالا اعمال شد. قلم و اندازهٔ تازه هم از اجرای بعدی برنامه سر جایشان می‌نشینند. 🌷")
                stage.close()
            }
        }

        val grid = GridPane().apply {
            hgap = 10.0; vgap = 14.0
            add(Label("پوستهٔ رنگی:").styled(), 0, 0); add(cmbTheme, 1, 0)
            add(Label("قلم برنامه:").styled(), 0, 1); add(cmbFont, 1, 1)
            add(Label("اندازهٔ قلم برنامه (درصد):").styled(), 0, 2); add(slider, 1, 2)
            add(Label("حافظهٔ موتور استخراج متن:").styled(), 0, 3); add(cmbMem, 1, 3)
            add(Label("قابلیت‌ها:").styled(), 0, 4); add(VBox(6.0, *feats.map { it.second }.toTypedArray()), 1, 4)
        }
        val content = VBox(20.0, grid, btnSave).apply {
            padding = Insets(20.0); style = "-fx-background-color: ${Constants.OPTION_BACKGROUND_COLOR};"
        }
        val root = BorderPane().apply {
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            top = CustomTitleBar(stage, "تنظیمات متن‌یاب", showMaximize = false)
            center = content
            style = "-fx-border-color: ${Constants.BORDER_COLOR};"
        }
        stage.scene = Scene(root, 560.0, 580.0)
        Theme.install(stage.scene, stage)
        // بستن بدون ذخیره، پوستهٔ قبلی را برمی‌گرداند
        stage.setOnHidden { if (!saved) Theme.setPalette(original) }
    }

    private fun Label.styled(): Label { style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px;"; return this }

    fun show() = stage.showAndWait()
}
