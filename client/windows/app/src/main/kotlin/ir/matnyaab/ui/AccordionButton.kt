package ir.matnyaab.ui

import ir.matnyaab.core.Constants
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.Node

/**
 * معادل customButton در additionalWidget.py — دکمه آکاردئونی با عنوان و محتوای بازشونده.
 * فقط یک گزینه در آن واحد باز می‌ماند (onlyOneOptionOpen).
 */
class AccordionButton(
    val optionId: Int,
    title: String,
    private val content: Node,
    private val onOpen: (Int) -> Unit,
) : VBox() {

    private val extendLabel = Label(Theme.GLYPH_DOWN).apply { style = Theme.awesome(11) + "-fx-text-fill: white;" }
    private val header = HBox()
    var isOpen = false; private set

    init {
        val lblTitle = Label(title).apply {
            style = "${Theme.FONT} -fx-text-fill: white; -fx-font-size: ${Constants.FONT_SIZE_NORMAL}px;"
        }
        val spacer = Region().also { HBox.setHgrow(it, Priority.ALWAYS) }
        header.children.addAll(extendLabel, spacer, lblTitle)
        header.alignment = Pos.CENTER_RIGHT
        header.padding = Insets(0.0, 12.0, 0.0, 12.0)
        header.prefHeight = Constants.OPTION_HEIGHT
        header.minHeight = Constants.OPTION_HEIGHT
        setHeaderStyle(false)
        header.setOnMouseClicked { toggle() }
        header.setOnMouseEntered { if (!isOpen) header.style = headerStyle(Constants.BUTTON_HOVER_BACKGROUND_COLOR) }
        header.setOnMouseExited { setHeaderStyle(isOpen) }

        content.isVisible = false
        content.isManaged = false
        children.addAll(header, content)
    }

    private fun headerStyle(color: String) =
        "-fx-background-color: $color; -fx-background-radius: 6px; -fx-cursor: hand;"

    private fun setHeaderStyle(open: Boolean) {
        header.style = headerStyle(if (open) Constants.BUTTON_HOVER_BACKGROUND_COLOR else Constants.BUTTON_BACKGROUND_COLOR)
        extendLabel.text = if (open) Theme.GLYPH_UP else Theme.GLYPH_DOWN
    }

    fun toggle() { if (isOpen) collapse() else expand() }

    fun expand() {
        isOpen = true
        content.isVisible = true
        content.isManaged = true
        setHeaderStyle(true)
        onOpen(optionId)
    }

    fun collapse() {
        isOpen = false
        content.isVisible = false
        content.isManaged = false
        setHeaderStyle(false)
    }
}
