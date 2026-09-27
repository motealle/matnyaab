package ir.matnyaab.ui

import ir.matnyaab.core.AppSettingsManager
import javafx.geometry.NodeOrientation
import javafx.scene.Scene
import javafx.scene.control.ListView
import javafx.scene.text.Font
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.Window

/** معادل ChooseFontDialog — لیست فونت‌های سیستم؛ انتخاب = تغییر فونت پیش‌نمایش + ذخیره */
class ChooseFontDialog(
    owner: Window?,
    private val settings: AppSettingsManager,
    private val onFontChosen: (String) -> Unit,
) {
    private val stage = Stage(StageStyle.UNDECORATED)

    init {
        owner?.let { stage.initOwner(it) }
        val list = ListView<String>().apply {
            items.addAll(Font.getFamilies())
            selectionModel.selectedItemProperty().addListener { _, _, family ->
                if (family != null) {
                    settings.setValue("PREVIEW_FONT_FAMILY", family)
                    onFontChosen(family)
                }
            }
        }
        val scene = Scene(javafx.scene.layout.StackPane(list), 260.0, 300.0)
        scene.root.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
        stage.scene = scene
        stage.focusedProperty().addListener { _, _, focused -> if (!focused) stage.close() }
    }

    fun showAt(x: Double, y: Double) { stage.x = x; stage.y = y; stage.show() }
}
