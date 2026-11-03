package ir.matnyaab.ui

import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.StackPane

/** معادل WgWait — لایه نیمه‌شفاف «لطفا صبر کنید...» روی کل پنجره */
class WaitOverlay : StackPane() {
    init {
        style = "-fx-background-color: rgba(0,0,0,0.18);"
        val spinner = javafx.scene.control.ProgressIndicator().apply { prefWidth = 36.0; prefHeight = 36.0 }
        val lbl = Label("در حال جست‌وجو…").apply { style = "-fx-font-size: 15px; -fx-font-weight: bold;" }
        val card = javafx.scene.layout.VBox(10.0, spinner, lbl).apply {
            alignment = Pos.CENTER
            maxWidth = 220.0; maxHeight = 120.0
            styleClass.add("card")
            style = "-fx-padding: 18;"
        }
        children.add(card)
        alignment = Pos.CENTER
        isVisible = false
    }
    fun showOverlay() { isVisible = true; toFront() }
    fun hideOverlay() { isVisible = false }
}
