package ir.matnyaab.ui

import ir.matnyaab.core.*
import ir.matnyaab.util.PersianUtil
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.Window
import java.awt.Desktop
import java.net.URI
import java.time.LocalDateTime

/**
 * معادل SubscriptionDialog (licensedlg.py) — پنجره اشتراک:
 * نمایش شناسه سیستم، دکمه کپی، فیلد سریال، بررسی سریال، خرید اشتراک (سایت)،
 * وضعیت اشتراک با تاریخ جلالی شروع/پایان و طول دوره
 */
class SubscriptionDialog(
    owner: Window?,
    private val settings: AppSettingsManager,
    private val license: LicenseChecker,
) {
    private val stage = Stage(StageStyle.UNDECORATED)
    private val lblStatus = Label()

    private fun cyanButton(text: String) = Button(text).apply {
        style = "${Theme.FONT} -fx-background-color: #5cc0d4; -fx-text-fill: black; -fx-background-radius: 4px; -fx-cursor: hand;"
        prefHeight = Constants.OPTION_BUTTONS_HEIGHT
        maxWidth = Double.MAX_VALUE
        setOnMouseEntered { style = style.replace("#5cc0d4", "#6fe7ff") }
        setOnMouseExited { style = style.replace("#6fe7ff", "#5cc0d4") }
    }

    init {
        stage.initModality(Modality.APPLICATION_MODAL)
        owner?.let { stage.initOwner(it) }

        val lblSysIdTitle = Label("شناسه سیستم شما:").styled()
        val txtSysId = TextArea(license.systemId).apply {
            isEditable = false; prefRowCount = 1; style = "-fx-font-family: monospace;"
        }
        val btnCopy = cyanButton("کپی شناسه سیستم").apply {
            setOnAction {
                Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(license.systemId) })
            }
        }

        val lblSerialTitle = Label("سریال اشتراک:").styled()
        val txtSerial = TextArea(settings.get("SERIAL")).apply { prefRowCount = 2 }
        val btnCheck = cyanButton("بررسی سریال").apply {
            setOnAction {
                license.serial = txtSerial.text.trim()
                val (start, end, duration) = license.validateSerial(true) { msg ->
                    CustomMessageBox.show(stage, "اشتراک", msg)
                }
                if (license.isSubscribed()) {
                    settings.setValue("SERIAL", license.serial)
                    updateStatus()
                    CustomMessageBox.show(stage, "اشتراک", "اشتراک با موفقیت فعال شد. لطفا برنامه را بسته و دوباره اجرا کنید.")
                } else if (end == LocalDateTime.MIN) {
                    CustomMessageBox.show(stage, "اشتراک", "خطا در سیستم اشتراک!")
                }
            }
        }
        val btnBuy = cyanButton("خرید اشتراک").apply {
            setOnAction {
                try { Desktop.getDesktop().browse(URI("https://www.matnyaab.ir")) } catch (e: Exception) {}
            }
        }

        updateStatus()

        val content = VBox(12.0, lblSysIdTitle, txtSysId, btnCopy, lblSerialTitle, txtSerial, btnCheck, btnBuy, lblStatus).apply {
            padding = Insets(18.0); alignment = Pos.TOP_RIGHT; style = "-fx-background-color: white;"
        }
        val root = BorderPane().apply {
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            top = CustomTitleBar(stage, "اشتراک", showMaximize = false)
            center = content
            style = "-fx-border-color: ${Constants.BORDER_COLOR};"
        }
        stage.scene = Scene(root, Constants.LICENSE_WINDOW_WIDTH, Constants.LICENSE_WINDOW_HEIGHT)
    }

    private fun Label.styled(): Label { style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px;"; return this }

    private fun updateStatus() {
        if (license.isSubscribed()) {
            val start = PersianUtil.toPersianNumbers(PersianUtil.toJalali(license.licenseStart))
            val end = PersianUtil.toPersianNumbers(PersianUtil.toJalali(license.licenseEnd))
            val dur = PersianUtil.toPersianNumbers(license.duration)
            lblStatus.text = "اشتراک فعال — شروع: $start | پایان: $end | طول دوره: $dur روز"
            lblStatus.style = "${Theme.FONT} -fx-text-fill: green; -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px;"
        } else {
            lblStatus.text = "نسخه آزمایشی، محدودیت فهرست گیری 25 مگابایت"
            lblStatus.style = "${Theme.FONT} -fx-text-fill: red; -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px;"
        }
    }

    fun show() = stage.showAndWait()
}
