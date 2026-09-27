package ir.matnyaab.ui

import ir.matnyaab.core.Constants
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

/** داده‌های جستجوی پیشرفته — معادل خروجی advanceSearching */
data class AdvancedSearchParams(
    val allWords: String, val allAccuracy: Boolean,
    val anyWords: String, val anyAccuracy: Boolean,
    val noneWords: String, val noneAccuracy: Boolean,
    val spaceBetween: Int,
)

/**
 * معادل mainSearchWidget — جستجوی سریع + جستجوی پیشرفته
 * (همه کلمات / یکی از کلمات / هیچ‌کدام از کلمات + چک‌باکس دقیق + حداکثر فاصله بین کلمات)
 */
class SearchPanel(
    private val onQuickSearch: (String) -> Unit,
    private val onAdvancedSearch: (AdvancedSearchParams) -> Unit,
) : VBox(8.0) {

    private val quickInput = TextField().apply { promptText = "جستجوی سریع" }

    private val allInput = AdvanceInput("همه کلمات")
    private val anyInput = AdvanceInput("یکی از کلمات")
    private val noneInput = AdvanceInput("هیچکدام از کلمات")
    private val spaceInput = TextField("0").apply {
        prefWidth = 70.0
        textProperty().addListener { _, old, new ->
            if (!new.matches(Regex("\\d{0,4}"))) text = old
            // مطابق نسخه اصلی: فاصله > 0 گزینه دقیق «همه کلمات» را فعال و قفل می‌کند
            val v = text.toIntOrNull() ?: 0
            if (v > 0) { allInput.accuracy.isSelected = true; allInput.accuracy.isDisable = true }
            else allInput.accuracy.isDisable = false
        }
    }

    init {
        nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
        padding = Insets(10.0)
        style = "-fx-background-color: ${Constants.OPTION_BACKGROUND_COLOR}; -fx-border-color: ${Constants.BORDER_COLOR}; -fx-border-radius: 6px;"

        // --- جستجوی سریع
        val btnQuick = Button(Theme.GLYPH_SEARCH).apply {
            style = Theme.awesome(13) + Theme.primaryButton()
            prefHeight = Constants.OPTION_BUTTONS_HEIGHT
            setOnAction { fireQuick() }
        }
        quickInput.prefHeight = Constants.OPTION_BUTTONS_HEIGHT
        quickInput.style = Theme.FONT
        quickInput.setOnAction { fireQuick() }
        val quickRow = HBox(6.0, quickInput, btnQuick).apply {
            alignment = Pos.CENTER
            HBox.setHgrow(quickInput, Priority.ALWAYS)
        }

        // --- جستجوی پیشرفته
        val lblSpace = Label("حداکثر فاصله بین کلمات").apply {
            style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"
            tooltip = javafx.scene.control.Tooltip("حداکثر تعداد کلمات مجاز بین کلمات جستجو (مخصوص همه کلمات)")
        }
        val spaceRow = HBox(8.0, lblSpace, spaceInput).apply { alignment = Pos.CENTER_RIGHT }

        val btnSearch = Button("جستجو کن").apply {
            style = Theme.primaryButton()
            prefHeight = Constants.OPTION_BUTTONS_HEIGHT
            maxWidth = Double.MAX_VALUE
            setOnMouseEntered { style = Theme.primaryButtonHover() }
            setOnMouseExited { style = Theme.primaryButton() }
            setOnAction { fireAdvanced() }
        }

        children.addAll(quickRow, javafx.scene.control.Separator(), allInput, anyInput, noneInput, spaceRow, btnSearch)
    }

    private fun fireQuick() { if (quickInput.text.isNotBlank()) onQuickSearch(quickInput.text.trim()) }

    private fun fireAdvanced() {
        onAdvancedSearch(
            AdvancedSearchParams(
                allInput.input.text.trim(), allInput.accuracy.isSelected,
                anyInput.input.text.trim(), anyInput.accuracy.isSelected,
                noneInput.input.text.trim(), noneInput.accuracy.isSelected,
                spaceInput.text.toIntOrNull() ?: 0,
            )
        )
    }

    /** معادل advanceInputWidget: فیلد متن با برچسب شناور و چک‌باکس «دقیق» (پیش‌فرض فعال) */
    class AdvanceInput(labelText: String) : VBox(2.0) {
        val input = TextField().apply { promptText = labelText; style = Theme.FONT }
        val accuracy = CheckBox("دقیق").apply {
            isSelected = true
            style = "${Theme.FONT} -fx-font-size: ${Constants.FONT_SIZE_SMALL}px;"
        }
        init {
            val row = HBox(8.0, input, accuracy).apply {
                alignment = Pos.CENTER_RIGHT
                HBox.setHgrow(input, Priority.ALWAYS)
            }
            children.add(row)
        }
    }
}
