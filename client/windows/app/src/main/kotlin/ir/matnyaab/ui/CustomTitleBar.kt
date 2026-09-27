package ir.matnyaab.ui

import ir.matnyaab.core.Constants
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.geometry.Rectangle2D
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.stage.Screen
import javafx.stage.Stage

/**
 * معادل CustomTitlebar.py — نوار عنوان سفارشی آبی با دکمه‌های بستن/بیشینه/کمینه و قابلیت جابجایی پنجره
 */
class CustomTitleBar(
    private val stage: Stage,
    title: String,
    showMaximize: Boolean = true,
    private val onClose: () -> Unit = { stage.close() },
) : HBox() {

    private var dragX = 0.0
    private var dragY = 0.0

    init {
        style = "-fx-background-color: ${Constants.TITLE_BAR_COLOR}; " +
            "-fx-border-color: transparent transparent ${Constants.BORDER_COLOR} transparent;"
        prefHeight = Constants.MAIN_WINDOW_TITLE_BAR
        minHeight = Constants.MAIN_WINDOW_TITLE_BAR
        alignment = Pos.CENTER_RIGHT
        // جهت صریح LTR تا دکمه‌ها همیشه سمت راست بمانند (حتی اگر والد RTL شود)
        nodeOrientation = NodeOrientation.LEFT_TO_RIGHT
        padding = Insets(0.0, 0.0, 0.0, 8.0)

        // دکمه‌ها سمت راست، مطابق چیدمان استاندارد ویندوز: کمینه، بیشینه، بستن
        val btnClose = makeButton(Theme.GLYPH_CLOSE) { onClose() }
        btnClose.styleClass.add("titlebar-close")
        val btnMax = makeButton(Theme.GLYPH_MAXIMIZE) { toggleMaximize() }
        val btnMin = makeButton(Theme.GLYPH_MINIMIZE) { stage.isIconified = true }

        val lblTitle = Label(title).apply {
            style = "-fx-text-fill: ${Constants.TITLE_BAR_TEXT_COLOR}; -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px; ${Theme.FONT}"
        }
        val icon = try {
            ImageView(Image(javaClass.getResourceAsStream("/image/green_icon.png"), 22.0, 22.0, true, true))
        } catch (e: Exception) { null }

        val spacer = Region().also { setHgrow(it, Priority.ALWAYS) }
        icon?.let { children.add(it); setMargin(it, Insets(0.0, 6.0, 0.0, 6.0)) }
        children.add(lblTitle)
        children.add(spacer)
        children.add(btnMin)
        if (showMaximize) children.add(btnMax)
        children.add(btnClose)

        // جابجایی پنجره با درگ کردن نوار عنوان
        setOnMousePressed { e -> dragX = e.screenX - stage.x; dragY = e.screenY - stage.y }
        setOnMouseDragged { e ->
            if (restoreBounds == null) { stage.x = e.screenX - dragX; stage.y = e.screenY - dragY }
        }
        setOnMouseClicked { e -> if (e.clickCount == 2 && showMaximize) toggleMaximize() }
    }

    private var restoreBounds: Rectangle2D? = null

    /**
     * بیشینه‌سازی دستی بر اساس visualBounds صفحه تا نوار وظیفه (تسک‌بار) ویندوز پوشانده نشود.
     * (stage.isMaximized در پنجره‌های UNDECORATED کل صفحه از جمله تسک‌بار را می‌گیرد)
     */
    private fun toggleMaximize() {
        if (restoreBounds == null) {
            restoreBounds = Rectangle2D(stage.x, stage.y, stage.width, stage.height)
            val screen = Screen.getScreensForRectangle(stage.x, stage.y, stage.width, stage.height).firstOrNull()
                ?: Screen.getPrimary()
            val vb = screen.visualBounds
            stage.x = vb.minX; stage.y = vb.minY
            stage.width = vb.width; stage.height = vb.height
        } else {
            val b = restoreBounds!!
            restoreBounds = null
            stage.x = b.minX; stage.y = b.minY
            stage.width = b.width; stage.height = b.height
        }
    }

    private fun makeButton(glyph: String, action: () -> Unit): Button =
        Button(glyph).apply {
            style = "${Theme.awesome(12)} -fx-background-color: transparent; -fx-text-fill: ${Constants.TITLE_BAR_TEXT_COLOR}; -fx-cursor: hand;"
            prefWidth = 45.0
            prefHeight = Constants.MAIN_WINDOW_TITLE_BAR
            setOnAction { action() }
            setOnMouseEntered {
                style = style + if (text == Theme.GLYPH_CLOSE)
                    "-fx-background-color: #c42b1c; -fx-text-fill: white;" else "-fx-background-color: #e9e9e9;"
            }
            setOnMouseExited {
                style = "${Theme.awesome(12)} -fx-background-color: transparent; -fx-text-fill: ${Constants.TITLE_BAR_TEXT_COLOR}; -fx-cursor: hand;"
            }
        }
}
