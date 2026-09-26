package ir.matnyaab.ui

import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import javafx.stage.StageStyle

/** معادل WgSlpash — نمایش تصویر splash.jpg هنگام راه‌اندازی */
class SplashWindow {
    private val stage = Stage(StageStyle.UNDECORATED)

    init {
        val root = StackPane()
        try {
            val img = Image(javaClass.getResourceAsStream("/image/splash.jpg"))
            root.children.add(ImageView(img))
        } catch (e: Exception) { root.children.add(Label("MATNYAAB")) }
        stage.scene = Scene(root)
        stage.centerOnScreen()
        stage.isAlwaysOnTop = true
    }

    fun show() = stage.show()
    fun close() = stage.close()
}
