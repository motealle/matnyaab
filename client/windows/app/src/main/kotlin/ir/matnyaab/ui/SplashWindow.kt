package ir.matnyaab.ui

import ir.matnyaab.core.Constants
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.image.ImageView
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.stage.StageStyle

/** صفحهٔ آغاز: آیکون، نام و نوار بارگذاری — بدون وابستگی به فایل تصویری بیرونی */
class SplashWindow {
    private val stage = Stage(StageStyle.TRANSPARENT)

    init {
        val icon = Theme.iconImage(84.0)?.let { ImageView(it) }
        val title = Label("${Constants.APP_NAME_PERSIAN} ${Constants.APP_VERSION_PERSIAN}").apply {
            style = "-fx-font-size: 26px; -fx-font-weight: bold;"
        }
        val tagline = Label("جست‌وجو در متن اسناد شما").apply { styleClass.add("muted"); style = "-fx-font-size: 14px;" }
        val bar = ProgressBar(ProgressBar.INDETERMINATE_PROGRESS).apply { prefWidth = 220.0; prefHeight = 6.0 }
        val root = VBox(10.0).apply {
            listOfNotNull(icon, title, tagline, bar).forEach { children.add(it) }
            alignment = Pos.CENTER
            padding = Insets(34.0, 48.0, 30.0, 48.0)
            nodeOrientation = NodeOrientation.RIGHT_TO_LEFT
            style = "-fx-background-color: -mx-surface; -fx-background-radius: 18; -fx-border-color: -mx-border; -fx-border-radius: 18;"
        }
        stage.scene = Scene(root).apply { fill = Color.TRANSPARENT }
        Theme.install(stage.scene, stage)
        stage.centerOnScreen()
        stage.isAlwaysOnTop = true
    }

    fun show() = stage.show()
    fun close() = stage.close()
}
