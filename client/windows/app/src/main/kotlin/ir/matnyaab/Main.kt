package ir.matnyaab

import ir.matnyaab.core.*
import ir.matnyaab.ui.MainWindow
import ir.matnyaab.ui.SplashWindow
import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage

/**
 * نقطه ورود برنامه — معادل بخش __main__ در matnyaab.py
 */
class MatnyaabApp : Application() {
    override fun start(primaryStage: Stage) {
        val settings = AppSettingsManager()
        Constants.FONT_CHANGE_RATE = (settings.get("FONT_RATE").toIntOrNull() ?: 100) / 100.0 * 1.2

        val splash = SplashWindow()
        splash.show()

        Thread {
            val license = LicenseChecker(settings, init = true)
            val indexer = LuceneIndexer(settings)
            Platform.runLater {
                val main = MainWindow(primaryStage, settings, indexer, license)
                splash.close()
                main.show()
                StatisticsReporter.report("open_app", license.systemId, license.isSubscribed())
            }
        }.apply { isDaemon = true }.start()
    }

}

fun main(args: Array<String>) {
    Application.launch(MatnyaabApp::class.java, *args)
}
