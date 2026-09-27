package ir.matnyaab.ui

import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.StackPane

/** معادل WgWait — لایه نیمه‌شفاف «لطفا صبر کنید...» روی کل پنجره */
class WaitOverlay : StackPane() {
    init {
        style = "-fx-background-color: rgba(243,250,240,0.9);"
        val lbl = Label("لطفا صبر کنید...").apply {
            style = "-fx-font-family: 'Tahoma','Arial'; -fx-font-size: 16px;"
        }
        children.add(lbl)
        alignment = Pos.CENTER
        isVisible = false
    }
    fun showOverlay() { isVisible = true; toFront() }
    fun hideOverlay() { isVisible = false }
}
