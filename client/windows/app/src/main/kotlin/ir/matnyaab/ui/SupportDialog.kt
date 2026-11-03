package ir.matnyaab.ui

import ir.matnyaab.core.Constants
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.Window
import java.awt.Desktop
import java.net.URI

/**
 * «حمایت از متن‌یاب» — جایگزین پنجرهٔ اشتراک.
 * متن‌یاب ۲ کاملاً رایگان و بدون محدودیت است؛ حمایت مالی داوطلبانه و اعتمادمحور
 * (کارت‌به‌کارت) انجام می‌شود و واریز از صفحهٔ اصلی سایت اعلام می‌شود.
 */
class SupportDialog(owner: Window?) {
    companion object {
        const val CARD_NUMBER = "5041721202227469"
        const val CARD_HOLDER = "محمد رمضانی"
        fun cardDisplay(): String = CARD_NUMBER.chunked(4).joinToString("  ")
    }

    private val stage = Stage(StageStyle.UNDECORATED)

    init {
        stage.initModality(Modality.APPLICATION_MODAL)
        owner?.let { stage.initOwner(it) }

        val badge = Label("رایگان و بدون محدودیت").apply { styleClass.add("badge-free") }
        val title = Label("متن‌یاب برای همه رایگان است").apply {
            style = "-fx-font-size: ${Constants.FONT_SIZE_LARGE}px; -fx-font-weight: bold;"
        }
        val body = Label(
            "همهٔ امکانات متن‌یاب بدون اشتراک، سریال یا سقف حجمی در اختیار شماست. " +
            "اگر متن‌یاب در پژوهش و کارتان مفید بوده، می‌توانید به‌دلخواه و به هر اندازه که مایلید " +
            "از ادامهٔ توسعهٔ آن حمایت کنید."
        ).apply { isWrapText = true; maxWidth = 440.0 }

        val lblCard = Label(cardDisplay()).apply {
            style = "-fx-font-family: 'Consolas','Segoe UI'; -fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: -mx-accent-text;"
            nodeOrientation = NodeOrientation.LEFT_TO_RIGHT
        }
        val lblHolder = Label("به نام $CARD_HOLDER").apply { styleClass.add("muted") }
        val btnCopy = Button("کپی شماره کارت").apply {
            style = Theme.addButton()
            setOnAction {
                Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(CARD_NUMBER) })
                text = "کپی شد ✓"
            }
        }
        val cardBox = VBox(6.0, Label("کارت‌به‌کارت").apply { styleClass.add("section-title") }, lblCard, lblHolder, btnCopy).apply {
            alignment = Pos.CENTER
            padding = Insets(14.0)
            styleClass.add("card")
        }

        val note = Label("پس از واریز، در صورت تمایل آن را از بخش «حمایت» در سایت اعلام کنید تا با سپاس ثبت شود.").apply {
            isWrapText = true; maxWidth = 440.0; styleClass.add("muted")
        }
        val btnSite = Button("اعلام واریز در سایت").apply {
            style = Theme.primaryButton(); prefHeight = Constants.OPTION_BUTTONS_HEIGHT; maxWidth = Double.MAX_VALUE
            setOnMouseEntered { style = Theme.primaryButtonHover() }
            setOnMouseExited { style = Theme.primaryButton() }
            setOnAction { try { Desktop.getDesktop().browse(URI(Constants.SUPPORT_URL)) } catch (e: Exception) {} }
        }
        val btnClose = Button("بستن").apply {
            style = Theme.addButton(); prefHeight = Constants.OPTION_BUTTONS_HEIGHT; maxWidth = Double.MAX_VALUE
            isCancelButton = true
            setOnAction { stage.close() }
        }
        val actions = HBox(10.0, btnSite, btnClose).apply {
            HBox.setHgrow(btnSite, javafx.scene.layout.Priority.ALWAYS)
            HBox.setHgrow(btnClose, javafx.scene.layout.Priority.ALWAYS)
        }

        val content = VBox(12.0, badge, title, body, cardBox, note, actions).apply {
            padding = Insets(20.0)
            style = "-fx-background-color: ${Constants.OPTION_BACKGROUND_COLOR};"
        }
        val root = BorderPane().apply {
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            top = CustomTitleBar(stage, "حمایت از متن‌یاب", showMaximize = false)
            center = content
            style = "-fx-border-color: ${Constants.BORDER_COLOR};"
        }
        root.prefWidth = 500.0
        stage.scene = Scene(root)
        Theme.install(stage.scene, stage)
    }

    fun show() = stage.showAndWait()
    internal fun showNonBlocking(): Stage { stage.show(); return stage }
}
