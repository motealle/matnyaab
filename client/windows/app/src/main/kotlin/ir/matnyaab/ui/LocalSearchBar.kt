package ir.matnyaab.ui

import ir.matnyaab.core.Constants
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.Window

/**
 * معادل localSearchDialog — نوار جستجوی محلی ۴۰۰×۴۶ درون پیش‌نمایش:
 * فیلد متن (LTR)، دکمه‌های بالا/پایین، شمارنده، دکمه بستن
 */
class LocalSearchBar(
    owner: Window?,
    private val onFind: (String) -> Int,
    private val onNext: () -> String,
    private val onPrev: () -> String,
    private val onClose: () -> Unit,
) {
    private val stage = Stage(StageStyle.UNDECORATED)
    private val input = TextField().apply {
        promptText = "واژه مورد نظر را وارد نمایید"
        nodeOrientation = NodeOrientation.LEFT_TO_RIGHT
        prefWidth = 200.0
    }
    private val counter = Label("0/0").apply { style = Theme.FONT }

    init {
        owner?.let { stage.initOwner(it) }
        fun glyphBtn(g: String, action: () -> Unit) = Button(g).apply {
            style = Theme.awesome(11) + "-fx-background-color: ${Constants.BUTTON_BACKGROUND_COLOR}; -fx-text-fill: white; -fx-background-radius: 3px; -fx-cursor: hand;"
            setOnAction { action() }
        }
        val btnUp = glyphBtn(Theme.GLYPH_UP) { counter.text = onPrev() }
        val btnDown = glyphBtn(Theme.GLYPH_DOWN) { counter.text = onNext() }
        val btnClose = glyphBtn(Theme.GLYPH_CLOSE) { stage.close(); onClose() }

        input.textProperty().addListener { _, _, text ->
            val n = onFind(text)
            counter.text = "0/$n"
        }
        input.setOnAction { counter.text = onNext() }

        val row = HBox(6.0, btnClose, counter, btnUp, btnDown, input).apply {
            alignment = Pos.CENTER
            style = "-fx-background-color: white; -fx-border-color: ${Constants.BUTTON_BACKGROUND_COLOR}; -fx-border-width: 2px; -fx-padding: 6;"
        }
        stage.scene = Scene(row, 400.0, 46.0)
        stage.isAlwaysOnTop = true
    }

    fun showAt(x: Double, y: Double) { stage.x = x; stage.y = y; stage.show(); input.requestFocus() }
    fun isShowing() = stage.isShowing
    fun close() = stage.close()
}
