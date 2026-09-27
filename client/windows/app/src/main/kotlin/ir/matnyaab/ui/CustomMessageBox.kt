package ir.matnyaab.ui

import ir.matnyaab.core.Constants
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.Window

/**
 * معادل CustomMessageBox — پیام‌باکس فارسی با نوار عنوان سفارشی و دکمه‌های دلخواه.
 * @return اندیس دکمه فشرده‌شده (یا -1)
 */
object CustomMessageBox {
    fun show(owner: Window?, title: String, message: String, buttons: List<String> = listOf("تایید")): Int {
        var result = -1
        val stage = Stage(StageStyle.UNDECORATED)
        owner?.let { stage.initOwner(it) }
        stage.initModality(Modality.APPLICATION_MODAL)

        val titleBar = CustomTitleBar(stage, title, showMaximize = false)
        val lbl = Label(message).apply {
            isWrapText = true
            style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px;"
        }
        val buttonRow = HBox(10.0).apply { alignment = Pos.CENTER }
        buttons.forEachIndexed { i, b ->
            buttonRow.children.add(Button(b).apply {
                style = Theme.primaryButton()
                prefWidth = 110.0; prefHeight = Constants.OPTION_BUTTONS_HEIGHT
                setOnMouseEntered { style = Theme.primaryButtonHover() }
                setOnMouseExited { style = Theme.primaryButton() }
                setOnAction { result = i; stage.close() }
            })
        }
        val center = VBox(20.0, lbl, buttonRow).apply {
            padding = Insets(20.0)
            alignment = Pos.CENTER
            style = "-fx-background-color: white;"
        }
        val root = BorderPane().apply {
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            top = titleBar
            this.center = center
            style = "-fx-border-color: ${Constants.BORDER_COLOR}; -fx-border-width: 1;"
        }
        stage.scene = Scene(root, 420.0, 200.0)
        stage.showAndWait()
        return result
    }
}
