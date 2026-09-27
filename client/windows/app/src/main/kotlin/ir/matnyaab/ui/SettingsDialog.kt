package ir.matnyaab.ui

import ir.matnyaab.core.AppSettingsManager
import ir.matnyaab.core.Constants
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.scene.Scene
import javafx.scene.control.Button
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

    init {
        stage.initModality(Modality.APPLICATION_MODAL)
        owner?.let { stage.initOwner(it) }

        val cmbMem = ComboBox(FXCollections.observableArrayList("256M", "512M", "1G", "2G", "4G", "8G", "16G")).apply {
            selectionModel.select(settings.get("TIKA_MEM"))
        }
        val slider = Slider(50.0, 150.0, settings.get("FONT_RATE").toDoubleOrNull() ?: 100.0).apply {
            isShowTickMarks = true; isShowTickLabels = true; majorTickUnit = 25.0
        }
        val btnSave = Button("ذخیره").apply {
            style = Theme.primaryButton(); prefWidth = 120.0; prefHeight = Constants.OPTION_BUTTONS_HEIGHT
            setOnMouseEntered { style = Theme.primaryButtonHover() }
            setOnMouseExited { style = Theme.primaryButton() }
            setOnAction {
                settings.setValue("TIKA_MEM", cmbMem.value)
                settings.setValue("FONT_RATE", slider.value.toInt())
                CustomMessageBox.show(stage, "تنظیمات", "برای اعمال تنظمیات برنامه را بسته و دوباره اجرا کنید.")
                stage.close()
            }
        }

        val grid = GridPane().apply {
            hgap = 10.0; vgap = 14.0
            add(Label("حافظه موتور استخراج متن:").styled(), 0, 0); add(cmbMem, 1, 0)
            add(Label("اندازه فونت برنامه (درصد):").styled(), 0, 1); add(slider, 1, 1)
        }
        val content = VBox(20.0, grid, btnSave).apply {
            padding = Insets(20.0); style = "-fx-background-color: white;"
        }
        val root = BorderPane().apply {
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            top = CustomTitleBar(stage, "تنظیمات", showMaximize = false)
            center = content
            style = "-fx-border-color: ${Constants.BORDER_COLOR};"
        }
        stage.scene = Scene(root, 420.0, 260.0)
    }

    private fun Label.styled(): Label { style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px;"; return this }

    fun show() = stage.showAndWait()
}
