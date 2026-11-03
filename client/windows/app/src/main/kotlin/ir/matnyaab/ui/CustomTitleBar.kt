package ir.matnyaab.ui

import ir.matnyaab.core.Constants
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.geometry.Rectangle2D
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.stage.Screen
import javafx.stage.Stage

/**
 * نوار عنوان سفارشی: آیکون برنامه، عنوان، و دکمه‌های کمینه/بیشینه/بستن
 * با اندازهٔ استاندارد ویندوز ۱۱ (۴۸×۴۲) و نمادهای بومی Segoe Fluent/MDL2.
 */
class CustomTitleBar(
    private val stage: Stage,
    title: String,
    showMaximize: Boolean = true,
    private val onClose: () -> Unit = { stage.close() },
) : HBox() {

    private var dragX = 0.0
    private var dragY = 0.0
    private var restoreBounds: Rectangle2D? = null
    private val btnMax: Button

    init {
        style = "-fx-background-color: ${Constants.TITLE_BAR_COLOR}; " +
            "-fx-border-color: transparent transparent ${Constants.BORDER_COLOR} transparent;"
        prefHeight = Constants.MAIN_WINDOW_TITLE_BAR
        minHeight = Constants.MAIN_WINDOW_TITLE_BAR
        alignment = Pos.CENTER_RIGHT
        // جهت صریح LTR تا دکمه‌ها مطابق ویندوز همیشه سمت راست بمانند
        nodeOrientation = NodeOrientation.LEFT_TO_RIGHT
        padding = Insets(0.0, 0.0, 0.0, 10.0)

        val btnClose = makeButton(Theme.MDL_CLOSE, Theme.GLYPH_CLOSE, "بستن") { onClose() }
        btnClose.styleClass.add("titlebar-close")
        btnMax = makeButton(Theme.MDL_MAXIMIZE, Theme.GLYPH_MAXIMIZE, "بزرگ کردن") { toggleMaximize() }
        val btnMin = makeButton(Theme.MDL_MINIMIZE, Theme.GLYPH_MINIMIZE, "کوچک کردن") { stage.isIconified = true }

        val lblTitle = Label(title).apply {
            style = "-fx-text-fill: ${Constants.TITLE_BAR_TEXT_COLOR}; -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px; -fx-font-weight: bold; ${Theme.FONT}"
        }
        val icon = Theme.iconImage(22.0)?.let { ImageView(it) }

        val spacer = Region().also { setHgrow(it, Priority.ALWAYS) }
        icon?.let { children.add(it); setMargin(it, Insets(0.0, 8.0, 0.0, 2.0)) }
        children.add(lblTitle)
        children.add(spacer)
        children.add(btnMin)
        if (showMaximize) children.add(btnMax)
        children.add(btnClose)

        setOnMousePressed { e -> dragX = e.screenX - stage.x; dragY = e.screenY - stage.y }
        setOnMouseDragged { e ->
            if (restoreBounds == null) { stage.x = e.screenX - dragX; stage.y = e.screenY - dragY }
        }
        setOnMouseClicked { e -> if (e.clickCount == 2 && showMaximize) toggleMaximize() }
    }

    /**
     * بیشینه‌سازی دستی بر اساس visualBounds صفحه تا نوار وظیفهٔ ویندوز پوشانده نشود.
     */
    fun toggleMaximize() {
        if (restoreBounds == null) {
            restoreBounds = Rectangle2D(stage.x, stage.y, stage.width, stage.height)
            val screen = Screen.getScreensForRectangle(stage.x, stage.y, stage.width, stage.height).firstOrNull()
                ?: Screen.getPrimary()
            val vb = screen.visualBounds
            stage.x = vb.minX; stage.y = vb.minY
            stage.width = vb.width; stage.height = vb.height
            setGlyph(btnMax, Theme.MDL_RESTORE, Theme.GLYPH_RESTORE)
        } else {
            val b = restoreBounds!!
            restoreBounds = null
            stage.x = b.minX; stage.y = b.minY
            stage.width = b.width; stage.height = b.height
            setGlyph(btnMax, Theme.MDL_MAXIMIZE, Theme.GLYPH_MAXIMIZE)
        }
    }

    private fun setGlyph(b: Button, mdl: String, fallback: String) {
        val f = Theme.windowGlyphFont
        b.text = if (f != null) mdl else fallback
    }

    private fun makeButton(mdl: String, fallback: String, tip: String, action: () -> Unit): Button {
        val f = Theme.windowGlyphFont
        return Button(if (f != null) mdl else fallback).apply {
            styleClass.add("titlebar-button")
            style = if (f != null) "-fx-font-family: '$f'; -fx-font-size: 13px;"
                    else "-fx-font-family: '${Constants.AWESOME_FONT_FAMILY}'; -fx-font-size: 17px;"
            prefWidth = 48.0; minWidth = 48.0
            prefHeight = Constants.MAIN_WINDOW_TITLE_BAR; minHeight = Constants.MAIN_WINDOW_TITLE_BAR
            isFocusTraversable = false
            tooltip = Tooltip(tip)
            setOnAction { action() }
        }
    }
}
